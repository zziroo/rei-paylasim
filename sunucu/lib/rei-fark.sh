#!/usr/bin/env bash
# Bu makinede kurulu dosyaları ~/ZZ/rei klonuyla karşılaştırır (klonu rei-klon.timer saatte bir ileri alır). Fark yoksa çıktı boş, varsa tek satır:
# `<adet> <ad> <ad> …` (en çok 5 ad). Sabah özeti bunu yazar; paralel oturumun ezdiği dosya biri rastgele diff alana kadar görünmez kalmasın.
# Yalnız `sunucu/kurulum.tsv`'deki vpn satırları; `sonra=elle` olanlar atlanır (Claude kendi anahtarını yazar). Mac ve ana VPS: sunucu/kur.sh fark.
# rei kullanıcısıyla çalışır. Sır okumaz: tablo sır dosyası içermez. Klon rei'nin yazabildiği yerdir: bu bir denetim değil, rapordur.
set -uo pipefail
cd /home/rei/ZZ/rei || exit 2
n=0; adlar=""
while IFS=$'\t' read -r y m k _s _d son; do
  case "$y" in \#*|"") continue ;; esac
  [ "$m" = vpn ] && [ "$son" != elle ] && [ -f "$y" ] || continue
  a=$(sha256sum "$y" | cut -d' ' -f1)
  b=$(sha256sum "$k" 2>/dev/null | cut -d' ' -f1)
  if [ "$a" != "$b" ]; then
    n=$((n + 1))
    if [ "$n" -le 5 ]; then adlar+=" ${k##*/}"; fi
  fi
done <sunucu/kurulum.tsv
if [ "$n" -gt 0 ]; then echo "$n$adlar"; fi
