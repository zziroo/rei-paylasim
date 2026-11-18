#!/usr/bin/env bash
# Frankfurt'tan ana VPS ve Kuma için dış yoklama (systemd rei-dis-yoklama.timer, 5 dakikada bir). Kuma, ops botu ve izlenen servisler aynı makinede:
# makine ya da Kuma düşerse başka alarm çıkmaz, ilk işaret sabah 08:30 olurdu. LLM yok, Rei oturumuna dokunmaz; mesaj Rei botundan Bot API ile gider.
# Ardışık $ESIK yoklamada hata → tek mesaj; hata listesi değişirse yeni mesaj; düzelince tek mesaj. Gönderilemeyen mesaj sonraki turda yeniden denenir.
# Durum: /var/lib/rei-dis-yoklama/durum (systemd StateDirectory, root 700). --kuru [dizin]: mesaj yerine ekrana basar, durum dosyası verilen dizinde ya da yeni geçici dizinde.
set -uo pipefail
ESIK=2
GONDER=/usr/local/lib/rei/tg-gonder.sh
DURUM=/var/lib/rei-dis-yoklama/durum
HEDEFLER=(
  "Kuma https://ops.example.com/api/entry-page"
  "tools https://tools.example.com/"
  "Diskort https://uygulama.example.com/healthz"
)
kuru=0
if [ "${1:-}" = --kuru ]; then kuru=1; DURUM=${2:-$(mktemp -d)}/durum; fi
gonder() { if [ "$kuru" = 1 ]; then echo "MESAJ: $1"; else printf '%s\n' "$1" | "$GONDER"; fi; }

sayac=0; baslangic=0; bildirilen=""
if [ -r "$DURUM" ]; then { read -r sayac; read -r baslangic; IFS= read -r bildirilen; } <"$DURUM"; fi
[[ "$sayac" =~ ^[0-9]+$ ]] || sayac=0
[[ "$baslangic" =~ ^[0-9]+$ ]] || baslangic=0

simdi=$(date +%s)
hata=""
for h in "${HEDEFLER[@]}"; do
  ad=${h%% *}; url=${h#* }
  kod=$(curl -s -o /dev/null -m 15 -w '%{http_code}' "$url" || true)
  case "$kod" in 2*) ;; *) hata+="${hata:+, }$ad ($kod)" ;; esac
done
# Kod değişse de liste aynı sayılsın: mesajı yalnız hatalı hedeflerin adı tetikler
adlar=$(printf '%s' "$hata" | sed -E 's/ \([0-9]*\)//g')

if [ -n "$hata" ]; then
  [ "$sayac" -gt 0 ] || baslangic=$simdi
  sayac=$((sayac + 1))
  if [ "$sayac" -ge "$ESIK" ] && [ "$adlar" != "$bildirilen" ]; then
    case "$adlar" in
      *Kuma*) ek=" Kuma alarm üretemez; ist-1'e bak." ;;
      *)      ek="" ;;
    esac
    if gonder "🚨 fra-1'den erişilemiyor: ${hata}.${ek}"; then bildirilen=$adlar; fi
  fi
else
  if [ -n "$bildirilen" ]; then
    if gonder "✅ Düzeldi: ${bildirilen} yeniden erişilebilir ($(( (simdi - baslangic) / 60 )) dk sürdü)."; then bildirilen=""; fi
  fi
  sayac=0
fi

printf '%s\n%s\n%s\n' "$sayac" "$baslangic" "$bildirilen" >"$DURUM.tmp" && mv "$DURUM.tmp" "$DURUM"
