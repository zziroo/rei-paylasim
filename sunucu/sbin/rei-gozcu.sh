#!/usr/bin/env bash
# Rei sunucusunda root olarak her gece 00:20 (İstanbul): gece gözcüsünün Diskort/Tussle bölümleri. systemd rei-gozcu.timer çağırır.
# Tarama ve rapor rei kullanıcısında (/usr/local/lib/rei/rei-gozcu.py: kapı anahtarı ve claude oturumu onda); gönderim tg-gonder.sh ile.
# Betik ACİL satırı basarsa Telegram'a hemen gider; öbür her şey 08:30 sabah özetinde (rei-sabah.sh).
set -euo pipefail
export TZ=Europe/Istanbul

acil=$(timeout 840 sudo -n -H -u rei /usr/bin/python3 -I /usr/local/lib/rei/rei-gozcu.py)
[ -n "$acil" ] || exit 0
printf '🚨 Gözcü ACİL: %s\n' "$acil" | /usr/local/lib/rei/tg-gonder.sh
echo "rei-gozcu: acil gönderildi"
