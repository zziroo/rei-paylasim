#!/usr/bin/env bash
# Ana VPS'te root olarak bir kez: rei-kapi'ye ziroobank-api alt komutunu kurar.
# Dosyalar /tmp'ye scp ile gelmiş olmalı: rei-kapi, ziroobank-api.py, ziroobank-rei-hesap.mjs.
# Ziroobank'ta `rei` operatör hesabı açılır; şifre yalnız /etc/rei/ziroobank.env'e yazılır, ekrana düşmez.
set -euo pipefail
install -d -m 755 -o root -g root /etc/rei
if [ ! -s /etc/rei/ziroobank.env ]; then
  PW=$(docker exec -i ziroobank node --input-type=module - < /tmp/ziroobank-rei-hesap.mjs)
  [ -n "$PW" ] || { echo "şifre üretilemedi" >&2; exit 1; }
  ( umask 027; printf 'ZB_USER=rei\nZB_PASS=%s\n' "$PW" > /etc/rei/ziroobank.env )
  unset PW
  chown root:deploy /etc/rei/ziroobank.env; chmod 640 /etc/rei/ziroobank.env
  echo "env yazıldı"
else
  echo "env zaten var, hesap adımı atlandı"
fi
install -o root -g root -m 755 /tmp/rei-kapi /usr/local/sbin/rei-kapi
install -o root -g root -m 644 /tmp/ziroobank-api.py /usr/local/lib/rei/ziroobank-api.py
rm -f /tmp/rei-kapi /tmp/ziroobank-api.py /tmp/ziroobank-rei-hesap.mjs /tmp/kur-ziroobank-api.sh
ls -la /etc/rei/ziroobank.env /usr/local/sbin/rei-kapi /usr/local/lib/rei/
