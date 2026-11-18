#!/usr/bin/env bash
# stdin'deki düz metni Rei botundan Yusuf'a yollar (rei-sabah, rei-gozcu, rei-dis-yoklama, rei-hata, rei-mac-izle). root çalıştırır; başarıda 0.
# Bot API'ye curl: getUpdates kullanılmaz, kanal oturumuyla çakışmaz (409 yok). Token ps'te görünmesin diye URL curl'e stdin'den verilir.
# Token ve allowlist rei'nin dosyaları: root o yolları kendi açmaz, rei kimliğiyle okunur (süre tavanlı, yol FIFO olsa da asılmaz).
# Kurulu yer: /usr/local/lib/rei/tg-gonder.sh (root 755).
set -euo pipefail
export LC_ALL=C.UTF-8  # kesim karakter sınırında olsun: yarım UTF-8 Telegram'dan 400 alır
ENVF=/home/rei/.claude/channels/telegram/.env
ACCESS=/home/rei/.claude/channels/telegram/access.json
oku() { timeout 10 runuser -u rei -- cat "$1"; }

token=$(oku "$ENVF" | sed -n 's/^TELEGRAM_BOT_TOKEN=//p' | tr -d '"\r' | head -1)
[ -n "$token" ] || { echo "tg-gonder: bot token yok" >&2; exit 1; }
chat=$(oku "$ACCESS" | python3 -I -c "import json,sys;print(json.load(sys.stdin)['allowFrom'][0])")
[[ "$chat" =~ ^[0-9]+$ ]] || { echo "tg-gonder: chat id yok" >&2; exit 1; }
mesaj=$(cat)
[ -n "$mesaj" ] || { echo "tg-gonder: boş mesaj" >&2; exit 1; }
mesaj=${mesaj:0:3800}
cevap=$(printf 'url = "https://api.telegram.org/bot%s/sendMessage"\n' "$token" | curl -s --max-time 20 --retry 3 --retry-delay 10 --retry-all-errors -K - -X POST \
  --data-urlencode "chat_id=${chat}" --data-urlencode "text=${mesaj}" --data-urlencode "disable_notification=false" \
  --data-urlencode 'link_preview_options={"is_disabled":true}' || true)
if echo "$cevap" | grep -q '"ok":true'; then exit 0; fi
echo "tg-gonder: gönderilemedi: ${cevap:0:200}" >&2
exit 1
