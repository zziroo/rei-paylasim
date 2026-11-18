# Rei sunucusu: ilk kurulum adımları

_Paylaşım kopyası: asıl günlüğün yalnız ilk kurulum bölümü; deney kayıtları çıkarıldı._

## İlk kurulum adımları (sır isteyenler Yusuf'un terminalinde)
Yapıldı (2026-10-31): claude.ai girişi (`rei` olarak `claude` → URL → kod; Ultra, auto mod, `/home/rei` güvenilir), Telegram eklentisi (`claude plugin install telegram@claude-plugins-official --scope user`, 0.0.7). Kalanlar sırayla:

1. **Bot token'ı** (BotFather `/newbot`, ad "Rei", kullanıcı adı `<bot_kullanici_adi>`; alınmışsa `<yedek_bot_adi>`). Yusuf, Mac Terminal sekmesinde:
   ```bash
   ssh -t vpn 'sudo -iu rei'
   ```
   ```bash
   mkdir -p ~/.claude/channels/telegram && chmod 700 ~/.claude/channels/telegram && read -rsp 'Bot token: ' T && echo && printf 'TELEGRAM_BOT_TOKEN=%s\n' "$T" > ~/.claude/channels/telegram/.env && chmod 600 ~/.claude/channels/telegram/.env && unset T && echo yazildi
   ```
   (Alternatif: `claude` içinde `/telegram:configure <token>`; aynı dosyaya yazar.) Sonra `exit`.
2. **Servisi ilk kez başlat** (Claude, root olarak; static mod eşleştirmeyi kapattığı için `rei.env` ilk turda YAZILMAZ):
   ```bash
   ssh vpn 'systemctl enable --now rei && sleep 8 && sudo -u rei tmux -L rei capture-pane -p -t rei | tail -20'
   ```
   `~/ZZ` için güven sorusu çıkarsa tmux'a `send-keys` ile cevaplanır.
3. **Eşleştirme (yapıldı 2026-10-31):** Yusuf bota mesaj atar, bot 6 haneli kod döner. **`/telegram:access pair <kod>` oturum içinde ÇALIŞMAZ:** managed settings `Edit(~/.claude/channels/**)` yasağı access.json yazmayı engeller (istenen davranış: ajan kendi allowlist'ini değiştiremez). Onun yerine root yazar: `~/.claude/channels/telegram/access.json` → `{"dmPolicy":"allowlist","allowFrom":["<Yusuf'un sayısal Telegram kimliği>"],"groups":{},"pending":{}}` (rei:rei, 600; kimlik, bekleyen kodun `senderId` alanında görünür). `approved/<kimlik>` adlı boş dosya bırakılırsa bot "Paired!" mesajı atar ve dosyayı siler. Sunucu access.json'ı her mesajda yeniden okur, yeniden başlatma gerekmez (static mod hariç).
4. **Allowlist'i dondur:** `cp /etc/rei/rei.env.example /etc/rei/rei.env && chmod 600 /etc/rei/rei.env && systemctl restart rei` (`TELEGRAM_ACCESS_MODE=static`).
5. **Kuma ölü adam monitörü (yapıldı 2026-10-31, Chrome üzerinden Claude; monitör id 28):** ops.example.com'te Yeni monitör → tip **Push**, ad `Rei`, kalp atışı 10 dk, tekrar 1, bildirim `<bildirim_botu>`. Push URL'sindeki jetonu (`/api/push/<JETON>?...`) sunucuya yaz:
   ```bash
   ssh -t vpn 'read -rsp "Kuma jetonu: " J; echo; printf "KUMA_URL=https://ops.example.com\nPUSH_REI=%s\n" "$J" > /etc/rei/kuma.env; chmod 600 /etc/rei/kuma.env; /usr/local/sbin/rei-ping.sh && echo gönderildi'
   ```

