
# Bu ortam: Mac işi (başsız, Telegram üzerinden)

Yukarıdaki kurallar masaüstü oturumu için yazıldı. Bu oturum Mac'te `rei` kullanıcısıyla, başsız `claude -p` olarak koşar. Yusuf Telegram'dadır, cevabın ona Rei üzerinden gider. Çelişkide bu bölüm geçerlidir.

## Burada olmayanlar
- `mcp__ccd_*` araçları yok: oturum başlığı, `start_session`, `get_usage` adımlarını atla. Yan oturum yok, işi bu oturumda ve alt ajanlarla yürüt.
- Vault yok: `~/ZZ/brain` notlarını arama, not yazma. Kayda değer karar ya da ders varsa özette `## Vault notu` başlığı altında Yusuf'a yaz.
- Tarayıcı, önizleme, masaüstü aracı yok.
- `ssh`, `scp`, deploy, sunucu işi yok. `~/.claude` altına yazma; kurallar her tur root kopyasından yenilenir.
- `sudo` yok, `git push` yok. Push kapının işidir, sen yalnız kendi dalına commit edersin.

## Rol
- Ana oturum koordinatördür: planlar, ajanlara böler, sonucu gözden geçirir, commit eder. Aşağıdaki ajan türleri bu oturumda tanımlıdır: `inceleme`, `supheci`, `kesif`.
- Alt ajana her zaman `model` ver; kısa iş inline yapılır, çok okuma gerektiren iş devredilir.
- Yazıcı ajanlar seri çalışır ya da ayrık dosyalara dokunur; aynı dosyaya iki ajan yazmaz. Worktree yalıtımı gerekiyorsa `isolation: "worktree"`.
- Alt ajanlar commit etmez, ana oturum commit eder. Worktree'deki işi dala birleştirip worktree'yi sen kaldırırsın: `.claude/worktrees` altında dala geçmemiş iş varsa `bitir` reddedilir.

## İnceleme kapısı
- Kod değiştiren her commit'ten önce yazardan bağımsız bir `inceleme` ajanı diff'i okur. Yalnız `.md` değişikliğinde inceleme gerekmez; `docs/kod-haritasi.md` ve `docs/sunucu-kurulum.md` bu istisnaya girmez.
- Riskli alanda `effort: "high"`: kimlik doğrulama, izinler, sırlar, sunucu ya da API kodu, ödeme, veri taşıma, bu reponun `sunucu/` altı.
- Orta ve üstü bulguda `supheci` her bulguyu koda karşı sınar; doğrulanan yüksek ve kritik bulgular commit'ten önce düzeltilir.
- Tur özetinde tek satır: `inceleme: N bulgu, M doğrulandı, K düzeltildi`; kod değişmediyse `inceleme: kod yok`.

## Sınırlar
- Kod haritası varsa (`docs/kod-haritasi.md`) önce onu oku; dosya ekleyip taşıyan commit'te haritayı güncelle.
- Ajan başına bağlam tavanı 200k. Kendi bağlamın 200k'ya yaklaşırsa işi commit et, durumu özetle ve `SORU:` ile dur.
- Workflow yalnız kapı açtıysa vardır; yoksa eşzamanlı en çok 3 alt ajan.
- Bu Mac'te başka işlerin ağacı ve Yusuf'un dosyaları senin alanın değil. Yalnız bu ağaçta ve bu dalda çalış.
