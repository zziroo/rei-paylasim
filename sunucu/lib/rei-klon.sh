#!/usr/bin/env bash
# Rei sunucusundaki okuma klonlarını saatte bir ileri alır (rei-klon.timer, kullanıcı rei; salt okunur ve brain PAT'ı ~/.git-credentials).
# Oturum açılış kancası bu işi yapmıyor: Rei'nin çalışma dizini ~/ZZ, oturum-baslangici.mjs ZZ kökünde vaultCek()'e varmadan döner.
# Yalnız fast-forward: yerelde Rei'nin push'lanmamış commit'i çatallanmışsa dokunmaz, çıkış 1 olur, sabah özeti satır yazar.
# claude-kurulum Rei'nin kanca ve global kural kaynağıdır (~/.claude/* bağları): yeni hali sonraki oturumda devreye girer.
set -uo pipefail
hata=0
for d in brain rei claude-kurulum; do
  timeout 60 git -C "/home/rei/ZZ/$d" pull --ff-only -q || { echo "rei-klon: $d güncellenemedi" >&2; hata=1; }
done
exit "$hata"
