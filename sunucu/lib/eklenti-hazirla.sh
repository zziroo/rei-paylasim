#!/usr/bin/env bash
# Telegram eklentisinin root sahipli kopyasını hazırlar: dizin izinleri, node_modules, duman denemesi. Kurulu yer: /usr/local/lib/rei/eklenti-hazirla.sh (root 755).
# sunucu/kur.sh `sonra=eklenti` satırlarında (`server.ts`, `package.json`, `bun.lock`) daemon-reload'dan önce çağırır; hata dönerse kur.sh daemon-reload yapmaz. Elle de çalışır, tekrar çalıştırmak zararsız.
# `.bagimlilik-ozet` yalnız duman denemesi geçince yazılır: kur.sh geçişte bu dosyayı hazır olmanın kanıtı sayar. Yeni node_modules dumanı geçemezse eskisi geri konur.
# Açılışta `bun install` yok (dizine rei yazamaz), bağımlılık burada kurulur. Bun rei'nin yazabildiği yerde: root onu çalıştırmaz, kurulum rei kimliğiyle geçici dizinde, root yalnız tar akışını açar.
# Duman denemesi sahte token ve boş durum diziniyle açar; süreç zinciri `claude --channels` olmadığı için dinlemez, canlı oturuma (bot.pid, 409) dokunmaz.
set -euo pipefail
K=/opt/rei
E=$K/telegram-eklenti
BUN_ORTAM=(env -i HOME=/home/rei PATH=/home/rei/.bun/bin:/usr/bin:/bin)
G=""; T=""; S=""
temizle() {
  [ -z "$G" ] || rm -rf "$G"
  [ -z "$T" ] || runuser -u rei -- rm -rf "$T"
  [ -z "$S" ] || runuser -u rei -- rm -rf "$S"
}
trap temizle EXIT
[ "$(id -u)" = 0 ] || { echo "eklenti-hazirla: root gerekli" >&2; exit 2; }
[ -f "$E/package.json" ] && [ -f "$E/bun.lock" ] && [ -f "$E/server.ts" ] || { echo "eklenti-hazirla: $E eksik, önce sunucu/kur.sh" >&2; exit 2; }

ozet=$(cat "$E/package.json" "$E/bun.lock" | sha256sum | cut -d' ' -f1)
yeni=0
if [ ! -d "$E/node_modules" ] || [ "$(cat "$E/.bagimlilik-ozet" 2>/dev/null)" != "$ozet" ]; then
  G=$(mktemp -d "$K/.nm.XXXXXX")
  T=$(runuser -u rei -- mktemp -d)
  runuser -u rei -- "${BUN_ORTAM[@]}" timeout 300 sh -c '
    cd "$1" && cat "$2/package.json" >package.json && cat "$2/bun.lock" >bun.lock && cat "$2/.npmrc" >.npmrc &&
    bun install --frozen-lockfile --ignore-scripts --no-summary 1>&2 && tar cf - node_modules' sh "$T" "$E" |
    tar xf - --no-same-owner --no-same-permissions -C "$G"
  [ -f "$G/node_modules/grammy/package.json" ] || { echo "eklenti-hazirla: kurulum eksik" >&2; exit 1; }
  # Akış rei kimliğinden geldi: aygıt, FIFO, soket ve dizin dışına çıkan bağ kabul edilmez (root tar mknod yapar, sonraki chmod rei'ye ham disk okuturdu)
  yabanci=$(find "$G" ! -type f ! -type d ! -type l -print -quit)
  [ -z "$yabanci" ] || { echo "eklenti-hazirla: beklenmeyen dosya türü: $yabanci" >&2; exit 1; }
  while IFS= read -r -d '' b; do
    case "$(readlink -f "$b")" in "$G"/*) ;; *) echo "eklenti-hazirla: dışarı çıkan bağ: $b" >&2; exit 1 ;; esac
  done < <(find "$G" -type l -print0)
  chown -hR root:rei "$G"
  chmod -R u=rwX,g=rX,o= "$G"
  if [ -d "$E/node_modules" ]; then mv "$E/node_modules" "$G/eski"; fi
  mv "$G/node_modules" "$E/node_modules"
  yeni=1
  echo "eklenti-hazirla: node_modules kuruldu"
fi

chown root:rei "$K"
chmod 750 "$K"
chown -hR root:rei "$E"
chmod -R u=rwX,g=rX,o= "$E"

S=$(runuser -u rei -- mktemp -d)
ISTEK='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"hazirla","version":"0"}}}
{"jsonrpc":"2.0","method":"notifications/initialized"}
{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
cikti=$( { printf '%s\n' "$ISTEK"; sleep 3; } | runuser -u rei -- "${BUN_ORTAM[@]}" TELEGRAM_STATE_DIR="$S" TELEGRAM_BOT_TOKEN=123456:SAHTE TELEGRAM_ACCESS_MODE=static \
  timeout 6 bun run --cwd "$E" --shell=bun --silent start 2>/dev/null ) || true
case "$cikti" in
  *'"name":"reply"'*)
    if [ "$yeni" = 1 ]; then printf '%s\n' "$ozet" >"$E/.bagimlilik-ozet"; chown root:rei "$E/.bagimlilik-ozet"; chmod 640 "$E/.bagimlilik-ozet"; fi
    echo "eklenti-hazirla: duman denemesi geçti" ;;
  *)
    if [ "$yeni" = 1 ] && [ -d "$G/eski" ]; then
      mv "$E/node_modules" "$G/bozuk"; mv "$G/eski" "$E/node_modules"
      echo "eklenti-hazirla: eski node_modules geri kondu" >&2
    fi
    echo "eklenti-hazirla: duman denemesi KALDI, restart etme" >&2; exit 1 ;;
esac
