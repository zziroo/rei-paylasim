#!/usr/bin/env bash
# rei.service, claude süreci ve Telegram dinleyicisi sağlamsa Kuma push monitörüne "up" gönderir; değilse "down".
# Kuma 10 dk sinyal almazsa <bildirim_botu> Telegram'a yazar. Jeton: /etc/rei/kuma.env (PUSH_REI, KUMA_URL).
# Dinleyici: eklenti (sunucu/telegram-eklenti/server.ts) başarılı her getUpdates'te $DINLEME dosyasını tazeler (uzun yoklama
# en çok 30 sn). Süreç deseni yetmez: 2026-11-01 kesintisinde dinleyen yoktu ve Kuma yeşil kaldı; `bun server.ts` deseni de
# dinlemeyen kopyaları sayar ve 409 alan dinleyici süreç olarak ayakta ama bilgisizdir.
# Yeniden başlatmada eski sinyal taze kalır; sıfırdan açılışta (reboot) $ACILIS_PAYI içinde sinyal aranmaz.
# Kurulum sırası: önce eklenti + restart, sonra bu betik (dosya yoksa sürekli down). Resmi eklenti bu sinyali yazmaz.
# Giriş: süreç ve dinleyici ayakta kalsa da Claude girişi bitmiş olabilir. rei-giris.py yalnız tarih alanlarını verir (API çağrısı yok).
# Refresh tarihi geçtiyse ya da dosya yoksa down. Access token kullanılmadıkça yenilenmez (sessiz gecede gözcü de LLM çağırmaz), bu yüzden
# yalnız yaşı down yapmaz: ERISIM_PAYI aşıldı VE tmux ekranında giriş hatası var ise down; biri tek başına yalnız msg'ye yazılır (gözlem).
set -uo pipefail
ENV=/etc/rei/kuma.env
DINLEME=/home/rei/.claude/channels/telegram/dinleme
SINYAL_TAVANI=180 # sn; üç uzun yoklama turu
ACILIS_PAYI=120   # sn; servis yeni kalktıysa
ERISIM_PAYI=86400 # sn; access token bu kadardır süresi dolu duruyorsa
GIRIS_DESENI='Please run /login|Invalid API key|OAuth token has expired|authentication_error'
[ -r "$ENV" ] || exit 0
. "$ENV"
[ -n "${PUSH_REI:-}" ] || exit 0
simdi=$(date +%s)
sinyal=$(stat -c %Y "$DINLEME" 2>/dev/null || echo 0)
acilis=$(date -d "$(systemctl show rei -p ActiveEnterTimestamp --value)" +%s 2>/dev/null || echo 0)
giris=$(timeout 10 runuser -u rei -- /usr/bin/python3 -I /usr/local/lib/rei/rei-giris.py 2>/dev/null || true)
refresh=${giris%% *}; erisim=${giris##* }
ekran=0; metin=$(timeout 10 runuser -u rei -- tmux -L rei capture-pane -p -t rei 2>/dev/null || true)
if grep -qE "$GIRIS_DESENI" <<<"$metin"; then ekran=1; fi
yas=0; [[ "$erisim" =~ ^[0-9]+$ ]] && yas=$((simdi - erisim))
if ! systemctl is-active --quiet rei || ! pgrep -u rei -f 'claude .*--channels' >/dev/null; then
  st=down; msg="Rei servisi ya da claude süreci yok"
elif [ $((simdi - sinyal)) -gt "$SINYAL_TAVANI" ] && [ $((simdi - acilis)) -gt "$ACILIS_PAYI" ]; then
  st=down; msg="Telegram dinleyicisi sessiz (son sinyal $((simdi - sinyal)) sn önce)"
elif [ "$giris" = yok ]; then
  st=down; msg="Rei girişi yok (credentials dosyası yok)"
elif [[ "$refresh" =~ ^[0-9]+$ ]] && [ "$refresh" -le "$simdi" ]; then
  st=down; msg="Rei girişi bitti (refresh token süresi doldu)"
elif [ "$ekran" = 1 ] && [ "$yas" -gt "$ERISIM_PAYI" ]; then
  st=down; msg="Rei girişi bozuk (ekranda giriş hatası, access token $((yas / 3600)) saattir yenilenmemiş)"
else
  st=up; msg="Rei ayakta ($(free -m | awk '/Mem:/{print $3}') MB RAM)"
  if [ -z "$giris" ] || [ "$giris" = bilinmiyor ]; then msg+="; giriş tarihi okunamadı"; fi
  if [ "$ekran" = 1 ]; then msg+="; ekranda giriş hatası deseni"; fi
  if [ "$yas" -gt "$ERISIM_PAYI" ]; then msg+="; access token $((yas / 3600)) saattir yenilenmemiş"; fi
fi
curl -s -m 10 -o /dev/null -G "${KUMA_URL:-https://ops.example.com}/api/push/$PUSH_REI" \
  --data-urlencode "status=$st" --data-urlencode "msg=$msg" --data-urlencode "ping=" || true
