#!/usr/bin/env bash
# Gece gözcüsü raporlarını (~/ZZ/gozcu) Rei sunucusuna kopyalar: vpn:/home/rei/ZZ/gozcu (sahibi rei, 750).
# Karşı yönde sunucu raporlarını (Diskort, Tussle; vpn:/home/rei/ZZ/gozcu-sunucu) ~/ZZ/gozcu/sunucu/ altına alır: Mac oturumları da okusun. Yalnız düzenli
# `[0-9]*-sunucu.md` dosyaları gelir: rei'nin yazabildiği dizinden CLAUDE.md ya da sembolik bağ Mac'e inmesin.
# launchd net.ziroo.gozcu-esitle: her gün 00:45 ve 08:15 (gözcü 00:10'da başlar; Mac uyuduysa ikinci saat yakalar).
# Giden: *.md raporlar, BENIOKU.md, durum.json, inceleme/; gitmeyen: .git, okundu/, araclar/, sunucu/, .DS_Store.
# Sunucuda rsync yok; tar akışıyla gider. Uzak tarafta açma işini root değil `rei` yapar (runuser): rei'nin kontrolündeki
# bir yolda root'un symlink izlemesi olmaz. Günlük: ~/Yedekler/Gozcu/esitle.log (TAMAM/HATA). Alias `vpn` (~/.ssh/config).
set -Eeuo pipefail
KAYNAK="$HOME/ZZ/gozcu"
HEDEF=/home/rei/ZZ/gozcu
LOG="$HOME/Yedekler/Gozcu/esitle.log"
now() { date '+%Y-%m-%d %H:%M'; }
fail() { echo "$(now) HATA   $*" >>"$LOG"; echo "HATA: $*" >&2; exit 1; }
trap 'fail "satır $LINENO: $BASH_COMMAND"' ERR
mkdir -p "$(dirname "$LOG")"
cd "$KAYNAK"
adet=$(find . -maxdepth 1 -name '*-gozcu.md' | wc -l | tr -d ' ')
son=$( (ls -t ./*-gozcu.md 2>/dev/null || true) | head -1 | xargs -n1 basename 2>/dev/null || true)
COPYFILE_DISABLE=1 tar --no-xattrs --no-mac-metadata -czf - --exclude .git --exclude .DS_Store --exclude okundu --exclude araclar --exclude ./sunucu . \
  | ssh -T -o BatchMode=yes -o ConnectTimeout=20 vpn "runuser -u rei -- sh -c 'mkdir -p $HEDEF && chmod 750 $HEDEF && tar -xzf - -C $HEDEF --no-same-owner --no-same-permissions'"
mkdir -p "$KAYNAK/sunucu"
ssh -T -o BatchMode=yes -o ConnectTimeout=20 vpn "runuser -u rei -- sh -c 'cd ${HEDEF}-sunucu && find . -maxdepth 1 -type f -name \"[0-9]*-sunucu.md\" -print0 | tar -czf - --null -T -'" | tar -xzf - -C "$KAYNAK/sunucu"
echo "$(now) TAMAM  $adet rapor, son: ${son:-yok}" >>"$LOG"
