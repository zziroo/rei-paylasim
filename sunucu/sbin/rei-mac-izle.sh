#!/usr/bin/env bash
# rei.service her başladığında bir kez çalışır (rei-mac-izle.service, WantedBy=rei.service). Rei'nin bekleme döngüsü kendi oturumunda koşar, yeniden
# başlatmada ölür; o anda Mac'te tur sürüyorsa bitişini kimse haber vermez (Kabul 3, 2026-11-02). Bu betik o turu izler, tur bitince Telegram'a sabit bir
# satır yazar. Süren tur yoksa hemen çıkar: normal akışta Mac'e tek `durum` sorusu gider. Rei kendi döngüsünü başlatırsa haber ona düşer, betik sessizce çıkar.
# İkinci yeniden başlatma birimi yeniden çalıştırmaz, süren izleme devam eder. Rei'nin kanal oturumuna dokunmaz.
# Telegram'a Mac'ten serbest metin gitmez: yalnız iş-id deseni ve aşağıdaki sabit durum adları. Root'un Mac'e anahtarı yok, kapıya rei kullanıcısına inerek gidilir.
# `pgrep` deseni sunucu/rules/sunucu.md'deki bekleme döngüsü komutuna bağlıdır. --kuru: mesajı göndermez, ekrana basar.
set -euo pipefail
kuru=0; [ "${1:-}" = --kuru ] && kuru=1
ARALIK=30    # sn, durum sorma aralığı
TAVAN=4500   # sn; Mac'teki tur tavanı 3600, birimdeki RuntimeMaxSec bunun üstünde

mac() { timeout 30 sudo -n -u rei /home/rei/bin/rei-mac "$@" </dev/null 2>/dev/null | head -c 16384; }

liste=""
for _ in 1 2 3 4 5; do   # makine açılışında Tailscale geç gelebilir
  if liste=$(mac durum); then break; fi
  liste=""
  sleep 20
done
[ -n "$liste" ] || { echo "rei-mac-izle: Mac'e ulaşılamadı, izlenmiyor"; exit 0; }
id=$(printf '%s\n' "$liste" | sed -nE '/^[0-9]{8}-[0-9]{6}-[a-z0-9-]{1,20}  çalışıyor /{s/ .*//p;q;}' || true)
[ -n "$id" ] || exit 0
echo "rei-mac-izle: $id izleniyor"

bas=$(date +%s)
while [ $(($(date +%s) - bas)) -lt "$TAVAN" ]; do
  sleep "$ARALIK"
  if pgrep -u rei -f 'while .*rei-mac durum' > /dev/null; then
    echo "rei-mac-izle: Rei kendi döngüsünü başlattı, haber ona düşer"
    exit 0
  fi
  s=$(mac durum "$id" | sed -n '1s/^DURUM: //p' || true)
  case "$s" in
    "soru bekliyor"*)  ne="soru bekliyor" ;;
    "cevap bekliyor"*) ne="cevabını bekliyor" ;;
    "bitti"*"(iptal)"*) echo "rei-mac-izle: $id iptal edildi"; exit 0 ;;   # iptali Rei çalıştırır, haber gerekmez
    "bitti"*)          ne="bitti" ;;
    "belirsiz"*)       ne="durumu belirsiz" ;;
    *) continue ;;
  esac
  mesaj="⏳ Mac işi $id: $ne. Rei tur sürerken yeniden başladığı için haber gelmedi. Rei'ye \"Mac işi ne durumda\" yaz."
  if [ "$kuru" = 1 ]; then printf '%s\n' "$mesaj"; exit 0; fi
  if printf '%s\n' "$mesaj" | /usr/local/lib/rei/tg-gonder.sh; then
    echo "rei-mac-izle: $id için gönderildi"
    exit 0
  fi
  echo "rei-mac-izle: gönderilemedi, yeniden denenecek"   # gönderim hatası haberi kaybettirmesin
done
echo "rei-mac-izle: $id $TAVAN sn içinde bitmedi ya da Mac erişilemedi, çıkılıyor"
