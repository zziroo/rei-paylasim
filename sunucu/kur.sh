#!/usr/bin/env bash
# Kurulu dosyaları repoyla karşılaştırır ve yalnız güvenli durumda kurar. Tablo: sunucu/kurulum.tsv. Dersi: paralel oturum kurulu dosyayı ezer, fark görünmez.
#   sunucu/kur.sh fark [--ozet]     üç makinede sha256 karşılaştırır (salt okunur); fark varsa çıkış 1. --ozet tek satır, gözcü raporu için.
#   sunucu/kur.sh kur <repo-yolu>   tek dosya kurar
#   sunucu/kur.sh kur tumu          farklı olan bütün satırları tablo sırasıyla kurar
#   sunucu/kur.sh denetle           yazmadan `kur tumu` ön koşullarını sınar
# `kur` yalnız şu durumda yazar: HEAD = origin/main, sunucu/ temiz, kurulu özet dosyanın main geçmişinde var (ya da dosya hiç yok).
# Geçmişte olmayan özet başka bir oturumun henüz birleşmemiş kurulumudur: durur, üstüne yazmaz. rei.service'i hiçbir durumda yeniden başlatmaz, gerekiyorsa söyler.
# macOS bash 3.2 ile çalışır. Sır okumaz: kurulum.tsv'de sır dosyası yoktur.
set -euo pipefail
KOK=$(cd "$(dirname "$0")/.." && pwd)
cd "$KOK"
TSV=sunucu/kurulum.tsv
GECICI=$(mktemp -d "${TMPDIR:-/tmp}/rei-kur.XXXXXX")
reload=0; restart=0; eklenti=0; etkin=""; hata=0
trap 'c=$?; son_adim; rm -rf "$GECICI"; [ "$hata" = 0 ] || c=1; exit $c' EXIT
# /home/rei altı rei'nin yazabildiği yer: root orada dosya açmaz, özet de yazma da rei kimliğiyle ve süre tavanlıdır (FIFO asmaz, üst dizine konan bağ root'a yol açmaz).
OZETLE='while IFS= read -r y; do
  if [ ! -e "$y" ]; then printf "YOK\t%s\n" "$y"
  elif [ ! -r "$y" ]; then printf "OKUNAMADI\t%s\n" "$y"
  else case "$y" in
    /home/rei/*) s=$(timeout 10 runuser -u rei -- sha256sum "$y" | cut -d" " -f1) ;;
    *) s=$( (sha256sum "$y" 2>/dev/null || shasum -a 256 "$y") | cut -d" " -f1) ;;
  esac; printf "%s\t%s\n" "$s" "$y"; fi
done'

satirlar() { grep -v '^#' "$TSV" | grep . ; }
yerel_ozet() { shasum -a 256 "$1" | cut -d' ' -f1; }
ugra() {  # <makine>: stdin'deki yolların özetini basar
  case "$1" in
    vpn|vps) ssh -T -o BatchMode=yes -o ConnectTimeout=20 "$1" "$OZETLE" ;;
    *) sh -c "$OZETLE" ;;
  esac
}
topla() {  # $GECICI/<makine>: "<özet>\t<yol>"
  local m
  for m in vpn vps mac macz; do
    satirlar | awk -F'\t' -v m="$m" '$2 == m {print $3}' | ugra "$m" >"$GECICI/$m" || { echo "HATA: $m okunamadı" >&2; exit 2; }
  done
}
kurulu_ozet() { awk -F'\t' -v y="$2" '$2 == y {print $1; exit}' "$GECICI/$1"; }

fark() {
  topla
  local ayni=0 farkli=0 yok=0 okunamadi=0 y m k s _a _b o
  while IFS=$'\t' read -r y m k s _a _b; do
    o=$(kurulu_ozet "$m" "$k")
    if [ "$o" = "$(yerel_ozet "$y")" ]; then ayni=$((ayni + 1))
    elif [ "$o" = YOK ]; then yok=$((yok + 1)); [ "${1:-}" = --ozet ] || echo "YOK    $m $k"
    elif [ "$o" = OKUNAMADI ]; then okunamadi=$((okunamadi + 1)); [ "${1:-}" = --ozet ] || echo "OKUNMAZ $m $k"
    else farkli=$((farkli + 1)); [ "${1:-}" = --ozet ] || echo "FARK   $m $k ($y)"
    fi
  done < <(satirlar)
  echo "kurulum farkı: $ayni aynı, $farkli farklı, $yok kurulu değil, $okunamadi okunamadı ($(git rev-parse --short HEAD))"
  [ $((farkli + yok)) -eq 0 ]
}

gecmiste_var() {  # <repo-yolu> <özet>; repoya girmeden önceki kurulu sürümler kurulum-ilk.sha256'da
  local c
  grep -qxF -- "$2 $1" sunucu/kurulum-ilk.sha256 && return 0
  for c in $(git log --format=%H -n 300 origin/main -- "$1"); do
    [ "$(git show "$c:$1" | shasum -a 256 | cut -d' ' -f1)" = "$2" ] && return 0
  done
  return 1
}
on_kosul() {
  git fetch -q origin main
  [ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || { echo "DUR: HEAD origin/main değil; yalnız push'lu main kurulur" >&2; exit 1; }
  [ -z "$(git status --porcelain -- sunucu)" ] || { echo "DUR: sunucu/ altında commit'lenmemiş değişiklik var" >&2; exit 1; }
}
yaz() {  # <repo-yolu> <makine> <kurulu> <sahip> <mod>; içerik ssh stdin'inden gelir, geçici dosya rei'nin bilemeyeceği yerde açılır
  local y=$1 m=$2 k=$3 s=$4 d=$5
  case "$m:$s" in
    macz:*) install -m "$d" "$y" "$k" ;;
    vpn:rei:rei) ssh -T -o BatchMode=yes "$m" "runuser -u rei -- sh -c 't=\$(mktemp $k.XXXXXX) && cat >\"\$t\" && chmod $d \"\$t\" && mv -fT \"\$t\" $k'" <"$y" ;;
    *) ssh -T -o BatchMode=yes "$m" "t=\$(mktemp -d) && cat >\$t/f && { case $k in /etc/sudoers.d/*) visudo -cf \$t/f >/dev/null ;; esac; } && install -D -o ${s%:*} -g ${s#*:} -m $d \$t/f $k; r=\$?; rm -rf \$t; exit \$r" <"$y" ;;
  esac
}
son_adim() {  # hata ile çıkılsa da çalışır: yarım kurulumda daemon-reload ve restart uyarısı kaybolmasın
  # Eklenti hazır değilse birim yeni yeri görmesin: rei.service dosyası kurulmuş olsa da daemon-reload'a kadar servis eski yerden sürer (makine yeniden açılırsa diskteki birimi okur)
  if [ "$eklenti" = 1 ] && ! ssh -n -T -o BatchMode=yes vpn /usr/local/lib/rei/eklenti-hazirla.sh; then
    echo "UYARI: eklenti hazırlanamadı; daemon-reload yapılmadı, restart etme. Birim zaten /opt'taysa 05:00 restart'ı bu sürümü alır: düzelt ya da rei-restart.timer'ı durdur"
    hata=1; reload=0; restart=0; etkin=""
  elif [ "$reload" = 1 ] && ssh -n -T -o BatchMode=yes vpn 'grep -q /usr/local/lib/rei/rei-baslat.sh /etc/systemd/system/rei.service && [ ! -f /opt/rei/telegram-eklenti/.bagimlilik-ozet ]'; then
    echo "UYARI: rei.service yeni yerde ama eklenti hazırlanmamış; daemon-reload yapılmadı: sunucu/kur.sh kur tumu"
    hata=1; reload=0; restart=0; etkin=""
  fi
  if [ "$reload" = 1 ]; then ssh -n -T -o BatchMode=yes vpn systemctl daemon-reload && echo "daemon-reload yapıldı" || echo "UYARI: daemon-reload yapılamadı"; fi
  local b
  for b in $etkin; do ssh -n -T -o BatchMode=yes vpn "systemctl enable --now $b" && echo "etkin: $b" || echo "UYARI: $b açılamadı"; done
  if [ "$restart" = 1 ]; then echo "GEREKİYOR: systemctl restart rei (Yusuf'un kararı; 05:00 restart'ı da alır)"; fi
}
kur() {
  [ -n "${1:-}" ] || { echo "kullanım: sunucu/kur.sh kur <repo-yolu>|tumu" >&2; exit 2; }
  on_kosul
  topla
  local y m k s d son o bulundu=0 yeni asama asamalar="denetle yaz"
  [ "${2:-}" != yalniz-denetle ] || asamalar=denetle
  # Önce bütün satırlar denetlenir, sonra yazılır: DUR yarım kurulum bırakmasın
  for asama in $asamalar; do
    while IFS=$'\t' read -r y m k s d son; do
      [ "$1" = tumu ] || [ "$1" = "$y" ] || continue
      bulundu=1
      o=$(kurulu_ozet "$m" "$k"); yeni=$(yerel_ozet "$y")
      if [ "$o" = "$yeni" ]; then
        if [ "$asama" = yaz ]; then case "$son" in etkin:*) etkin="$etkin ${son#etkin:}"; reload=1 ;; eklenti) eklenti=1 ;; esac; fi
        continue
      fi
      if [ "$m" = mac ]; then [ "$asama" = denetle ] || echo "ATLA  $k: Mac kapısı, sudo bash sunucu/mac/kur-rei-guncelle.sh"; continue; fi
      if [ "$son" = elle ]; then [ "$asama" = denetle ] || echo "ATLA  $k: elle kurulur (kod haritası)"; continue; fi
      if [ "$asama" = denetle ]; then
        if [ "$o" = OKUNAMADI ] || [ -z "$o" ]; then echo "DUR: $k okunamadı" >&2; exit 1; fi
        if [ "$o" != YOK ] && ! gecmiste_var "$y" "$o"; then
          echo "DUR: $m $k kurulu sürüm main geçmişinde yok (başka oturum kurmuş olabilir, karşılaştır)" >&2; exit 1
        fi
        continue
      fi
      yaz "$y" "$m" "$k" "$s" "$d"
      echo "KURULDU $m $k"
      case "$son" in
        daemon-reload) reload=1 ;;
        etkin:*) reload=1; etkin="$etkin ${son#etkin:}" ;;
        restart) restart=1 ;;
        eklenti) eklenti=1; restart=1 ;;
      esac
    done < <(satirlar)
  done
  [ "$bulundu" = 1 ] || { echo "HATA: tabloda yok: $1" >&2; exit 2; }
}

case "${1:-}" in
  fark) fark "${2:-}" ;;
  kur)  kur "${2:-}" ;;
  denetle) kur tumu yalniz-denetle; echo "denetim geçti, yazılmadı" ;;
  *) echo "kullanım: sunucu/kur.sh fark [--ozet] | kur <repo-yolu>|tumu | denetle" >&2; exit 2 ;;
esac
