#!/bin/bash
# rei kullanıcısı içinde kurulum (Aşama 3). kur-rei-mac.sh bunu /Users/rei/bin/'e kopyalar; Yusuf çalıştırır:
#   sudo -u rei -H /Users/rei/bin/kur-rei-kullanici.sh
# Yaptıkları (tekrar çalıştırılabilir): Claude Code (native, ~/.local/bin; root ana kopyası /usr/local/lib/rei-mac/bin/claude varsa kurulmaz,
# tek kopya orada kalır), git kimliği, GitHub salt okunur PAT
# (sorar, ekrana yazmaz; ~/.git-credentials 600), ~/ZZ/rei ve ~/ZZ/brain klonları.
# Sonra ayrı adım: `claude setup-token` → ~/.config/rei/token (aşağıda yazıyor), ardından root ile kur-rei-guncelle.sh (ana kopyayı alır).
set -euo pipefail
[ "$(id -un)" = rei ] || { echo "rei olarak çalıştır: sudo -u rei -H $0" >&2; exit 1; }
cd "$HOME"
export PATH="$HOME/.local/bin:/usr/local/bin:/usr/bin:/bin"
umask 077

echo "== Claude Code"
ANA=/usr/local/lib/rei-mac/bin/claude
if [ -x "$ANA" ]; then CLAUDE_BIN="$ANA"; echo "ana kopya kullanılıyor: $("$ANA" --version)"
elif [ -x "$HOME/.local/bin/claude" ]; then CLAUDE_BIN="$HOME/.local/bin/claude"; echo "zaten kurulu: $("$CLAUDE_BIN" --version)"
else curl -fsSL https://claude.ai/install.sh | bash; CLAUDE_BIN="$HOME/.local/bin/claude"; "$CLAUDE_BIN" --version; fi

echo "== git"
# Commit kimliği Yusuf'unki (2026-11-01 kararı: Mac'teki işlerin commit'leri GitHub'da Yusuf'un katkısı olarak görünsün).
git config --global user.name "Ziroo"
git config --global user.email "ornek@example.com"
# Apple git'in sistem gitconfig'i osxkeychain yardımcısını açar; rei'nin anahtar zinciri yok ("Anahtar Zinciri Bulunamadı" penceresi).
# Boş değer listeyi sıfırlar, sonra yalnız store eklenir.
git config --global --replace-all credential.helper ''
git config --global --add credential.helper store
git config --global pull.ff only
if [ -s "$HOME/.git-credentials" ]; then echo "PAT zaten kayıtlı"
else
  read -rsp "GitHub salt okunur PAT (rei + brain, Contents read; ekrana yazılmaz): " t; echo
  [ -n "$t" ] || { echo "boş" >&2; exit 1; }
  printf 'https://zziroo:%s@github.com\n' "$t" > "$HOME/.git-credentials"; unset t
  chmod 600 "$HOME/.git-credentials"
fi

echo "== klonlar"
mkdir -p "$HOME/ZZ"
for p in rei brain; do
  if [ -d "$HOME/ZZ/$p/.git" ]; then (cd "$HOME/ZZ/$p" && git fetch -q origin && echo "$p: güncel")
  else git clone -q "https://github.com/zziroo/$p" "$HOME/ZZ/$p" && echo "$p: klonlandı"; fi
done

cat <<EOF

Bitti. Son adım, Claude girişi (uzun ömürlü token; SSH oturumunda anahtar zinciri olmadığı için):
  sudo -u rei -H $CLAUDE_BIN setup-token
Çıkan bağlantıyı tarayıcıda aç (claude.ai'de Yusuf'un hesabı), kodu yapıştır; ekrana gelen sk-ant-oat01-… token'ını
şu komutla kaydet (ekrana yazmaz):
  sudo -u rei -H bash -c 'read -rsp "token: " t; echo; umask 077; printf "%s" "\$t" > ~/.config/rei/token'
Sonra kendi kullanıcınla ana kopyayı kur:
  sudo bash ~/ZZ/zProjects/rei/sunucu/mac/kur-rei-guncelle.sh
EOF
