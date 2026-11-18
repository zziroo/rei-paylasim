#!/usr/bin/env bash
# Zamanlı iş düşünce çalışır (OnFailure=rei-hata@<birim>.service): Rei botundan tek mesaj; sabah özeti için Kuma'ya da down yollar,
# çünkü bot bozulduysa bu mesaj da gitmez ve haber ops botundan gelmelidir. Jeton /etc/rei/kuma.env PUSH_SABAH (yoksa atlanır).
set -uo pipefail
birim=${1:-}
[[ "$birim" =~ ^rei-(sabah|gozcu|tarama)$ ]] || { echo "rei-hata: birim reddedildi: $birim" >&2; exit 1; }
if [ "$birim" = rei-sabah ] && [ -r /etc/rei/kuma.env ]; then
  . /etc/rei/kuma.env
  if [ -n "${PUSH_SABAH:-}" ]; then
    curl -s -m 10 -o /dev/null -G "${KUMA_URL:-https://ops.example.com}/api/push/$PUSH_SABAH" \
      --data-urlencode "status=down" --data-urlencode "msg=sabah özeti gönderilemedi" --data-urlencode "ping=" || true
  fi
fi
case "$birim" in
  rei-sabah) ad="sabah özeti" ;;
  rei-gozcu) ad="gece gözcüsü" ;;
  rei-tarama) ad="asistan taraması" ;;
esac
printf '⚠️ Zamanlı iş başarısız: %s (ayrıntı: journalctl -u %s)\n' "$ad" "$birim" | /usr/local/lib/rei/tg-gonder.sh
