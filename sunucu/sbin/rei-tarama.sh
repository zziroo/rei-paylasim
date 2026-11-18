#!/usr/bin/env bash
# Rei sunucusunda root olarak günde dört kez (09, 13, 18, 22 İstanbul): asistan taraması. systemd rei-tarama.timer çağırır.
# Tarama ve yorum rei kullanıcısında (/usr/local/lib/rei/rei-tarama.py: claude oturumu onda); gönderim tg-gonder.sh ile. Kanal oturumuna dokunmaz.
# Betik metin basarsa Telegram'a gider, sonra --onayla ile yeni durum geçerli olur; gönderim düşerse imleçler yerinde kalır. Boş çıktı sessiz turdur.
# --kuru: göndermez, durumu değiştirmez, ekrana basar. Başka seçenek reddedilir: yanlış yazılmış bir komut gerçek tur başlatmasın.
set -euo pipefail
export TZ=Europe/Istanbul
PY=/usr/local/lib/rei/rei-tarama.py
calistir() { sudo -n -H -u rei /usr/bin/python3 -I "$PY" "$@"; }

case "${1:-}" in
  ''|--kuru) ;;
  *) echo "rei-tarama: bilinmeyen seçenek: $1 (yalnız --kuru)" >&2; exit 2 ;;
esac

install -d -o rei -g rei -m 700 /var/lib/rei-tarama
if [ "${1:-}" = --kuru ]; then calistir --kuru; exit 0; fi
metin=$(timeout 420 sudo -n -H -u rei /usr/bin/python3 -I "$PY")  # düşerse set -e birimi düşürür, OnFailure haber verir
[ -n "$metin" ] || exit 0
printf '%s\n' "$metin" | /usr/local/lib/rei/tg-gonder.sh
calistir --onayla
echo "rei-tarama: gönderildi"
