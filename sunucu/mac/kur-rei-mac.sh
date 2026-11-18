#!/bin/bash
# Mac'te Rei kapısının kurulumu (root; Aşama 3). Yusuf kendi terminalinde çalıştırır:
#   sudo bash ~/ZZ/zProjects/rei/sunucu/mac/kur-rei-mac.sh <Rei sunucusu Tailscale IP> "<rei_mac.pub satırı>" ["<rei_mac_onay.pub satırı>"]
# Yaptıkları (tekrar çalıştırılabilir, sır içermez):
#   1. `rei` standart kullanıcısı (yoksa; rastgele şifre, giriş ekranında gizli, admin değil; giriş yalnız SSH anahtarıyla);
#      kabuğu /bin/sh (sshd command= için `sh -c`: etkileşimsiz sh dotfile okumaz; zsh olsaydı rei'nin yazdığı ~/.zshenv kapıyı shell'e çevirirdi)
#   2. /usr/local/sbin/rei-mac (root 755)  3. /etc/ssh/authorized_keys.d/rei (root 644; restrict,from=,command=)
#   4. /etc/ssh/sshd_config.d/200-rei.conf: `Match User rei` … `Match all` (Include 19. satırda; blok Match all ile kapanır), `sshd -t` geçmezse geri alır
#   5. SSH erişim grubu (com.apple.access_ssh) yalnız rei (grup yoksa Uzaktan Giriş açılmaz); Uzaktan Giriş'i açmayı dener, -getremotelogin ile doğrular
#   6. /usr/local/lib/rei-mac/rei-settings.json (root 644; kapı `--settings` ile verir, ~/.claude/settings.json yazılmaz; yumuşak sınır, asıl sınır OS)
#   7. /Users/rei altı dizinler (700; `agaclar` işlerin worktree'leri). /Users/rei altı işin yazabildiği yerdir: dizinleri ve kopyaları rei kimliği açar,
#      root orada mkdir/chown/chmod/install yapmaz (iş ~/bin'i /usr/local/sbin'e bağlasa betik yeniden çalışınca dizin rei'nin olurdu)
#   8. /Users/<sudo kullanıcısı> 700 (rei okuyamasın)  9. /Users/rei/bin/kur-rei-kullanici.sh (sonraki adım)
#  10. Güç adaptöründe uyku kapalı, ağdan uyanma açık (pmset -c)
#  11. Push (2026-11-01): /usr/local/sbin/rei-push (root 755), /etc/sudoers.d/rei-push (visudo -cf, 440), /etc/rei (root 700);
#      PAT dosyası /etc/rei/github-push.env'i betik YAZMAZ: yoksa sonunda Yusuf'un terminalinde çalıştıracağı komutu basar
#      (fine-grained PAT: yalnız rei + brain, Contents RW; Yusuf üretir). Varsa `rei-push kontrol` ile izinleri gösterir.
#  13. Adım günlüğü hook'u: /usr/local/lib/rei-mac/{adim.mjs,adim-etiket.mjs} (root 644; rei-settings.json'daki PostToolUse/Stop
#      hook'u /opt/homebrew/bin/node ile çalıştırır; node yoksa uyarır, hook sessizce çalışmaz).
#  12. Onay kapısı (3. argüman verilirse): `reionay` gizli kullanıcısı (kabuk /bin/sh, admin değil), /usr/local/sbin/rei-mac-onay
#      (root 755), /etc/ssh/authorized_keys.d/reionay (restrict,from=,command=), sshd Match bloğu ve erişim grubu iki kullanıcı;
#      yalnız o `rei-push birlestir` çalıştırabilir (iş `rei` olarak koşar, onayı atlayamasın). `reionay` zaten varsa 3. argüman zorunludur:
#      verilmezse sshd bloğu ve erişim grubundan düşer, onay kapısı sessizce kapanırdı.
# Sonraki adım (ana kopya: claude, kural dosyaları, ajanlar; dizinler; cron.deny): kur-rei-kullanici.sh'den sonra kur-rei-guncelle.sh.
# Günlük kapı ve kural güncellemesi için bu betik değil kur-rei-guncelle.sh çalışır (IP ve anahtar istemez, .onceki saklar, `geri` alır).
# GERİ ALMA: rm /etc/ssh/sshd_config.d/200-rei.conf /etc/ssh/authorized_keys.d/rei /usr/local/sbin/rei-mac;
#   dseditgroup -o edit -d rei -t user com.apple.access_ssh; systemsetup -setremotelogin off;
#   sysadminctl -deleteUser rei (ev dizini de gider); pmset -c sleep 1 (eski değer); chmod 750 /Users/ziroo (eski değer);
#   rm /usr/local/sbin/rei-push /etc/sudoers.d/rei-push /etc/rei/github-push.env (PAT'ı GitHub'da da iptal et);
#   rm /usr/local/sbin/rei-mac-onay /etc/ssh/authorized_keys.d/reionay; dseditgroup -o edit -d reionay -t user com.apple.access_ssh;
#   sysadminctl -deleteUser reionay; rm -rf /usr/local/lib/rei-mac /usr/local/var/rei; /usr/lib/cron/cron.deny'dan rei satırı.
set -euo pipefail
[ "$(id -u)" = 0 ] || { echo "sudo ile çalıştır" >&2; exit 1; }
IP="${1:-}"; PUB="${2:-}"; PUB_ONAY="${3:-}"
[[ "$IP" =~ ^100\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$ ]] || { echo "1. argüman Rei sunucusunun Tailscale IP'si olmalı (100.x.y.z)" >&2; exit 1; }
[[ "$PUB" =~ ^ssh-ed25519\ [A-Za-z0-9+/=]+(\ [[:print:]]*)?$ ]] || { echo "2. argüman rei_mac.pub satırı olmalı (ssh-ed25519 AAAA… rei@rei-sunucu)" >&2; exit 1; }
[ -z "$PUB_ONAY" ] || [[ "$PUB_ONAY" =~ ^ssh-ed25519\ [A-Za-z0-9+/=]+(\ [[:print:]]*)?$ ]] || { echo "3. argüman rei_mac_onay.pub satırı olmalı" >&2; exit 1; }
[ -z "$PUB_ONAY" ] || [ "$PUB_ONAY" != "$PUB" ] || { echo "onay anahtarı rei anahtarıyla aynı olamaz" >&2; exit 1; }
if [ -z "$PUB_ONAY" ] && id reionay >/dev/null 2>&1; then echo "reionay kullanıcısı var ama 3. argüman (rei_mac_onay.pub) yok: onay kapısı SSH erişiminden düşerdi. Satırı ver ya da kapı güncellemesi için kur-rei-guncelle.sh çalıştır." >&2; exit 1; fi
K="$(cd "$(dirname "$0")" && pwd)"
for f in rei-mac rei-settings.json kur-rei-kullanici.sh kur-rei-guncelle.sh rei-push sudoers-rei-push rei-mac-onay adim.mjs ../lib/adim-etiket.mjs; do [ -f "$K/$f" ] || { echo "$K/$f yok" >&2; exit 1; }; done
SAHIP="${SUDO_USER:-ziroo}"
adim() { printf '\n== %s\n' "$*"; }
rei_k() { ( cd / && sudo -n -u rei -H "$@" ); }   # /Users/rei altındaki yazmalar: symlink izlense de yetki rei'nin yetkisidir

kullanici_kur() {  # <ad> <tam ad>: gizli standart kullanıcı, kabuk /bin/sh, rastgele şifre, admin değil, ev 700
  local ad="$1" tam="$2" sifre
  if ! id "$ad" >/dev/null 2>&1; then
    sifre="$(openssl rand -base64 30)"
    sysadminctl -addUser "$ad" -fullName "$tam" -shell /bin/sh -home "/Users/$ad" -password "$sifre" 2>&1 | grep -viE 'password' || true
    unset sifre
    dscl . -create "/Users/$ad" IsHidden 1
    echo "$ad oluşturuldu (şifre rastgele, kimse bilmiyor; giriş yalnız SSH anahtarıyla)"
  else
    echo "$ad zaten var"
  fi
  dscl . -create "/Users/$ad" UserShell /bin/sh
  [ -d "/Users/$ad" ] || createhomedir -c -u "$ad" >/dev/null
  if dseditgroup -o checkmember -m "$ad" admin >/dev/null 2>&1; then echo "$ad admin grubundaydı, çıkarılıyor"; dseditgroup -o edit -d "$ad" -t user admin; fi
  chown "$ad:staff" "/Users/$ad"; chmod 700 "/Users/$ad"
  id "$ad"; echo "kabuk: $(dscl . -read "/Users/$ad" UserShell | awk '{print $2}')"
}
ONAY_KULLANICI=""   # 3. argüman verildiyse reionay; sshd Match bloğu ve erişim grubu buna göre
[ -z "$PUB_ONAY" ] || ONAY_KULLANICI=reionay

adim "1. rei kullanıcısı${ONAY_KULLANICI:+ (+ $ONAY_KULLANICI onay kullanıcısı)}"
kullanici_kur rei "Rei"
[ -z "$ONAY_KULLANICI" ] || kullanici_kur "$ONAY_KULLANICI" "Rei Onay"   # 5. adım (erişim grubu) bu kullanıcıyı ister

adim "2. /usr/local/sbin/rei-mac"
mkdir -p /usr/local/sbin
install -o root -g wheel -m 755 "$K/rei-mac" /usr/local/sbin/rei-mac; xattr -c /usr/local/sbin/rei-mac 2>/dev/null || true
bash -n /usr/local/sbin/rei-mac && ls -l /usr/local/sbin/rei-mac

adim "3. /etc/ssh/authorized_keys.d/rei"
mkdir -p /etc/ssh/authorized_keys.d; chown root:wheel /etc/ssh/authorized_keys.d; chmod 755 /etc/ssh/authorized_keys.d
printf 'restrict,from="%s",command="/usr/local/sbin/rei-mac" %s\n' "$IP" "$PUB" > /etc/ssh/authorized_keys.d/rei
chown root:wheel /etc/ssh/authorized_keys.d/rei; chmod 644 /etc/ssh/authorized_keys.d/rei
cat /etc/ssh/authorized_keys.d/rei

adim "4. sshd_config.d/200-rei.conf (Match User rei … Match all)"
CONF=/etc/ssh/sshd_config.d/200-rei.conf
# Uzaktan Giriş hiç açılmamışsa sunucu anahtarları yoktur ve `sshd -t` "no hostkeys available" der; eksikleri üret.
[ -f /etc/ssh/ssh_host_ed25519_key ] || { echo "sshd sunucu anahtarları üretiliyor"; ssh-keygen -A >/dev/null; }
grep -q '^Include /etc/ssh/sshd_config.d/\*' /etc/ssh/sshd_config || { echo "sshd_config'de Include /etc/ssh/sshd_config.d/* yok; betik bunu varsayıyor" >&2; exit 1; }
MATCH_USER="rei${ONAY_KULLANICI:+,$ONAY_KULLANICI}"
cat > "$CONF.yeni" <<EOF
# rei-mac kapısı (Rei sunucusu → claude -p) ve onay kapısı (reionay); kaynak zziroo/rei sunucu/mac/kur-rei-mac.sh.
# Match bloğu dosya sonunda 'Match all' ile kapanır; ana sshd_config'in Include'dan sonraki satırları yine genel kalır.
Match User $MATCH_USER
    AuthorizedKeysFile /etc/ssh/authorized_keys.d/%u
    PasswordAuthentication no
    KbdInteractiveAuthentication no
    PubkeyAuthentication yes
    AllowTcpForwarding no
    AllowAgentForwarding no
    X11Forwarding no
    PermitTunnel no
    PermitTTY no
Match all
EOF
chown root:wheel "$CONF.yeni"; chmod 644 "$CONF.yeni"
[ -f "$CONF" ] && cp "$CONF" "$CONF.eski" || true
mv "$CONF.yeni" "$CONF"
if /usr/sbin/sshd -t; then echo "sshd -t: tamam"; rm -f "$CONF.eski"
else
  echo "sshd -t BAŞARISIZ; 200-rei.conf geri alınıyor" >&2
  if [ -f "$CONF.eski" ]; then mv "$CONF.eski" "$CONF"; else rm -f "$CONF"; fi
  exit 1
fi
/usr/sbin/sshd -T -C user=rei,host=x,addr=127.0.0.1 2>/dev/null | grep -iE '^(authorizedkeysfile|passwordauthentication|permittty)' || true

adim "5. SSH erişimi yalnız rei; Uzaktan Giriş"
if dscl . -read /Groups/com.apple.access_ssh >/dev/null 2>&1; then
  dseditgroup -o edit -a rei -t user com.apple.access_ssh
  [ -z "$ONAY_KULLANICI" ] || dseditgroup -o edit -a "$ONAY_KULLANICI" -t user com.apple.access_ssh
  uyeler="$(dscl . -read /Groups/com.apple.access_ssh GroupMembership 2>/dev/null | sed 's/^GroupMembership://')"
  for u in $uyeler; do [ "$u" = rei ] || [ "$u" = "$ONAY_KULLANICI" ] || { echo "grupta beklenmeyen üye vardı, çıkarılıyor: $u"; dseditgroup -o edit -d "$u" -t user com.apple.access_ssh; }; done
  echo "com.apple.access_ssh üyeleri:$(dscl . -read /Groups/com.apple.access_ssh GroupMembership 2>/dev/null | sed 's/^GroupMembership://')"
  systemsetup -setremotelogin on >/dev/null 2>&1 || true
  if systemsetup -getremotelogin 2>/dev/null | grep -q ': On'; then echo "Uzaktan Giriş: açık"
  else echo "UYARI: Uzaktan Giriş açılamadı (Tam Disk Erişimi gerekebilir). Sistem Ayarları > Genel > Paylaşma > Uzaktan Giriş'i elle aç; erişim 'Yalnızca şu kullanıcılar: rei' olmalı."; fi
else
  echo "UYARI: com.apple.access_ssh grubu yok (Uzaktan Giriş 'tüm kullanıcılar' modunda olabilir); Uzaktan Giriş AÇILMADI."
  echo "       Sistem Ayarları > Genel > Paylaşma > Uzaktan Giriş (i) > 'Yalnızca şu kullanıcılar' → rei; sonra bu betiği yeniden çalıştır."
fi
launchctl print system/com.openssh.sshd >/dev/null 2>&1 && echo "sshd launchd'de kayıtlı" || echo "not: sshd launchd'de görünmüyor (Uzaktan Giriş kapalı?)"

adim "6. Ana kopya ayarı (root sahipli) + adım günlüğü hook'u"
rei_k /bin/sh -c 'umask 077; mkdir -p /Users/rei/.claude && chmod 700 /Users/rei/.claude' || { echo "/Users/rei/.claude hazırlanamadı (symlink?)" >&2; exit 1; }
mkdir -p /usr/local/lib/rei-mac; chown root:wheel /usr/local/lib/rei-mac; chmod 755 /usr/local/lib/rei-mac
install -o root -g wheel -m 644 "$K/rei-settings.json" /usr/local/lib/rei-mac/rei-settings.json
ls -l /usr/local/lib/rei-mac/rei-settings.json
install -o root -g wheel -m 644 "$K/adim.mjs" /usr/local/lib/rei-mac/adim.mjs
install -o root -g wheel -m 644 "$K/../lib/adim-etiket.mjs" /usr/local/lib/rei-mac/adim-etiket.mjs
if [ -x /opt/homebrew/bin/node ]; then
  /opt/homebrew/bin/node --check /usr/local/lib/rei-mac/adim.mjs && echo "adım hook'u: $(/opt/homebrew/bin/node --version)"
  sudo -n -u rei -H /opt/homebrew/bin/node --version >/dev/null 2>&1 && echo "rei node'u çalıştırabiliyor" || echo "UYARI: rei /opt/homebrew/bin/node'u çalıştıramıyor (izinler?)"
else echo "UYARI: /opt/homebrew/bin/node yok; adım günlüğü hook'u çalışmaz (brew install node)"; fi

adim "7. rei dizinleri"
rei_k /bin/sh -c 'umask 077; cd /Users/rei && for d in ZZ isler agaclar bin .config .config/rei .ssh; do mkdir -p "$d" && chmod 700 "$d" || exit 1; done' \
  || { echo "/Users/rei altı dizinler hazırlanamadı (symlink?); elle incele" >&2; exit 1; }
rei_k /bin/sh -c 'umask 077; f=/Users/rei/bin/kur-rei-kullanici.sh; rm -f "$f"; cat > "$f" && chmod 700 "$f"' < "$K/kur-rei-kullanici.sh" \
  || { echo "/Users/rei/bin/kur-rei-kullanici.sh yazılamadı" >&2; exit 1; }
ls -ld /Users/rei/ZZ /Users/rei/isler /Users/rei/agaclar /Users/rei/bin

adim "8. /Users/$SAHIP rei'den kapalı"
chmod 700 "/Users/$SAHIP"
if sudo -u rei ls "/Users/$SAHIP" >/dev/null 2>&1; then echo "UYARI: rei /Users/$SAHIP'i okuyabiliyor!"; else echo "rei /Users/$SAHIP'i okuyamıyor: tamam"; fi
ls -ld "/Users/$SAHIP" /Users/Shared

adim "9. Güç: adaptörde uyku kapalı, ağdan uyanma"
pmset -c sleep 0 womp 1
pmset -g custom | grep -E '^ *(sleep|womp)' || true

adim "10. Push yardımcısı: /usr/local/sbin/rei-push + sudoers"
install -o root -g wheel -m 755 "$K/rei-push" /usr/local/sbin/rei-push; xattr -c /usr/local/sbin/rei-push 2>/dev/null || true
bash -n /usr/local/sbin/rei-push && ls -l /usr/local/sbin/rei-push
grep -qE '^#includedir /(private/)?etc/sudoers.d' /etc/sudoers || { echo "/etc/sudoers'ta #includedir /private/etc/sudoers.d yok; betik bunu varsayıyor" >&2; exit 1; }
visudo -cf "$K/sudoers-rei-push" >/dev/null || { echo "sudoers-rei-push sözdizimi hatalı" >&2; exit 1; }
mkdir -p /private/etc/sudoers.d; chmod 755 /private/etc/sudoers.d
install -o root -g wheel -m 440 "$K/sudoers-rei-push" /private/etc/sudoers.d/rei-push
visudo -c >/dev/null && echo "sudoers: tamam" || { echo "visudo -c BAŞARISIZ; /etc/sudoers.d/rei-push kaldırılıyor" >&2; rm -f /private/etc/sudoers.d/rei-push; exit 1; }
sudo -n -u rei sudo -n -l /usr/local/sbin/rei-push kontrol >/dev/null 2>&1 && echo "rei → sudo rei-push kontrol: izinli" || echo "UYARI: rei, rei-push'u sudo ile çalıştıramıyor (sudo -l)"
sudo -n -u rei sudo -n -l /usr/local/sbin/rei-push birlestir x y >/dev/null 2>&1 && echo "UYARI: rei birlestir çalıştırabiliyor; sudoers yanlış!" || echo "rei → birlestir: reddediliyor (doğru)"

adim "11. /etc/rei/github-push.env (PAT; betik yazmaz)"
mkdir -p /etc/rei; chown root:wheel /etc/rei; chmod 700 /etc/rei
if [ -f /etc/rei/github-push.env ]; then
  chown root:wheel /etc/rei/github-push.env; chmod 600 /etc/rei/github-push.env
  sudo -n -u rei sudo -n /usr/local/sbin/rei-push kontrol || echo "UYARI: rei-push kontrol başarısız (PAT biçimi/izni?)"
else
  cat <<'EOF'
PAT dosyası yok. GitHub > Settings > Developer settings > Fine-grained tokens: yalnız `rei` ve `brain` repoları,
Repository permissions: Contents = Read and write (Metadata otomatik), 90 gün. Sonra kendi terminalinde (ekrana yazmaz):
  sudo bash -c 'umask 077; read -rsp "GitHub push PAT (ekrana yazılmaz): " t; echo; printf "GITHUB_PAT=%s\nGITHUB_SAHIP=zziroo\n" "$t" > /etc/rei/github-push.env; chmod 600 /etc/rei/github-push.env'
  sudo -u rei sudo -n /usr/local/sbin/rei-push kontrol      # beklenen: kontrol: rei mod=main "push":true (brain de)
EOF
fi

if [ -n "$ONAY_KULLANICI" ]; then
  adim "12. Onay kapısı: /usr/local/sbin/rei-mac-onay ($ONAY_KULLANICI kullanıcısı 1. adımda açıldı)"
  install -o root -g wheel -m 755 "$K/rei-mac-onay" /usr/local/sbin/rei-mac-onay; xattr -c /usr/local/sbin/rei-mac-onay 2>/dev/null || true
  bash -n /usr/local/sbin/rei-mac-onay && ls -l /usr/local/sbin/rei-mac-onay
  printf 'restrict,from="%s",command="/usr/local/sbin/rei-mac-onay" %s\n' "$IP" "$PUB_ONAY" > "/etc/ssh/authorized_keys.d/$ONAY_KULLANICI"
  chown root:wheel "/etc/ssh/authorized_keys.d/$ONAY_KULLANICI"; chmod 644 "/etc/ssh/authorized_keys.d/$ONAY_KULLANICI"
  cat "/etc/ssh/authorized_keys.d/$ONAY_KULLANICI"
  sudo -n -u "$ONAY_KULLANICI" sudo -n -l /usr/local/sbin/rei-push birlestir x y >/dev/null 2>&1 && echo "$ONAY_KULLANICI → sudo rei-push birlestir: izinli" || echo "UYARI: $ONAY_KULLANICI birlestir çalıştıramıyor (sudo -l)"
  sudo -n -u "$ONAY_KULLANICI" sudo -n -l /usr/local/sbin/rei-push yayinla x y >/dev/null 2>&1 && echo "UYARI: $ONAY_KULLANICI yayinla çalıştırabiliyor; sudoers yanlış!" || echo "$ONAY_KULLANICI → yayinla: reddediliyor (doğru)"
  /usr/sbin/sshd -T -C "user=$ONAY_KULLANICI,host=x,addr=127.0.0.1" 2>/dev/null | grep -iE '^(authorizedkeysfile|permittty)' || true
else
  echo; echo "not: 3. argüman (rei_mac_onay.pub) verilmedi; onay kapısı (reionay, rei-mac birlestir) kurulmadı."
fi

adim "Bitti. Sıradaki: ilk kurulumda sudo -u rei -H /Users/rei/bin/kur-rei-kullanici.sh, sonra her durumda sudo bash $K/kur-rei-guncelle.sh (ana kopya, ultra ayarı, kural dosyaları, cron.deny; o çalışmadan iş başlamaz)"
