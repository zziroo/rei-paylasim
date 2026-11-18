#!/usr/bin/env bash
# Rei sunucusunda root olarak her sabah 08:30 (İstanbul) tek Telegram mesajı: gece gözcüsünün özetleri + Rei'nin dünkü kullanımı.
# systemd rei-sabah.timer çağırır (kaynak: rei projesi sunucu/sbin ve sunucu/systemd). Rei'nin kanal oturumuna dokunmaz;
# gönderim tg-gonder.sh ile Bot API'ye gider (getUpdates kullanılmaz, 409 yok). --kuru: mesajı göndermez, ekrana basar, Kuma'ya push etmez.
# Rei kullanıcısı token dosyasını OS düzeyinde okuyabilir, engel yalnız managed Read deny; bu betik güven sınırı değil, yalnız raporlayıcı.
# Mac raporu sunucu/mac/gozcu-esitle.sh ile /home/rei/ZZ/gozcu'ya, sunucu raporu rei-gozcu.sh ile /home/rei/ZZ/gozcu-sunucu'ya gelir. Rapor yoksa bunu söyler.
set -euo pipefail
export TZ=Europe/Istanbul
kuru=0; [ "${1:-}" = --kuru ] && kuru=1
GOZCU=/home/rei/ZZ/gozcu
SUNUCU=/home/rei/ZZ/gozcu-sunucu
PROJE=/home/rei/.claude/projects

bugun=$(date +%F); dun=$(date -d yesterday +%F)
mesaj="☀️ Günaydın. $(date '+%d.%m.%Y %A')"$'\n'

# Gece gözcüsü iki parça: sunucu bölümleri (Diskort, Tussle) gece 00:20'de burada üretilir, Mac'e bağlı değil; Mac bölümleri (kripto-bot, yedekler, kod incelemesi)
# 00:10'da Mac'te yazılır, 00:45/08:15'te gelir. Her iki parça için de bir satır yazılır: yokluğu "sorun yok" sanılmasın.
son_rapor() { ls "$1"/"$bugun"-*-"$2".md 2>/dev/null | sort | tail -1 || true; }
# Düz metin gider (parse_mode yok): Markdown işaretleri (**, `) ayıklanır
# Rapor rei'nin yazabildiği dizinde: root açmaz, rei kimliğiyle okunur (süre tavanlı, FIFO asmaz)
ozet() { timeout 10 runuser -u rei -- cat "$1" | awk '/^## Özet/{f=1;next} /^## /{if(f)exit} f' | sed -e '/^[[:space:]]*$/d' -e 's/\*\*//g' -e 's/`//g' | head -12 || true; }
saat() { basename "$1" | cut -c12-15 | sed 's/\(..\)\(..\)/\1:\2/'; }

rapor=$(son_rapor "$SUNUCU" sunucu)
if [ -n "$rapor" ]; then
  o=$(ozet "$rapor")
  mesaj+=$'\n'"🔎 Diskort ve Tussle ($(saat "$rapor")):"$'\n'"${o:-(özet boş)}"$'\n'
else
  mesaj+=$'\n'"⚠️ Diskort ve Tussle: bu geceki tarama çalışmadı."$'\n'
fi

rapor=$(son_rapor "$GOZCU" gozcu)
if [ -n "$rapor" ]; then
  o=$(ozet "$rapor")
  mesaj+=$'\n'"🌙 Mac tarafı ($(saat "$rapor")):"$'\n'"${o:-(özet boş)}"$'\n'
else
  mesaj+=$'\n'"⚠️ Mac bölümleri eksik (kripto-bot, Mac yedekleri, kod incelemesi): bugünün raporu gelmedi, Mac kapalı olabilir."$'\n'
fi

# Rei'nin dünkü kullanımı: transkriptlerden, rei kullanıcısı olarak (/usr/local/lib/rei/rei-kullanim.py)
kullanim=$(timeout 30 sudo -n -u rei /usr/bin/python3 -I /usr/local/lib/rei/rei-kullanim.py "$PROJE" "$dun" 2>/dev/null || echo "ölçülemedi")
mesaj+=$'\n'"📊 Dün ($dun): $kullanim"$'\n'

bos=$(df -h / | awk 'NR==2{print $4}' || true); ram=$(free -m | awk '/^Mem:/{print $7}' || true)
servis=$(systemctl is-active rei || true)
surum=$(timeout 10 runuser -u rei -- /home/rei/.local/bin/claude --version 2>/dev/null | cut -d' ' -f1 || true)
mesaj+=$'\n'"🖥 Sunucu: rei ${servis:-?}, claude ${surum:-?}, disk boş ${bos:-?}, RAM uygun ${ram:-?} MB"
dolu=$(df --output=pcent / | tail -1 | tr -dc 0-9 || true)
if [ "${dolu:-0}" -ge 85 ]; then mesaj+=$'\n'"⚠️ Sunucu diski %$dolu dolu"; fi

# Bitiş tarihleri: Claude girişi credentials dosyasından (rei-giris.py yalnız tarih verir), gerisi /etc/rei/sureler. Her gün en yakını yazılır;
# satırın yokluğu "sorun yok" sanılmasın. UYARI_GUN içindekiler ayrı ayrı uyarılır. Refresh tarihi yenilemede kaymaz (2026-11-01 ölçümü).
UYARI_GUN=14
simdi_sn=$(date +%s); en_az=""; en_yakin=""; sure_uyari=""
sure_ekle() {  # <ad> <bitiş epoch>
  local kalan=$(( ($2 - simdi_sn) / 86400 )) tarih
  tarih=$(date -d "@$2" +%d.%m.%Y)
  if [ "$kalan" -le "$UYARI_GUN" ]; then sure_uyari+=$'\n'"⚠️ $1 $tarih bitiyor ($kalan gün)"; fi
  if [ -z "$en_az" ] || [ "$2" -lt "$en_az" ]; then en_az=$2; en_yakin="$1 $tarih ($kalan gün)"; fi
  return 0
}
giris=$(timeout 10 runuser -u rei -- /usr/bin/python3 -I /usr/local/lib/rei/rei-giris.py 2>/dev/null || true)
refresh=${giris%% *}
if [[ "$refresh" =~ ^[0-9]+$ ]]; then sure_ekle "Claude girişi" "$refresh"; else mesaj+=$'\n'"⚠️ Claude girişi: bitiş tarihi okunamadı"; fi
if [ -r /etc/rei/sureler ]; then
  while read -r tarih ad; do
    [[ "$tarih" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || continue
    bitis=$(date -d "$tarih" +%s 2>/dev/null) || { mesaj+=$'\n'"⚠️ Bitiş listesinde geçersiz tarih: $tarih"; continue; }
    sure_ekle "$ad" "$bitis"
  done </etc/rei/sureler
fi
if [ -n "$sure_uyari" ]; then mesaj+=$sure_uyari; elif [ -n "$en_yakin" ]; then mesaj+=$'\n'"🔑 En yakın bitiş: $en_yakin"; fi

# Klonlar saatte bir ileri alınır (rei-klon.timer); dış yoklama alarm hattıdır: ikisi de sessiz düşebilir, burada görünsün.
if [ "$(systemctl show -p Result --value rei-klon.service 2>/dev/null || true)" != success ] || ! systemctl is-active --quiet rei-klon.timer; then mesaj+=$'\n'"⚠️ Sunucu klonları güncellenemedi (brain, rei ya da claude-kurulum)"; fi
fark=$(timeout 30 runuser -u rei -- /usr/local/lib/rei/rei-fark.sh 2>/dev/null || true)
if [ -n "$fark" ]; then mesaj+=$'\n'"⚠️ Kurulu dosya repodan farklı: $fark"; fi
if ! systemctl is-active --quiet rei-dis-yoklama.timer; then mesaj+=$'\n'"⚠️ Dış yoklama çalışmıyor: ist-1 düşerse alarm gelmez"; fi

# Ana VPS güncelliği: root'un ana VPS'e anahtarı yok, kapıya rei kullanıcısına inerek gidilir (kullanım satırıyla aynı yol).
# Yalnız `ÖZET:` satırı alınır. Her durumda bir satır yazılır: satırın yokluğu "güncel" sanılmasın.
vps=$(timeout 90 sudo -n -u rei /home/rei/bin/rei-vps sunucu-durum 2>/dev/null | sed -n '/^ÖZET: /{s///p;q}' || true)
case "$vps" in
  "sorun yok") mesaj+=$'\n'"🛡 ist-1 güncel" ;;
  "")          mesaj+=$'\n'"⚠️ ist-1: durum alınamadı" ;;
  *)           mesaj+=$'\n'"⚠️ ist-1: $vps" ;;
esac

# Bekleyen Mac işi: root'un Mac'e anahtarı yok, kapıya rei kullanıcısına inerek gidilir (üstteki satırla aynı yol). Yalnız tam desene uyan
# `BEKLEYEN: N` satırı sayılır. Satır yalnız N>0 iken yazılır; Mac erişilemezse N bilinmez, satır yazılmaz (gözcü satırı zaten Mac'in kapalı olabileceğini söyler).
bekleyen=$(timeout 30 sudo -n -u rei /home/rei/bin/rei-mac durum 2>/dev/null | sed -nE '/^BEKLEYEN: [0-9]+$/{s/^BEKLEYEN: //p;q;}' || true)
if [ -n "$bekleyen" ] && [ "$bekleyen" -gt 0 ] 2>/dev/null; then mesaj+=$'\n'"⏳ Bekleyen Mac işi: $bekleyen"; fi

# Ziroobank mailleri: yalnız sorun ya da okuma hatası varsa satır yazılır. Kişi ve durum gider, tutar ve adres gitmez.
# Gönderim ayın 1'i 10:00; her istek denetim günlüğüne giriş yazdığı için yalnız ayın 2-4'ünde sorulur (geç bounce 72 saat izlenir).
zbmail=""
gun=$(date +%-d)
if [ "$gun" -ge 2 ] && [ "$gun" -le 4 ]; then
zbmail=$(timeout 60 sudo -n -u rei /home/rei/bin/rei-vps ziroobank-api GET /mail/status 2>/dev/null | sed 1d | python3 -c '
import json, sys
d = json.load(sys.stdin)
sorun = [r["person"] for r in d["log"] if r["kind"] == "reminder" and (r["status"] != "sent" or r.get("delivery") == "failed")]
if d["problems"]: print(d["problems"], "mail sorunlu (" + ", ".join(sorun) + ")")
' 2>/dev/null) || zbmail="durum alınamadı"
fi
if [ -n "$zbmail" ]; then mesaj+=$'\n'"⚠️ Ziroobank mail: $zbmail"; fi

# Telefondaki Rei SMS 6 saatte bir yoklar; HyperOS dondurursa mesajlar sessizce kesilir. Yalnız `SESSIZ:` satırı alınır, mesaj içeriği özete girmez.
# Satır yalnız sorun varken yazılır. Okunamazsa da yazılır: yokluğu "telefon çalışıyor" sanılmasın.
sms=$(timeout 15 runuser -u rei -- /home/rei/bin/rei-sms durum 2>/dev/null | sed -n 's/^SESSIZ: //p' || true)
case "$sms" in
  hayır) ;;
  evet)  mesaj+=$'\n'"⚠️ Telefon sessiz: Rei SMS 24 saattir ses vermiyor, mesajlar gelmiyor olabilir" ;;
  *)     mesaj+=$'\n'"⚠️ Telefon mesajları: durum alınamadı" ;;
esac

# Asistan taraması sessizken de çalışır: yokluğu "önemli bir şey yok" sanılmasın. Her gün tek satır, yalnız sayı; durum dosyası rei'nin, rei kimliğiyle okunur.
tarama=$(timeout 15 runuser -u rei -- /usr/bin/python3 -I /usr/local/lib/rei/rei-tarama.py --durum 2>/dev/null || true)
tsatir=$(printf '%s\n' "$tarama" | sed -n 's/^TARAMA: //p')
if ! systemctl is-enabled --quiet rei-tarama.timer 2>/dev/null; then
  :  # zamanlayıcı açılmadan önce satır yok
elif [ -z "$tsatir" ]; then mesaj+=$'\n'"⚠️ Asistan taraması: durum alınamadı"
elif printf '%s\n' "$tarama" | grep -q '^ESKI: evet'; then mesaj+=$'\n'"⚠️ Asistan taraması tıkanmış: $tsatir"
else mesaj+=$'\n'"👀 Tarama: $tsatir"; fi

if [ "$kuru" = 1 ]; then printf '%s\n' "$mesaj"; exit 0; fi
printf '%s\n' "$mesaj" | /usr/local/lib/rei/tg-gonder.sh
echo "rei-sabah: gönderildi"
# Rei botu bozulursa bu sinyal kesilir, haberi ops botundan gelir (Kuma push monitörü "sabah özeti", 26 saat). Jeton yoksa atlanır.
if [ -r /etc/rei/kuma.env ]; then
  . /etc/rei/kuma.env
  if [ -n "${PUSH_SABAH:-}" ]; then
    curl -s -m 10 -o /dev/null -G "${KUMA_URL:-https://ops.example.com}/api/push/$PUSH_SABAH" \
      --data-urlencode "status=up" --data-urlencode "msg=gönderildi" --data-urlencode "ping=" || true
  fi
fi
