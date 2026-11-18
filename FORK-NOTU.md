# Fork notu

Bu, Rei reposunun paylaşım için temizlenmiş kopyasıdır. Geçmiş taşınmadı, tek commit'le başlar.

## Çıkarılan ya da değiştirilenler
- `sunucu/etc/tanisma.md`: gerçek adlar çıkarıldı, örnek adlarla şablon kaldı. Kendi kişilerini yaz.
- `docs/kurulum-gunlugu.md`: yalnız ilk kurulum adımları kaldı; deney kayıtları çıkarıldı.
- `docs/denetim-2026-10-31.json` (ön denetimin ham sonucu) çıkarıldı. Bazı belgeler hâlâ ona atıf yapar.
- Sunucu ve Mac IP'leri, bot kullanıcı adları, e-posta ve alan adları yer tutucuya çevrildi:
  `<SUNUCU_IP>`, `<SUNUCU_TAILSCALE_IP>`, `<MAC_TAILSCALE_IP>`, `@<bot_kullanici_adi>`, `<yedek_bot_adi>`, `<bildirim_botu>`, `ornek@example.com`, `ops.example.com`, `tools.example.com`, `uygulama.example.com`.

## Repoda hiç olmayanlar
Sırlar repoda yoktu, kopyada da yok. Kendi değerlerini kendin üretip sunucuya yazarsın:
- Telegram bot token'ı: `~/.claude/channels/telegram/.env` (rei kullanıcısı).
- Servis ortamı ve Kuma jetonları: `/etc/rei/*.env`; örnekleri `sunucu/etc/*.env.example`.
- SMS uygulamasının anahtarı, TLS sertifikası ve APK imza dosyası: `~/.config/rei/sms/` (derleme makinesinde).

## Sana özel olmayan, uyarlaman gerekenler
- Belgeler ve betikler asıl sahibinin düzenine göre yazıldı: Frankfurt'taki Rei sunucusu, ayrı bir ana VPS, Mac'e Tailscale üzerinden açılan kapı.
- `sunucu/vps/ziroobank-*` ve `sunucu/bin/rei-ekstre.py` sahibinin kişisel bütçe uygulamasına bağlı. İşine yaramıyorsa `sunucu/kurulum.tsv` ve `sunucu/vps/rei-kapi` ile birlikte çıkar.
- GitHub adresleri (`zziroo/...`), Android paket adı (`net.ziroo.reisms`) ve dosya yolları (`~/ZZ/...`) kendi adlarınla değiştirilmeli.
- `CLAUDE.md` ve `sunucu/rules/sunucu.md` Rei'nin kişiliğini ve kurallarını tanımlar; kendine göre yeniden yaz.
