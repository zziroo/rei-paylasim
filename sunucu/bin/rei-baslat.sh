#!/usr/bin/env bash
# tmux içinde Claude Code'u Telegram kanalıyla başlatır. systemd rei.service bunu çağırır. Kurulu yer: /usr/local/lib/rei/rei-baslat.sh (root 755; Rei kendi başlatma betiğini değiştiremesin).
# Model ve efor açıkça verilir: yalnız --effort verilince varsayılan model Fable'a kayıyordu (2026-11-01); Rei Opus 5.5 medium ile çalışır.
# Telegram eklentisi Rei forkudur (repo sunucu/telegram-eklenti → /opt/rei/telegram-eklenti, root:rei 750: izin kartı köprüsü rei'nin yazabildiği yerde durmasın; node_modules eklenti-hazirla.sh ile). Yükleme yolu: kurulu eklenti kaydı
# (~/.claude/plugins/installed_plugins.json, telegram@claude-plugins-official) fork dizinine yönlendirilir; --plugin-dir ile yüklenen
# kopya MCP sunucusunu başlatıyor ama kanal bildirimleri kaydolmuyordu (mesajlar kayboldu, 2026-11-01). Kayıt her açılışta burada
# onarılır (eklenti güncellemesi kaydı yeni sürüme çevirirse fork yine baskın kalır). Fork dizini yoksa kayıt dokunulmadan kalır (resmi eklenti).
# Eklenti rei'nin kullanıcı ayarında kapalıdır (sunucu/etc/rei-claude-settings.json), yalnız burada --settings ile açılır: düz `claude` oturumu
# başlatmasın. Kayıt resmi önbelleğe dönerse (2026-11-01 15:28) resmi server.ts'in dinleme koruması yok, gerçek dinleyiciyi bot.pid üzerinden devralırdı.
# rei-ping.sh'nin pgrep deseni "claude .*--channels" ile uyumlu kalmalı.
export PATH="$HOME/.local/bin:$HOME/.bun/bin:$PATH"
cd "$HOME/ZZ"
EKLENTI=/opt/rei/telegram-eklenti
KAYIT="$HOME/.claude/plugins/installed_plugins.json"
if [ -f "$EKLENTI/server.ts" ] && [ -f "$KAYIT" ]; then
  python3 - "$KAYIT" "$EKLENTI" <<'PY' || echo "rei-baslat: eklenti kaydı onarılamadı, kurulu eklentiyle sürüyor" >&2
import json, sys
yol, fork = sys.argv[1], sys.argv[2]
surum = json.load(open(fork + "/.claude-plugin/plugin.json")).get("version", "fork")
j = json.load(open(yol))
girdiler = j.get("plugins", {}).get("telegram@claude-plugins-official")
if not girdiler:
    sys.exit("telegram@claude-plugins-official kaydı yok")
g = girdiler[0]
if g.get("installPath") != fork or g.get("version") != surum:
    g["installPath"], g["version"] = fork, surum
    tmp = yol + ".tmp"
    json.dump(j, open(tmp, "w"), indent=2); open(tmp, "a").write("\n")
    import os; os.replace(tmp, yol)
    print("rei-baslat: eklenti kaydı forka yönlendirildi", file=sys.stderr)
PY
fi
exec claude --model opus --effort medium --settings '{"enabledPlugins":{"telegram@claude-plugins-official":true}}' --channels plugin:telegram@claude-plugins-official
