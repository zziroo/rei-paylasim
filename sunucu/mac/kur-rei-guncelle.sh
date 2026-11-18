#!/bin/bash
# Mac işi güncellemesi (root; Mac oturum akışı F1.2). Yusuf kendi terminalinde çalıştırır, IP ve anahtar istemez, tekrar çalıştırılabilir:
#   sudo bash ~/ZZ/zProjects/rei/sunucu/mac/kur-rei-guncelle.sh         kapıları ve ana kopyayı kurar
#   sudo bash ~/ZZ/zProjects/rei/sunucu/mac/kur-rei-guncelle.sh geri    tek adım geri döner
# Yaptıkları:
#   1. Kapılar: /usr/local/sbin/{rei-mac,rei-push,rei-mac-onay} (root 755), /usr/local/lib/rei-mac/{adim.mjs,adim-etiket.mjs} (root 644).
#   2. Ana kopya, LIB=/usr/local/lib/rei-mac (root 755/644; iş yazamaz):
#      bin/claude                 rei'nin ~/.local/bin/claude bağının gerçek dosyası; yalnız yoksa alınır, sonra rei'nin bağı kaldırılır (tek kopya root'ta).
#                                 Sürüm yükseltmek: rei'de yeni sürümü kur, `sudo rm $LIB/bin/claude`, bu betiği yeniden çalıştır.
#      rei-settings.json          sunucu/mac/rei-settings.json; rei-settings-ultra.json ondan jq ile (ultracode:true, workflowSizeGuideline:"medium")
#      claude/CLAUDE.md           global CLAUDE.md + sunucu/mac/mac-is.md
#      claude/agents/, claude/skills/kod-haritasi/, claude/hooks/{ajan-tavani,baglam-uyarisi}.mjs   global kurulumdan; sesli, pano, kaydet, bitir alınmaz
#      agents.json                agents/*.md frontmatter'ından {ad:{description,prompt,tools,model}}; kapı `--agents` ile verir
#      surum                      tek satır: "<rei sha> <claude-kurulum sha>", ağaç kirliyse sha'nın sonunda `+`, repo okunamadıysa `-`
#   3. /Users/rei/agaclar (rei 700), /usr/local/var/rei (root 755), rei /usr/lib/cron/cron.deny içinde.
# Kaynak global kurulum: /Users/$SUDO_USER/ZZ/claude-kurulum/claude. Kapı kural_tazele ile LIB/claude'u her tur ~/.claude'a yazar, `durum` farkı `KURAL:` ile gösterir.
# Canlı kilit ya da bekleyen iş (bitis dosyası olmayan iş dizini) varsa hiçbir şeyi değiştirmeden durur; eski bitmiş işler engel değildir.
# Bir dosya değiştiyse kurulumdan önce her yönetilen dosyanın o anki hali `<dosya>.onceki` olur; hiçbir şey değişmediyse .onceki'ler korunur, yalnız surum yazılır.
# GERİ ALMA (`geri`): .onceki dosyaları yerine döner, LIB/rei-settings.json içeriği rei kimliğiyle ~/.claude/settings.json'a yazılır (eski kapı ayarı oradan okur),
#   ~/.local/bin/claude bağı LIB/bin/claude'a kurulur. Kaynak ve ana kopya silinmez. Bekleyen işin ağacı ~/agaclar'da kalır.
# sshd, sudoers, pmset ve kullanıcılara dokunmaz (onlar kur-rei-mac.sh). Sır okumaz, yazmaz. /Users/rei altı işin yazabildiği yerdir, veridir:
# oradaki okuma, silme ve bağ işlemleri rei kimliğiyle yapılır, root o yollarda dosya içeriği okumaz. macOS bash 3.2.
set -euo pipefail
export PATH=/usr/bin:/bin:/usr/sbin:/sbin
LIB=/usr/local/lib/rei-mac
SBIN=/usr/local/sbin
EV=/Users/rei
SONUC_DIZIN=/usr/local/var/rei
CRON_DENY=/usr/lib/cron/cron.deny
NODE=/opt/homebrew/bin/node
JQ=/usr/bin/jq
ID_DESENI='^[0-9]{8}-[0-9]{6}-[a-z0-9-]{1,20}$'
dur()  { echo "DURDU: $*" >&2; exit 1; }
adim() { printf '\n== %s\n' "$*"; }
[ "$(id -u)" = 0 ] || dur "sudo ile çalıştır"
[ $# -le 1 ] || dur "tek argüman: geri"
komut="${1:-}"
[ -z "$komut" ] || [ "$komut" = geri ] || dur "tanınmayan argüman: $komut (yalnız geri)"
K="$(cd "$(dirname "$0")" && pwd)"
SAHIP="${SUDO_USER:-}"
[[ "$SAHIP" =~ ^[a-z_][a-z0-9_-]{0,31}$ ]] && [ "$SAHIP" != root ] || dur "sudo ile kendi kullanıcınla çalıştır (SUDO_USER: '${SAHIP}')"
KUR="/Users/$SAHIP/ZZ/claude-kurulum"
SRC="$KUR/claude"
cd /           # Yusuf'un klasöründen başlayınca rei kimliğindeki komutlar getcwd uyarısı vermesin
umask 022

rei_k()   { sudo -n -u rei -H "$@"; }
sahip_k() { sudo -n -u "$SAHIP" -H "$@"; }

kilit_durumu() {  # yok | bayat | canlı <pid>; pid dosyası işin yazabildiği yerde, rei kimliğiyle okunur, yalnız rakam alınır
  [ -d "$EV/isler/kilit" ] || { echo yok; return 0; }
  local p
  p=$(rei_k /usr/bin/head -c 20 "$EV/isler/kilit/pid" 2>/dev/null | tr -dc '0-9' || true)
  if [ -n "$p" ] && ps -p "$p" -o command= 2>/dev/null | grep -q -- rei-mac; then echo "canlı $p"; else echo bayat; fi
}
is_denetle() {  # canlı kilit ya da bekleyen iş varsa durur
  local k d ad bekleyen=""
  k=$(kilit_durumu)
  case "$k" in canlı*) dur "çalışan iş var (kilit: $k); bitmesini bekle ya da iptal et" ;; esac
  for d in "$EV"/isler/*; do
    ad="${d##*/}"
    [[ "$ad" =~ $ID_DESENI ]] && [ -d "$d" ] || continue
    [ -e "$d/bitis" ] || bekleyen="$bekleyen $ad"
  done
  if [ -n "$bekleyen" ]; then
    echo "Bekleyen işler:$bekleyen" >&2
    echo "Telegram'dan bitir ya da iptal et. Eski ve ölü bir kayıtsa: sudo -u rei -H touch $EV/isler/<iş-id>/bitis" >&2
    dur "bekleyen iş var, güncelleme çalışan akışı bozar"
  fi
}
# Yönetilen dosyalar: hedef yolları (.onceki bunların yanında durur). bin/claude bilinçli olarak yok: bir kez alınır, geri dönüşte de durur.
YONETILEN="$SBIN/rei-mac $SBIN/rei-push $SBIN/rei-mac-onay $LIB/adim.mjs $LIB/adim-etiket.mjs $LIB/rei-settings.json $LIB/rei-settings-ultra.json $LIB/agents.json $LIB/surum"

son_durum() {
  adim "Durum"
  printf 'sudo -n -u rei -H %s/bin/claude --version: ' "$LIB"
  rei_k "$LIB/bin/claude" --version 2>&1 | head -1 || echo "UYARI: claude çalışmadı"
  echo "kilit: $(kilit_durumu)"
  if [ -f "$LIB/surum" ]; then echo "surum: $(cat "$LIB/surum")"; fi
}

geri_al() {
  adim "Geri alınıyor"
  is_denetle
  local h
  for h in $YONETILEN "$LIB/claude"; do
    if [ -e "$h.onceki" ]; then rm -rf "$h"; mv "$h.onceki" "$h"; echo "döndü: $h"; fi
  done
  [ -f "$LIB/rei-settings.json" ] || dur "$LIB/rei-settings.json yok"
  [ -f "$LIB/bin/claude" ] || dur "$LIB/bin/claude yok"
  [ -d "$EV/.claude" ] && [ ! -L "$EV/.claude" ] || dur "$EV/.claude dizin değil ya da symlink"
  # rei'nin dizinine root yazmaz: içerik stdin'den, yazan rei (dizin denetimden sonra symlink yapılsa da root başka yere yazmaz)
  rei_k /bin/sh -c 'umask 022; rm -f "$1.yeni"; cat > "$1.yeni" && mv -f "$1.yeni" "$1"' sh "$EV/.claude/settings.json" < "$LIB/rei-settings.json" \
    || dur "$EV/.claude/settings.json yazılamadı"
  rei_k /bin/mkdir -p "$EV/.local/bin"
  rei_k /bin/ln -sfn "$LIB/bin/claude" "$EV/.local/bin/claude"
  echo "ayar: $EV/.claude/settings.json; bağ: $EV/.local/bin/claude → $LIB/bin/claude"
  son_durum
}

ana_claude_al() {  # bin/claude: varsa dokunma; yoksa rei'nin bağının gerçek dosyası, içeriği rei kimliğiyle okunur (root başka dosyayı kopyalayamaz)
  mkdir -p "$LIB/bin"; chown root:wheel "$LIB/bin"; chmod 755 "$LIB/bin"
  if [ -f "$LIB/bin/claude" ] && [ ! -L "$LIB/bin/claude" ]; then
    echo "ana kopya var: $LIB/bin/claude"
  else
    local gercek
    gercek=$(rei_k /bin/realpath "$EV/.local/bin/claude" 2>/dev/null || true)
    [ -n "$gercek" ] || dur "$EV/.local/bin/claude yok; önce kur-rei-kullanici.sh"
    rm -f "$LIB/bin/claude.yeni"
    rei_k /bin/cat "$gercek" > "$LIB/bin/claude.yeni" || { rm -f "$LIB/bin/claude.yeni"; dur "claude okunamadı: $gercek"; }
    [ "$(stat -f %z "$LIB/bin/claude.yeni")" -gt 1000 ] || { rm -f "$LIB/bin/claude.yeni"; dur "claude dosyası çok küçük: $gercek"; }
    chown root:wheel "$LIB/bin/claude.yeni"; chmod 755 "$LIB/bin/claude.yeni"; xattr -c "$LIB/bin/claude.yeni" 2>/dev/null || true
    mv -f "$LIB/bin/claude.yeni" "$LIB/bin/claude"
    echo "ana kopya alındı: $LIB/bin/claude"
  fi
}
rei_bagi_kaldir() {  # kurulum bittikten sonra: araya hata girerse eski kapı kendi bağıyla çalışmayı sürdürür
  rei_k /bin/rm -f "$EV/.local/bin/claude"
  if rei_k /bin/test -e "$EV/.local/bin/claude"; then echo "UYARI: $EV/.local/bin/claude silinemedi"; else echo "rei'nin bağı yok: tamam"; fi
}

ajan_json() {  # <agents/x.md> → {"<name>":{description,prompt,tools,model}}; frontmatter tek satırlık anahtarlar, gövde ikinci `---` sonrası
  local f="$1" on govde ad
  [ "$(head -1 "$f")" = "---" ] || dur "frontmatter yok: $f"
  on=$(awk 'NR == 1 { next } /^---$/ { exit } { print }' "$f")
  govde=$(awk 'n >= 2 { print } /^---$/ && n < 2 { n++ }' "$f" | sed '/./,$!d')
  ad=$(printf '%s\n' "$on" | sed -n 's/^name:[[:space:]]*//p' | head -1)
  [[ "$ad" =~ ^[a-z0-9-]{1,40}$ ]] && [ "$ad" = "$(basename "$f" .md)" ] || dur "ajan adı dosya adıyla uyuşmuyor: $f"
  "$JQ" -cn --arg ad "$ad" --arg prompt "$govde" \
    --arg description "$(printf '%s\n' "$on" | sed -n 's/^description:[[:space:]]*//p' | head -1)" \
    --arg tools "$(printf '%s\n' "$on" | sed -n 's/^tools:[[:space:]]*//p' | head -1)" \
    --arg model "$(printf '%s\n' "$on" | sed -n 's/^model:[[:space:]]*//p' | head -1)" \
    '{($ad): {description: $description, prompt: $prompt, tools: ($tools | split(",") | map(gsub("^ +| +$"; ""))), model: $model}}'
}

surum_parca() {  # <repo> → <12 hex sha>[+] ya da -; git, repo sahibinin kimliğiyle koşar (root başkasının repo ayarını okumaz)
  local s kirli
  s=$(sahip_k /usr/bin/git -C "$1" rev-parse HEAD 2>/dev/null | head -c 12 | tr -dc '0-9a-f' || true)
  [ -n "$s" ] || { echo "-"; return 0; }
  kirli=$(sahip_k /usr/bin/git -C "$1" status --porcelain 2>/dev/null | head -1 || true)
  if [ -n "$kirli" ]; then echo "$s+"; else echo "$s"; fi
}

hazirla() {  # her şeyi $HAZIR altında kurar ve doğrular; bu aşamada kurulu hiçbir dosya değişmez
  local f n
  for f in "$K/rei-mac" "$K/rei-push" "$K/rei-mac-onay" "$K/adim.mjs" "$K/../lib/adim-etiket.mjs" "$K/rei-settings.json" "$K/mac-is.md" \
           "$SRC/CLAUDE.md" "$SRC/hooks/ajan-tavani.mjs" "$SRC/hooks/baglam-uyarisi.mjs" "$SRC/skills/kod-haritasi/SKILL.md"; do
    [ -f "$f" ] || dur "kaynak yok: $f"
  done
  [ -x "$JQ" ] || dur "$JQ yok"
  for f in "$K/rei-mac" "$K/rei-push" "$K/rei-mac-onay"; do bash -n "$f" || dur "sözdizimi: $f"; done
  if [ -n "$(find "$SRC/agents" "$SRC/skills/kod-haritasi" -type l 2>/dev/null | head -1)" ]; then dur "kaynakta symlink var"; fi

  mkdir -p "$HAZIR/claude/agents" "$HAZIR/claude/skills" "$HAZIR/claude/hooks"
  { cat "$SRC/CLAUDE.md"; cat "$K/mac-is.md"; } > "$HAZIR/claude/CLAUDE.md"
  cp "$SRC"/agents/*.md "$HAZIR/claude/agents/"
  cp -R "$SRC/skills/kod-haritasi" "$HAZIR/claude/skills/kod-haritasi"
  cp "$SRC/hooks/ajan-tavani.mjs" "$SRC/hooks/baglam-uyarisi.mjs" "$HAZIR/claude/hooks/"
  find "$HAZIR" -name .DS_Store -delete

  "$JQ" -e . "$K/rei-settings.json" > "$HAZIR/rei-settings.json" || dur "rei-settings.json geçersiz"
  "$JQ" '. + {ultracode: true, workflowSizeGuideline: "medium"}' "$HAZIR/rei-settings.json" > "$HAZIR/rei-settings-ultra.json"

  n=0
  for f in "$SRC"/agents/*.md; do ajan_json "$f" >> "$HAZIR/agents.satir"; n=$((n + 1)); done
  [ "$n" -gt 0 ] || dur "ajan yok: $SRC/agents"
  "$JQ" -cs add "$HAZIR/agents.satir" > "$HAZIR/agents.json"; rm -f "$HAZIR/agents.satir"
  [ "$("$JQ" 'length' "$HAZIR/agents.json")" = "$n" ] || dur "agents.json ajan sayısı tutmuyor"
  "$JQ" -e 'all(.[]; (.description | length) > 0 and (.prompt | length) > 0 and (.tools | length) > 0 and (.model | length) > 0)' "$HAZIR/agents.json" > /dev/null \
    || dur "agents.json eksik alan içeriyor"

  printf '%s %s\n' "$(surum_parca "$K")" "$(surum_parca "$KUR")" > "$HAZIR/surum"
  grep -qE '^[0-9a-f+-]+ [0-9a-f+-]+$' "$HAZIR/surum" || dur "surum biçimi: $(cat "$HAZIR/surum")"

  if [ -x "$NODE" ]; then
    for f in "$K/adim.mjs" "$HAZIR/claude/hooks/ajan-tavani.mjs" "$HAZIR/claude/hooks/baglam-uyarisi.mjs"; do "$NODE" --check "$f" || dur "node --check: $f"; done
  else echo "UYARI: $NODE yok; hook'lar çalışmaz (brew install node)"; fi

  chown -R root:wheel "$HAZIR"
  find "$HAZIR" -type d -exec chmod 755 {} +
  find "$HAZIR" -type f -exec chmod 644 {} +
  xattr -cr "$HAZIR" 2>/dev/null || true
}

ikili() {  # sıra: kaynak hedef mod; surum burada yok (yalnız sha değişince .onceki dönmesin)
  printf '%s\n' \
    "$K/rei-mac|$SBIN/rei-mac|755" "$K/rei-push|$SBIN/rei-push|755" "$K/rei-mac-onay|$SBIN/rei-mac-onay|755" \
    "$K/adim.mjs|$LIB/adim.mjs|644" "$K/../lib/adim-etiket.mjs|$LIB/adim-etiket.mjs|644" \
    "$HAZIR/rei-settings.json|$LIB/rei-settings.json|644" "$HAZIR/rei-settings-ultra.json|$LIB/rei-settings-ultra.json|644" \
    "$HAZIR/agents.json|$LIB/agents.json|644"
}
degisti_mi() {
  local satir k h
  while IFS= read -r satir; do
    k="${satir%%|*}"; h="${satir#*|}"; h="${h%|*}"
    [ -f "$h" ] && cmp -s "$k" "$h" || return 0
  done <<< "$(ikili)"
  [ -d "$LIB/claude" ] && diff -r "$HAZIR/claude" "$LIB/claude" > /dev/null 2>&1 || return 0
  return 1
}
kur() {
  local satir k h m
  if degisti_mi; then
    for h in $YONETILEN "$LIB/claude"; do
      rm -rf "$h.onceki"
      if [ -e "$h" ]; then cp -pR "$h" "$h.onceki"; fi
    done
    # Eski kapı ayarı ~/.claude/settings.json'dan okurdu; orada root sahipli dosya duruyorsa geri dönüşün ayarı odur (yeni deny listesi eski kapının klonda çalışmasını keser).
    # Bağ sayısı 1 şartı: rei'nin dizinine hardlink'le konmuş başka bir root dosyası kopyalanmasın. stat yalnız ön eleme: rei dosyayı denetimden sonra
    # rename ile değiştirebilir, bu yüzden içeriği root değil rei okur (PAT'a giden bağ açılamaz), root'un dizininde jq ile doğrulanır, sonra kurulur.
    if [ -f "$EV/.claude/settings.json" ] && [ ! -L "$EV/.claude/settings.json" ] && [ "$(stat -f '%u %l' "$EV/.claude/settings.json")" = "0 1" ]; then
      if rei_k /bin/cat "$EV/.claude/settings.json" > "$HAZIR/eski-ayar.json" 2>/dev/null && "$JQ" -e 'type == "object"' "$HAZIR/eski-ayar.json" > /dev/null 2>&1; then
        install -o root -g wheel -m 644 "$HAZIR/eski-ayar.json" "$LIB/rei-settings.json.onceki"
      else echo "UYARI: eski ~/.claude/settings.json okunamadı ya da geçersiz; geri dönüş ayarı önceki ana kopya kalır"; fi
    fi
    while IFS= read -r satir; do
      k="${satir%%|*}"; h="${satir#*|}"; m="${h##*|}"; h="${h%|*}"
      install -o root -g wheel -m "$m" "$k" "$h"; xattr -c "$h" 2>/dev/null || true
      echo "kuruldu: $h"
    done <<< "$(ikili)"
    rm -rf "$LIB/claude"; mv "$HAZIR/claude" "$LIB/claude"; echo "kuruldu: $LIB/claude"
  else
    echo "dosyalar güncel; .onceki kopyaları korundu"
  fi
  install -o root -g wheel -m 644 "$HAZIR/surum" "$LIB/surum"
}

dizinler() {
  rei_k /bin/sh -c "umask 077; mkdir -p '$EV/agaclar'; chmod 700 '$EV/agaclar'"
  [ -d "$EV/agaclar" ] && [ ! -L "$EV/agaclar" ] || dur "$EV/agaclar dizin değil ya da symlink"
  ls -ld "$EV/agaclar"
  mkdir -p "$SONUC_DIZIN"; chown root:wheel "$(dirname "$SONUC_DIZIN")" "$SONUC_DIZIN"; chmod 755 "$(dirname "$SONUC_DIZIN")" "$SONUC_DIZIN"
  ls -ld "$SONUC_DIZIN"
}
cron_kapat() {  # /usr/lib/cron → /var/at; cron.deny satır satır kullanıcı listesi
  if [ -f "$CRON_DENY" ] && grep -qx rei "$CRON_DENY"; then echo "cron.deny: rei zaten var"
  else
    if [ -s "$CRON_DENY" ] && [ -n "$(tail -c 1 "$CRON_DENY")" ]; then echo >> "$CRON_DENY"; fi
    echo rei >> "$CRON_DENY"; echo "cron.deny: rei eklendi"
  fi
  if crontab -u rei -l > /dev/null 2>&1; then echo "UYARI: rei'nin crontab'ı var (cron.deny çalışanı silmez): sudo crontab -u rei -r"; fi
}

if [ "$komut" = geri ]; then geri_al; exit 0; fi

adim "Denetim: çalışan ya da bekleyen iş"
is_denetle
echo "iş yok, kilit: $(kilit_durumu)"

adim "Hazırlık (kaynak: $K, $SRC)"
mkdir -p "$SBIN" "$LIB"; chown root:wheel "$LIB"; chmod 755 "$LIB"
HAZIR="$LIB/.hazirlik"
rm -rf "$HAZIR"; mkdir "$HAZIR"; trap 'rm -rf "$HAZIR"' EXIT
hazirla
echo "surum: $(cat "$HAZIR/surum")"
"$JQ" -r 'keys | join(" ")' "$HAZIR/agents.json" | sed 's/^/ajanlar: /'

adim "Kurulum"
ana_claude_al
kur
rei_bagi_kaldir

adim "Dizinler ve cron"
dizinler
cron_kapat

son_durum
adim "Bitti. Kontrol: Telegram'dan 'rei-mac durum' KURAL: satırı. Sorun olursa: sudo bash $0 geri"
