# Mac oturum akışı: uygulama planı

2026-11-01 planı, 2026-11-02 kararıyla kısaldı. Üst bölüm bugünkü durumdur. `# Tarihsel kayıt` başlığının altı uygulama kaydıdır, yeni iş oradan alınmaz. Satır numaraları planın yazıldığı günkü `main`'e göredir.

## Durum (2026-11-02)
- **Faz 1 kapandı, kapı donduruldu.** Üç kabul ve `rei-mac-izle` gerçek denemede geçti. `is` küçük net işler için çalışır, yeni özellik ve yeni proje almaz.
- **Kod işi ve tartışma Claude oturumlarında yapılır.** Telefondan Remote Control kullanılır: Mac'te başlatılan oturumlar otomatik bağlanır, Settings > Code > Remote Control > Folders listesine eklenen klasörlerde telefondan yeni oturum açılır. Rei bu işi taşımaz, Yusuf'u yönlendirir, gerekirse `00-Inbox/rei-*.md` notu bırakır.
- **Neden:** Dağınıklığın kökü, güvenilmez metin okuyan asistana kod yazdırmaktı. Rei Gmail ve web metni okuyor, `ziroo` oturumunda `ssh vps` ve `ssh vpn` root anahtarları var. Bu yüzden Rei `ziroo` oturumuna bağlanmaz, `ziroo` oturumu açmaz. Karar notu vault'ta: `10-Projects/rei/decisions/2026-11-02-rei-okur-isletir-kod-claude-oturumunda.md`.
- **Rei'de kalanlar:** Telegram ve izin kartları, ses, ekstre, Ziroobank, Gmail ve takvim, salt okunur deploy durumu, sorgu kapısı, sabah özeti, gece gözcüsü, alarmlar, vault `00-Inbox` notu.
- **Remote Control sınırları (ölçüldü):** Uygulama Remote Control'den gelen mesajla `start_session`'ı reddediyor. Klasör listesinde "Includes" satırları telefonda seçilemiyor, her proje ayrı eklenmeli. Ekran kilitliyken davranışın sonucu kayıtlı değil, bilinmiyor sayılır.
- **Faz 2 başlamadı.** Faz 2 oturumu yalnız plan yazdı, kod yazmadı.
- **Kod silinmez.** `sunucu/mac/*`, sudoers, Mac kullanıcıları ve Tailscale yerinde kalır. Yaklaşık bir ay `is` kullanılmazsa kaldırma ayrı karardır.

| Parça | Durum |
|---|---|
| Faz 0, Faz 1 | Bitti |
| F2.1-F2.3 | İptal |
| F2.4, F2.5 | Yalnız `is` düzenli kullanılırsa |
| Faz 3 | İptal |
| F4.1-F4.4 | İptal; pano için `ziroo → rei` sudoers kararı verilmedi |
| F4.5 | İhtiyaçla |

## Konuşan `is`

1. Rei iş notunu yazar, `rei-mac is <proje> [ayar] < not.md` çalıştırır. Telegram kartı Yusuf'tan onay alır.
2. Kapı worktree açar, `claude -p --session-id` ilk turu koşar.
3. Tur biter. Son satır `SORU:` ise `soru bekliyor` (düğmeli), değilse `cevap bekliyor`. Push yok, ağaç durur, kilit bırakılır.
4. Rei son mesajı Telegram'a yazar. İlk mesaj plandır: ne anladığı ve ne yapacağı 5 satırda.
5. Yusuf yazar, Rei mesajı aynen `rei-mac devam <id>` ile stdin'den iletir. Kapı `claude -p --resume` ile aynı oturumu sürer. 3. adıma döner.
6. Yusuf "bitir" der, Rei `rei-mac bitir <id>` çalıştırır, kart onaylanır. Kapı inceleme izini okur, `rei-push yayinla` çalıştırır. Sonuç bugünkü `PUSH:` satırıdır, gerekirse `birlestir` düğmesi gelir.

| | Değer |
|---|---|
| Süreç | Tur başına `claude -p`, aynı oturum, `~/agaclar/<id>`, dal `rei/<id>` |
| Kimlik | Setup-token, Rei aracı, yeni kimlik yok |
| İzin | Kart yok, auto mod |
| Süre tavanı | Tur başına 3600 sn, `iptal` var |
| Eşzamanlılık | Çalışan tur tek (kilit); bekleyen işler ağaçlarında durur |

`cevap` ve `devam` aynı kod yoludur: `cevap` soru düğmesinin cevabı, `devam` serbest mesajdır.

## Ortak ayar: model, efor, kota

**Komut:** `is <proje> [opus|sonnet] [low|medium|high|xhigh|max] [workflow|ultracode]`. Sıra serbest, her sınıftan en çok biri, tanınmayan kelime `HATA`.

- **Varsayılan:** opus + medium, Workflow kapalı, eşzamanlı ≤3 alt ajan, boyut `small`.
- **Tepe:** opus + max + ultracode, boyut `medium`. `sonnet ultracode` `HATA` verir. `fable` listede yok.
- **Ayar kapı argümanıdır.** Komut kartta görünür, Rei kendiliğinden yükseltmez, nottaki "ultracode" kelimesi tetiklemez (`workflowKeywordTriggerEnabled:false`). Bu kalite sınırıdır, güvenlik sınırı değil: iş kapıyı yerelden de çağırabilir.
- **Ayar dosyadan okunmaz.** `is` ayarı `--calistir` argümanıyla geçer, `$D/ayar` yalnız gösterimdir. `devam <id>` varsayılanla sürer; varsayılan dışı için Rei her turda `devam-ayar <id> <ayar>` yazar, bu biçim kart ister.

**Kota kuralı (Rei).** Faz 1'de yüzde kaynağı yalnız Yusuf'un söylediği sayıdır, Rei notun başına `Kota:` satırı yazar.

| Haftalık | Davranış |
|---|---|
| Bilinmiyor | Varsayılan; yükseltmede Rei önce yüzdeyi sorar |
| %80 altı | Yusuf ne derse o |
| %80-94 | `high` serbest; üstü için "haftalık %X, ortak havuz" deyip teyit alır |
| %95 ve üstü | Varsayılan ya da düşük; üstü için açık "limit pahasına" sözü |

## Bilinen açıklar
- **`bitir` kartı güvenlik sınırı değildir.** İş `sudo rei-push yayinla`yı kendisi çağırabilir, kapıyı yerelden de çalıştırabilir. Sınır pozitif liste, PAT ve `reionay`; bugünkü `is` de böyledir.
- **Rei aracıdır, kanal güvenilir değildir.** Injection almış Rei `devam` yazabilir. Etkisi bugünkü `cevap` ile aynı: auto mod, dal, pozitif liste.
- **Rei tarafındaki kart komutun yazımına bakar.** Sarmalayıcı seçenekleri ve bilinmeyen alt komutu reddeder, ama kartın çıkıp çıkmadığını bilemez. Verb tırnakla bölünür ya da değişkenden gelirse (`bi""tir`, `$v`) ask kuralı eşleşmeyebilir; Claude Code'un ya da auto sınıflandırıcının bunu durdurup durdurmadığı denenmedi. Rei `~/.ssh/rei_mac_onay` anahtarını kullanabildiği için bu yol `birlestir` için de geçerlidir: `reionay` işi durdurur, Rei'yi durdurmaz. Sınır yine pozitif liste ve PAT'tır.
- İş kendi `claude`'unu başlatabilir; model, efor ve Workflow sınırları yumuşaktır. Süre tavanı turu bağlar, işin toplam süresini bağlamaz.
- İnceleme izi kalite göstergesidir. Bulguların düzeltildiğini kanıtlamaz.
- `mac-is.md` zorlayıcı değil. `kural_tazele` tur içi değişikliği önlemez; `.git/config`, `~/.gitconfig` ve `~/.claude.json` kalır.
- Pozitif liste sahte içeriği engellemez, yerini ve boyutunu sınırlar.
- Mac kapalıysa bekleyen işe erişilmez. Bekleyen iş süresiz durur, ağaç yer tutar.
- Masaüstüyle aynı olmayanlar: tarayıcı, `start_session`, `get_usage`, claude.ai bağlayıcıları (bilinçli kapalı), doğrudan konuşma (`is` Rei üzerinden yürür, doğrudan konuşma Claude oturumundadır).
- **`rei.service` yeniden başlarsa süren turun haberi Rei'ye gelmez.** Bekleme döngüsü Rei'nin oturumunda koşar; 05:00 ve gün içi kurulumlar onu öldürür (Kabul 3'te görüldü). `rei-mac-izle` turu izler, bitince Telegram'a sabit satır yazar; Rei'yi uyandırmaz, Yusuf yazınca Rei `durum`dan devralır. Yeniden başlatmadan hemen önce biten turun haberi yine gelmez; bekleyen işi sabah özeti `Bekleyen Mac işi` ile gösterir, `bitti` işi göstermez.
- Maliyet artar. Her `devam` bağlamı yeniden okur, havuz masaüstüyle ortaktır; M14 ölçtü, F2.4 yalnız `is` düzenli kullanılırsa.
- Ana kopya otomatik güncellenmez, `KURAL:` kaymayı gösterir.

## Koşullu kalanlar

Yalnız `is` düzenli kullanılırsa yapılır. F4.5 ihtiyaçla, satırı tarihsel Faz 4 tablosundadır.
### F2.4 Token takibi
- **Dosya:** `sunucu/lib/rei-kullanim.py:8,10,19-21`, `rei-mac`, `sunucu/sbin/rei-sabah.sh:45-46`.
- **Ne:** Transkript ortak kaynaktır, stream-json gerekmez. Özyinelemeli glob, `argv[3]` oturum süzgeci, gün `-` ise tarih süzgeci yok; `/subagents/` mesaj sayısına girmez, token'a girer. `isfile` + `not islink`, satır başına `isinstance(d, dict)`. Tur sonu ve `bitir`'de `perl alarm 30` ile `$D/kullanim` yazılır, `durum` yalnız okur: `KULLANIM: 1,2M girdi / 34k çıktı, opus/high, 3 alt ajan`. Salt okunur `kullanim <YYYY-MM-DD>`; sabah özeti `timeout 30` ile her durumda satır yazar, raporlanmamış Mac işlerini de sayar.
- **Kim:** Kod, Koordinatör (sunucu), Yusuf terminali (Mac). **Doğrulama:** Toplam elle sayımla tutuyor, Workflow ajanı dahil; FIFO `durum`'u kilitlemiyor; sabah mesajında satır var.
### F2.5 Limit yüzdesi ve maliyet kabulü
- **Dosya:** `rei-mac`, `sunucu/rules/sunucu.md`.
- **Ne:** M7 olumluysa `LİMİT:` satırı (son iş anının değeri). Değilse kaynak Yusuf'un sayısı kalır, kapıda sert limit yok. Küçük bir `ultracode` işi `KULLANIM` ile maliyeti gösterir, kota eşikleri bununla sınanır.
- **Kim:** Kod, Koordinatör. **Doğrulama:** Kabul işinin sayısı karar 2'ye girdi olur.

# Tarihsel kayıt

2026-11-01 planının kalan metni: hakem planının, iki itiraz turunun ve Remote Control ölçümünün sonucu. İptal edilen parçalar işaretlidir, gerekçesi kaybolmasın diye silinmedi.

## Kısa cevap (2026-11-01)

Bu özet 2026-11-02 kararından öncedir; bugünkü durum dosyanın başındadır.

- **Masaüstü kalitesini konuşan `is` taşır.** Faz 1 ek kimlik istemez: bugünkü setup-token ile kural, ajan, kod haritası, model/efor, inceleme izi ve worktree Mac işine gelir.
- **Konuşma Telegram'dan yürür, Rei aracıdır.** Tur bitince oturum kapanmaz. `devam <id>` Yusuf'un mesajını `claude -p --resume` ile aynı oturuma iletir, Yusuf "bitir" deyince push adımı çalışır.
- **Telefon oturumu (Remote Control) ayrı karardır.** Tam claude.ai girişi hesabın tamamını açar: Gmail/Drive bağlayıcıları, rutinler, diğer köprülü oturumlar. Faz 3 ancak Yusuf onaylarsa, ayrı uid ve sandbox ile, R6, R9, R11, R12 ölçümleri geçerse kurulur.
- **Geri dönüş hazır:** Geçit kapanmazsa telefon oturumu kurulmaz, Faz 1'in Telegram konuşması kalır.
- **Hakem planından değişenler:** Worktree Faz 1'e geldi, stream-json çıktı, ana oturum bağlam hook'u (eski F1.5) kalktı, uzun iş beklemesi `is`'e daraldı, vault Faz 2'ye gitti, `_overview`'un düğmesiz main'e gitmesi kararı tersine döndü.

**Sıra:** Faz 0 ölçümü → Faz 1 → Faz 2 → Faz 3 kararı → Faz 4. Faz 4, Faz 3'e bağlı değildir.

## Faz 0: ölçüm (kod yazmadan)

- **Dosya:** yeni `sunucu/mac/deney-rei-mac.sh` (geçici, kurulmaz).
- **Ne:** Aşama 1 M maddelerini `sudo -u rei -H` ile scratch klonda dener, sonucu tek dosyaya yazar. Tam giriş gerektirmez, `~/isler/kilit`'i deney boyunca tutar. Aşama 2 (R maddeleri) yalnız Faz 3 kararından sonra çalışır.
- **Kim:** Kod yazar, Yusuf terminali çalıştırır, Koordinatör sonuçla planı günceller. **Doğrulama:** Her maddenin cevabı yazılı, olumsuz dal seçilmiş.

| # | Soru | Olumsuzsa |
|---|---|---|
| M1 | `rei`'deki `claude --version` | 2.1.316'dan eskiyse önce güncelle |
| M2 | Workflow + arka plan ajanı setup-token ve deny altında; alt ajan env tavanları | Deny'ı dosya adlarına daralt; yine kırıksa `workflow` ve `ultracode` kapalı başlar |
| M3 | Arka plan ajanı varken `result` sayısı, son mesaj SORU mu | Yalnız `is` için stream-json + `/usr/bin/jq` özütleme |
| M4 | `--settings` ile `ultracode:true`: kontrol koşusuna göre fark | `ultracode` kalkar, `workflow high` önerilir |
| M5 | `--restricted --add-dir` taze turda not yazıyor mu | `is` vault'u LLM'siz Inbox yoluna düşer |
| M6 | Alt ajan `rules/mac-is.md`'yi görüyor mu, hafıza `-p`'de yükleniyor mu | Kural `CLAUDE.md` sonuna eklenir |
| M7 | `rate_limit_event` geliyor mu, `utilization` dolu mu | Yüzde Yusuf'tan gelir |
| M8 | `--max-turns` tavanında çıkış kodu | Tavan metinden algılanır |
| M9 | `/usr/bin/python3 -I` `rei` ile çalışıyor mu | Sayım node'a taşınır |
| M10 | `--settings` deny'ları etkin mi | Kurulum durur, dosya kopyası kullanılır |
| M11 | Telegram'da arka plan Bash bitişi Rei'yi uyandırıyor mu (Yusuf dener) | Sabit Bot API bildirimi |
| M12 | Üç ardışık sınıflandırıcı reddi: çıkış kodu, stderr, ask sayaca girer mi | `is`'te ret metni aranmaz, `HATA` genel kalır |
| M13 | `CLAUDE_CODE_SUBPROCESS_ENV_SCRUB=1` yan etkisi; `--agents` proje ajanını eziyor mu | Scrub kapalı kalır; ajan için worktree temizliğine güvenilir |
| **M14** | `claude -p --resume` art arda üç turda aynı oturumu sürdürüyor mu; tur başına bağlam ve maliyet | Geçit: konuşan `is` kurulmaz, `SORU:` köprüsü kalır; maliyet yüksekse tur tavanı konur |

### Faz 0 sonuçları (2026-11-01, `rei`, 2.1.316)

Tablodaki Faz 2 ve Faz 3 atıfları 2026-11-02 iptalinden öncedir.

| # | Sonuç | Karar |
|---|---|---|
| M1 | 2.1.316, token çalışıyor | Güncelleme gerekmez |
| M2 | Workflow ve arka plan ajanı çalışıyor; `CLAUDE_CODE_MAX_CONCURRENT_SUBAGENTS` etkili | `workflow` ve `ultracode` açık, tavanlar env ile |
| M3 | Üç `result`, son `result` boş; metin modunda son boş olmayan satır `SORU:` | Metin modu kalır, stream-json yok; SORU yalnız o turun çıktı dosyasının son boş olmayan satırından okunur |
| M4 | Opus + ultracode Workflow çağırdı, sonnet farksız, Opus kontrol koşusu yok | Belirsiz; `ultracode` argümanı kalır, `sonnet ultracode` `HATA`; F2.5 `ultracode` kabul işinde görülür |
| M5 | Yazıyor | Faz 2 LLM yolu kalır |
| M6 | Alt ajan `~/.claude/rules` görmüyor | **Olumsuz dal:** `mac-is.md` ana kopyada global `CLAUDE.md` sonuna eklenir, `rules/` yok; `CLAUDE.md` alt ajana gidiyor: Kabul 3'te `kesif` bölümü araçsız yazdı (2026-11-02) |
| M6b | `autoMemoryEnabled:false` ve `CLAUDE_CODE_DISABLE_AUTO_MEMORY=1` etkili | İkisi de kullanılır |
| M7 | `utilization` dolu, %77 | Faz 2 (F2.5) |
| M8 | Çıkış 1, `Error: Reached max turns` stdout'ta, stderr boş | `kod≠0` yolu çalışır; limit metni tur çıktısı ve `hata.log`'dan okunur |
| M9 | python 3.9.6, jq 1.7.1, node 24 | Değişiklik yok |
| M10 | `--settings` deny etkin | Kurulum sürer |
| M11 | Arka plan Bash bitince Rei kendiliğinden uyandı ve Telegram'a yazdı (`task-notification`, 2026-11-01) | Olumlu: F1.10'da `run_in_background` + `grep -qE` döngüsü |
| M12 | 5 ret, çıkış 0, `Agent aborted` yok | **Olumsuz dal:** ret metni aranmaz, `HATA` genel kalır |
| M13a | `SCRUB=1` izin modunu `default`a zorluyor | **Olumsuz dal:** scrub kapalı |
| M13b | Belirsiz (alt ajanın son mesajı akışta boştu) | **Olumsuz dal:** worktree temizliğine güvenilir; kabul işinde görülür |
| M14 | Üç tur aynı oturum, hatırladı | Geçit geçti, konuşan `is` kurulur |

### Faz 1 uygulama kararları (plan metnini tamamlar)

- **Yollar:** `LIB=/usr/local/lib/rei-mac`. `--settings` `$LIB/rei-settings.json` ya da `$LIB/rei-settings-ultra.json`. `--agents "$(cat $LIB/agents.json)"`. Hook'lar ana kopyadan mutlak yolla çalışır (`/opt/homebrew/bin/node $LIB/claude/hooks/ajan-tavani.mjs`); `~/.claude/hooks` yazılmaz.
- **Tavanlar:** normal: eşzamanlı 3, oturum 12; `workflow` ve `ultracode`: eşzamanlı 5, oturum 20.
- **Tur çıktısı:** `$D/tur.log` her turda sıfırlanır, tur sonunda `cikti.log`'a eklenir. SORU algısı ve `log` yalnız `tur.log`'a bakar; eski turun SORU satırı yeniden algılanmaz.
- **`log <id>`:** önce `tur.log` (≤20 KB), sonra sabit `--- log sonu ---`, sonra stderr ve adım kuyruğu. Rei Telegram'a yalnız ayraç öncesini taşır.
- **Süreç grubu:** perl satırı `setpgrp` sonrası `$$`'ı `$D/tur.pgid`'e yazar; `iptal` ve tur sonu temizliği değeri `sayi` ile okur, `-gt 1` değilse dokunmaz.
- **Uid temizliği:** Kapı süreç tablosunda ata zinciri `rei-mac` ya da `sshd`'ye varan süreçlere ve `/System/`, `/usr/libexec/`, `/usr/sbin/` yollu sistem süreçlerine dokunmaz. Eşzamanlı `durum` bozulmaz.
- **`devam` mesajı:** ≤8 KB; `cevap` ≤2000 bayt kalır. Parça tavanı `is` ve `devam` için 5.
- **Ayar:** `--calistir <id> <proje> <--ilk|--devam> <model> <efor> <mod>`; dördü de `--calistir` başında beyaz listeyle doğrulanır.
- **`durum`:** liste görünümünde `KURAL:` ve `BEKLEYEN: N` (tüm işler, son 10 ile sınırlı değil). `durum <id>`: kapı bloğu, `--- kapı bloğu sonu ---`, sonra `git.txt` ve `## Claude çıktısı`.
- **Sonuç dosyası:** `/usr/local/var/rei/<id>.txt` (dizin root 755, dosya root 644, geçici dosya + `mv`). İlk satır `PUSH:` ya da `HATA:`, sonraki satırlar en çok 30 `YOL: <yol>` (denetim karakterleri silinir). `push.txt`'ten yalnız `PUSH: atlandı …` ve `HATA: …` kabul edilir.
- **`rei-mac-onay`:** ikinci alt komut `yollar <iş-id> <sha>`, sonuç dosyasının `YOL:` satırlarını basar.
- **Pozitif listeler:** `rei`: `^[^/]+\.md$`, `^docs/[^/]+\.md$`, iki istisna ile. `brain`: `^00-Inbox/[^/]+\.md$`, `^10-Projects/[^/]+/(sessions|decisions|lessons)/[^/]+\.md$`. Dosya modu `100644` değilse dal. `grep -iE`, diff hatası fail-closed.
- **`geri`:** `.onceki` dosyalarını döndürür, `rei-settings.json`'ı `~/.claude/settings.json`'a koyar, `~/.local/bin/claude` bağını ana kopyaya kurar. Eski kapı böylece çalışır.
- **Çalışan iş denetimi:** `kur-rei-guncelle.sh` canlı kilit ya da bekleyen iş varsa durur.

#### Uygulamada değişenler

1. **Yeni verb'ler:** `devam-ayar <id> <ayar>` ve `cevap-ayar <id> <ayar>` sarmalayıcıda `devam` ve `cevap` olur, ask kuralı bunlara bağlıdır. Düz `devam` ve `cevap` sarmalayıcıda ayar almaz, boşluklu argüman reddedilir. Sarmalayıcı ilk argümanı pozitif listeyle denetler, `-` ile başlayan argümanı reddeder; `-- bitir <id>` ve `-l reionay … birlestir` ask kuralını böyle atlayamaz. Mac kapısı `devam <id> [ayar]` biçimini korur.
2. **`yollar <iş-id> <sha>`:** Liste sha'ya bağlıdır. Sarmalayıcı `rei-mac-onay`'a yollar, kart çıkmaz, Rei yolları düğmeden önce yazar.
3. **`PUSH:` satırı:** Yol adı taşımaz, neden sabit metin ve sayıdır, `push_gecerli` tam desenle doğrular. HATA kaydı var olan `PUSH:` kaydını ezmez, sonuç dosyası yalnız `yayinla`'da yazılır.
4. **İnceleme izi:** Ölçüt `end_turn` ya da sonrasında `toolEndsTurn:true`; eski ölçüt 96 gerçek kaydın 95'ine `KANIT YOK` derdi. Dördüncü metin `INCELEME: gerekmiyor (commit yok)`.
5. **Worktree:** `.git/hooks` ve exclude sıfırlaması her tur ve `bitir` başında. `git clean` `.claude/worktrees/`'i dışarıda bırakır, `bitir` dala geçmemiş alt worktree'de `HATA` verir.
6. **`iptal`:** Tur süreç tablosundan bulunur, TERM sonra KILL; sonuç `HATA: durdurulamadı` olabilir. İptal işaretli işte `devam`, `cevap`, `bitir` `HATA` verir, `bitir` sürerken `iptal` `HATA` verir.
7. **Hatalar:** Kapı hataları `HATA:` önekli. Tavanı aşan `devam` ve `cevap` kesilmez, `HATA` verir. `devam` soru bekleyen işe de açık, `cevap` yalnız soruya.
8. **`durum`:** `DAL:` ve `UYARI:` satırları eklendi. İlk turdan önce `KURAL: <sha> (ilk turda yazılır)`. Commit yoksa `bitir` `PUSH: atlandı (commit yok)` der, ağacı siler.
9. **Yarım tur:** Doğrudan `bitir` de iz yoksa dala zorlanır. `--devam` hazırlığı düşerse `bitis` yazılmaz, `belirsiz` yarım SORU'ya döner. Yeni neden `süreç kayboldu`.
10. **Ana kopya ve `geri`:** `.onceki` tek adımlık anlık görüntüdür. `geri` de iş varsa durur, ayarı rei kimliğiyle yazar. Ayara `~/ZZ/**`, `~/bin/**`, `~/.local/**`, `~/Library/**` deny'ları eklendi, eski ayar `.onceki`'dedir. `agents.json` `effort` taşımaz.

## Faz 1: konuşan `is`
### F1.1 Kapı temeli
- **Dosya:** `sunucu/mac/rei-mac:31,34,152-154`, `sunucu/bin/rei-mac:4`.
- **Ne:** `PATH`'e sistem dizinlerinden sonra `/opt/homebrew/bin`. `is` için ≤5 parça, okuma `${parca[2]:-}` biçiminde (bash 3.2 + `set -u`). Ayar ayrıştırıcı. Her zaman dolu `ortak=(...)` dizisi: `--model`, `--effort`, root `--settings`, `--strict-mcp-config`, `--agents`, `-n`. Mod normalse `--disallowedTools Workflow`, `ultracode` ise ultra ayar dosyası. `--settings` dosyası yoksa iş `HATA` ile hemen reddedilir. `CLAUDE=/usr/local/lib/rei-mac/bin/claude`; `DISABLE_AUTOUPDATER=1`, `CLAUDE_CODE_DISABLE_AUTO_MEMORY=1`, `ENABLE_CLAUDEAI_MCP_SERVERS=false`.
- **Kim:** Kod. **Doğrulama:** `bash -n`; sahte `claude` ile her mod bir kez; yanlış kelime `HATA`.
### F1.2 Root ana kopya ve güncelleme betiği
- **Dosya:** yeni `sunucu/mac/kur-rei-guncelle.sh`; `sunucu/mac/kur-rei-mac.sh:35,57,135`; `sunucu/mac/rei-settings.json`; `rei-mac` (`kural_tazele`); `docs/sunucu-kurulum.md:77`.
- **Ne, betik:** IP ve anahtar istemez. `rei-mac`, `rei-push`, `rei-mac-onay` ve kural dosyalarını `install` eder, öncekini `.onceki` saklar. sshd, sudoers, pmset ve kullanıcılara dokunmaz. Tek argümanı `geri`: tek komutta eski hale döner.
- **Ne, ana kopya:** `/usr/local/lib/rei-mac/`: `bin/claude`, `rei-settings.json`, `claude/{CLAUDE.md (global + mac-is.md),agents,skills/kod-haritasi,hooks}`, `agents.json`, `surum` (kaynak sha). `sesli`, `pano`, `kaydet`, `bitir`, `kur.sh` alınmaz. `~/.local/bin/claude` kaldırılır, tek kopya root'ta. Ayara eklenenler: `cleanupPeriodDays:3650`, `workflowKeywordTriggerEnabled:false`, `workflowSizeGuideline:"small"`, `autoMemoryEnabled:false`, `autoMode.environment` (klon sınırı), `ajan-tavani` ve `baglam-uyarisi` hook'ları. `jq` ile `rei-settings-ultra.json`: `ultracode:true`, boyut `medium`.
- **Ne, `kural_tazele`:** Her iş ve tur başında, kilit alınmışken çalışır. `~/.claude` altında pozitif liste: yalnız `projects` (içindeki `*/memory` silinir) ve `jobs` kalır, gerisi silinir. Ana kopyadan `rename` ile atomik yazılır. Kullanıcı `settings.json` silinir, ayar yalnız root `--settings`'ten gelir (kapı `rei` olduğu için root sahipliğini koruyamaz). `durum`, ana kopyayla `diff -r` yapıp `KURAL: <sha>` ya da `KURAL: BOZUK` basar.
- **Ne, kurulum betiği:** `reionay` varken 3. argüman yoksa durur. `agaclar` dizinini açar, `rei`'yi `/usr/lib/cron/cron.deny`'a ekler. Runbook satırı aynı committe düzelir.
- **Kim:** Kod, Yusuf terminali. **Doğrulama:** `~/.claude/CLAUDE.md`'ye konan işaret sonraki turda yok; kurcalanan kural `KURAL: BOZUK` veriyor; `reionay` SSH grubunda kalıyor.
### F1.3 Kural dosyaları
- **Dosya:** yeni `sunucu/mac/mac-is.md`, `rei-mac:103`.
- **Ne, `mac-is.md`:** ≤40 satır; global `CLAUDE.md` çatallanmaz. Taslak `genel-akis` raporunda. Eklenenler: yazıcı ajanlar seri ya da ayrık dosyalarda; özette `inceleme: N bulgu, M doğrulandı, K düzeltildi` ya da `inceleme: kod yok`.
- **Ne, `is` sistem mesajı:** Yusuf Telegram'dadır, cevabın ona Rei üzerinden gider. İlk turda planı 5 satırda yaz, onay bekle. Arka plan görevi ya da Workflow sürerken turu bitirme; SORU son mesajın son satırı olsun. Commit `rei/<id>`'ye, push yok. İş bitince "Telegram'dan bitir de" yaz. ~200k bağlamda commit et ve SORU ile dur.
- **Kim:** Kod. **Doğrulama:** Alt ajan kuralı görüyor; `ssh` denemesi yok; ilk tur plan.
### F1.4 Worktree
- **Dosya:** `rei-mac:85-101,132-135`.
- **Ne:** `A=~/agaclar/<id>`, `git -c core.hooksPath=/dev/null -C $K worktree add -b rei/$id $A origin/$taban`. Stash satırları kalkar. Kapının bütün git çağrıları hook'suz. `--devam` dahil her turda `git clean -ffdx -- .claude CLAUDE.local.md .mcp.json`. İş başında `.git/hooks` boşaltılır, `.git/info/exclude` sıfırlanıp `.claude/worktrees/` satırı yazılır. `bitir` sonrası `worktree remove --force`, `prune`, `branch -D`. Bekleyen ağaç silinmez.
- **Kim:** Kod. `rei-push` bunun için değişmez, dal ortak repodadır. **Doğrulama:** Bekleyen işin commit'lenmemiş dosyası araya başka iş girince duruyor; `exclude` + `.claude/agents` hilesi sonraki işe geçmiyor.
### F1.5 `devam` ve bekleme durumu
- **Dosya:** `rei-mac:113-120,127,221-233`, yeni `devam` kolu.
- **Ne:** Tur sonu push yok. Son satır `SORU:` ise bugünkü gibi `soru`, değilse `$D/bekliyor` yazılır, kilit bırakılır. `devam <id> [ayar]` stdin'den mesajı alır, `cevap` koluyla aynı yoldan `--calistir … --devam` başlatır. Kilit doluysa `HATA: başka tur çalışıyor`. `durum`, `cevap bekliyor` basar. `log <id>` son asistan mesajını verir, Rei bunu Telegram'a taşır.
- **Bekleyen iş:** Süresiz bekler, kilit tutmaz. `durum` listeler, sabah özeti `Bekleyen Mac işi: N` yazar.
- **Kim:** Kod. **Doğrulama:** Üç tur konuşma tek oturum ve tek dalda; arada başka iş çalışıp bitiyor; kilit doluyken `devam` `HATA`.
### F1.6 `durum` ve `bitir`
- **Dosya:** `rei-mac:156-169,190-211`, yeni `bitir` kolu.
- **Ne, `durum`:** `durum <id>`: `AYAR:`, commit ve kirli dosya sayısı, `DURUM:`. Kapı bloğundan sonra sabit ayraç, `## Claude çıktısı` ondan sonra. `PUSH:` root dosyasından gelir (F1.9). `INCELEME:` ayrı dosyadan, 600 bayt sınırının dışında. Satırlar tek satıra zorlanır, desenle doğrulanır.
- **Ne, `bitir <id>`:** Yalnız bitmemiş ve bekleyen iş. Sıra: (1) tur çalışıyorsa `HATA`; (2) kirli ağaçsa `HATA: commit'lenmemiş N dosya`, push yok; Rei durumu Yusuf'a söyler, istenirse `devam` ile commit ettirir; (3) `git.txt`, inceleme izi, `sudo -n rei-push yayinla`; (4) `PUSH:` ise worktree silinir, `HATA:` ise ağaç kalır ve `bitir` yeniden çalışır; (5) `bitis` yazılır.
- **Kim:** Kod. **Doğrulama:** Çalışan ve kirli işte `HATA`; temizde `PUSH:`; iki kez `bitir` güvenli.
### F1.7 `iptal`, süre tavanı, süreç temizliği
- **Dosya:** `rei-mac:106,110,140-144`, yeni `iptal` kolu.
- **Ne:** Tur şöyle koşar: `caffeinate -i perl -e 'setpgrp(0,0); alarm 3600; exec @ARGV' claude ...`. `iptal <id>` süreç grubunu öldürür, push yok, ağaç kalır, `bitis` yazılır. Tur sonunda kendi grubu temizlenir. Kilit boşsa `rei` uid'sinin kapı ve sshd dışı süreçleri de temizlenir; doluysa `durum` `ARTIK SÜREÇ: N` uyarır. Çıktı dosyaları temizlikten sonra yazılır. `~/Library/LaunchAgents` doluysa `durum` uyarır. M2 olumluysa `ultracode` için `CLAUDE_CODE_MAX_CONCURRENT_SUBAGENTS` ve `CLAUDE_CODE_MAX_SUBAGENTS_PER_SESSION`.
- **Kim:** Kod, Yusuf terminali (`cron.deny`, F1.2 kurulumu). **Doğrulama:** Telegram'dan `iptal` süreçleri bitiriyor; `nohup` artığı kalmıyor; süre dolunca dal kaybolmuyor.
### F1.8 Yarım kalan tur
- **Dosya:** `rei-mac:113-120,127,221`.
- **Ne:** `kod≠0` ve transkriptte asistan mesajı varsa kapı `SORU: İş yarıda kaldı (çıkış N), devam edeyim mi? || Devam | Bırak` yazar. Metin `tur.log` ve `hata.log` kuyruğundan beslenir (M8), limit hatası "limit" der. Asistan mesajı yoksa bugünkü `bitti kod=N` ve stderr kalır. `Bırak` kapıda işlenir: `--resume` yok, `PUSH: atlandı (Yusuf bıraktı; dal Mac'te)`. `Devam` sonrası inceleme izi yoksa `bitir` push'u dala zorlar. Çıkış 142 "süre doldu" olur. `belirsiz` + oturum kaydı varsa `durum` soru dosyasını üretir.
- **Kim:** Kod. **Doğrulama:** Düşük `--max-turns` SORU veriyor; `Bırak` push etmiyor; bozuk `--settings` SORU üretmiyor.
### F1.9 Push kuralları ve inceleme izi
- **Dosya:** `sunucu/mac/rei-push:25-33,41,44,95-101`; `sunucu/mac/rei-mac-onay`; `rei-mac:126-131`.
- **Ne, `rei-push`:** `rei` ve `brain` için pozitif liste: main'e ancak yol listede **ve** korumalı listede değilse gider. `rei`: `^[^/]+\.md$` ve `^docs/[^/]+\.md$`; `docs/sunucu-kurulum.md`, `docs/kod-haritasi.md` hariç. Kalan her şey dal + düğme. `KORUMALI_brain`'e `_overview.md`, `Home.md`, `20-Areas/` eklenir, `_overview` her zaman dala düşer. `korumali_bul` varsayılanı `.` olur (fail-closed). `yayinla` 4. argüman olarak yalnız literal `dal` alır, neden metni sabit.
- **Ne, sonuç:** `rei-push`, `PUSH:` satırını ve değişen yol listesini kendisi hesaplar, root sahipli `/usr/local/var/rei/<id>.txt`'ye yazar. Kapı oradan okur, `rei-mac-onay` kart öncesi yolları verir, Rei kartla birlikte yazar.
- **Ne, inceleme:** Akış masaüstüyle aynı: commit'ten önce `inceleme`, riskli alanda effort high, orta ve üstü bulguda `supheci`. Kapı `subagents` altında özyinelemeli arar: `agentType:inceleme` meta + aynı adlı `agent-*.jsonl`'de `end_turn` ile biten son asistan mesajı. `$D/inceleme.txt`: `INCELEME: ajan izi var (içerik doğrulanmadı)`, `INCELEME: KANIT YOK` ya da `INCELEME: gerekmiyor (yalnız .md)`. Satır yoksa `durum` `INCELEME: kayıt yok` basar. Yukarıdaki iki `docs/` dosyasına `.md` istisnası uygulanmaz.
- **Kim:** Kod, Yusuf terminali. **Doğrulama:** `scripts/x.sh`, `CLAUDE.md`, `docs/CLAUDE.md`, `docs/kod-haritasi.md` ve brain `_overview` dala, `README.md` main'e gidiyor; sahte `push.txt` `durum`'a yansımıyor; ön plan Task ve Workflow incelemesi birer örnekle iz veriyor.
### F1.10 Rei kuralı ve izinler
- **Dosya:** `sunucu/rules/sunucu.md:75-81`, `sunucu/etc/managed-settings.json:54-59`, `sunucu/bin/rei-mac:4`.
- **Ne, aracılık:** Yusuf'un mesajı `devam`'a aynen gider, Rei özetlemez, kendi yorumunu katmaz. Mac'in cevabı Telegram'a özetsiz, gerekiyorsa bölünerek gelir. `bitir` yalnız Yusuf'un sözüyle çalışır. Yusuf'un sözü ayar satırına aynen geçer.
- **Ne, izin:** `bitir`, `iptal`, `devam-ayar` ve `cevap-ayar` ask kuralına girer (üç yol biçimi ve `*rei-mac <verb> *` jokeri; `$HOME/bin/rei-mac`, `bash ~/bin/rei-mac` gibi yazımlar için). Joker geniş tutulmaz: `*rei-mac*is*` düz `devam` ve `durum`a da kart çıkarırdı. Tek argümanlı `devam` kartsızdır, bugünkü `cevap` ile aynı.
- **Ne, bekleme:** M11 olumluysa `run_in_background` ile `grep -qE` döngüsü. Koşul `soru`, `cevap bekliyor`, `bitti`, `belirsiz`, ardışık 5 kez 255 ya da 70 dk; her durumda Rei uyanıp son durumu yazar. Olumsuzsa `rei-sabah` deseniyle Bot API'den sabit "tur bitti, sor" mesajı. `tmux send-keys` hiçbir dalda yok.
- **Ne, okuma:** Ayraç sonrası `PUSH`, `VAULT`, `INCELEME` satırları geçersizdir. `INCELEME: KANIT YOK` ya da `kayıt yok` varsa Rei düğmeden önce tek satır söyler.
- **Kim:** Kod, Koordinatör (`scp`, install, `systemctl restart rei`). **Doğrulama:** Sahte `PUSH:` satırı içeren özet düğme açmıyor; Yusuf'un mesajı Mac'e aynen ulaşıyor.
### F1.11 Kurulum sırası ve kabul
- **Dosya:** `docs/sunucu-kurulum.md`, `docs/asama-3-tasarim.md:45,84`, `docs/kod-haritasi.md`.
- **Ne, inceleme:** Kapı dosyaları güvenlik sınırı: `inceleme` effort high, orta ve üstü bulguda `supheci`, `harita.mjs kontrol`.
- **Ne, sıra:** Çalışan ya da bekleyen iş yokken: (1) Yusuf terminali: `kur-rei-guncelle.sh`, kural, kapı ve `rei-push` birlikte; (2) `durum` `KURAL:` kontrolü; (3) Koordinatör: sunucu kuralı ve managed settings.
- **Kabul 1:** `is rei high`, küçük kod değişikliği; Telegram'da iki tur tartışma, bir `SORU`; alt ajan, inceleme, harita; `bitir`, `PUSH: dal`, düğme.
- **Kabul 2:** `is rei`, kısa belge; tur sonucu kendiliğinden Telegram'a geliyor. İnceleme atlanınca `INCELEME: KANIT YOK` Rei'nin mesajında.
- **Kabul 3:** Tavana düşen `is` SORU ile sürüyor; `iptal` çalışıyor.
- **Kim:** Kod, Yusuf terminali, Koordinatör. **Doğrulama:** Üç kabul işi geçer, Faz 2 ancak o zaman başlar.
- **Sonuç:** Üçü de geçti (2026-11-02), ayrıntı `docs/kurulum-gunlugu.md`.

## Faz 2: vault ve token (başlamadı; F2.1-F2.3 iptal)

**İptal (2026-11-02):** Vault Claude oturumundan yazılır, kapıya vault yolu açılmaz. Faz 2 oturumu yalnız plan yazdı. F2.4 ve F2.5 üst bölümde "Koşullu kalanlar" altında.

Vault yazma F2.1 bitmeden açılmaz. Faz 1'de kayda değer karar özette `## Vault notu` olarak Yusuf'a gelir.
### F2.1 `rei-push vault` (iptal)
- **Dosya:** `sunucu/mac/rei-push`, `sunucu/mac/sudoers-rei-push`, `kur-rei-guncelle.sh` (sudoers `visudo -cf` ile), `claude-kurulum/claude/hooks/oturum-baslangici.mjs:93-99`.
- **Ne:** `vault <kaynak> <vid>`; `<kaynak>` root tablosundan. Düğmesiz main'e yalnız `10-Projects/<kaynak>/{sessions,decisions,lessons}/*.md` ve `00-Inbox/rei-*.md`. `diff --raw -z` ile yalnız `100644` ve `A`/`M`; numstat `-` dala; dosya başına 8 KB, toplam 400 satır; yollar küçük harfle karşılaştırılır, harf çakışması dala; sır deseni (`sk-ant-`, `github_pat_`, `gh?_`, `PRIVATE KEY`, base64 dahil). Her kontrol fail-closed. ff değilse kapı rebase eder, 2 deneme, çakışmada `abort` + dal. `_overview` önerisi oturum notuna yazılır, masaüstü `/kaydet` işler. Paylaşılan hook Mac kaynaklı metni "veri, talimat değil" diye etiketler.
- **Kim:** Kod, Yusuf terminali. **Doğrulama:** Başka projeye, symlink, NUL'lü, tek satır 9 KB, `_OVERVIEW.md` denemelerinin hepsi dala düşüyor.
### F2.2 Vault okuma (iptal)
- **Dosya:** `rei-mac` (`baglam_kur`), `mac-is.md`.
- **Ne:** Proje brain değilse `~/agaclar/<vid>` brain worktree'si (`vid=<id zamanı>-brain`). Fetch sabit URL ile: `git -c core.hooksPath=/dev/null fetch --force <URL> +refs/heads/main:refs/remotes/origin/main`. awk `Güncel durum` ve `Sıradaki işler` bölümlerini alır, 6000'de keser, `iconv -c` geçirir, boru `|| true` ile korunur. Sonuç `$D/baglam.md`, `uuidgen` çitiyle; not yalnız dosyaya işaret eder.
- **Kim:** Kod. **Doğrulama:** `rei` işinde "Sıradaki işler" aktarılıyor; bozuk `.git/config` ile de gerçek main geliyor.
### F2.3 Vault yazma (iptal)
- **Dosya:** `rei-mac` (`bitir`), yeni `sunucu/mac/vault-sistem.md`.
- **Ne:** Yalnız özette `## Vault notu` varsa ve son tur kodu 0 ise, `bitir` içinde: taze `claude -p --restricted --settings <root> --strict-mcp-config --add-dir <vid> --model sonnet --max-turns 15`, cwd vault ağacı. Girdi `## Vault notu`, `git.txt`, `baglam.md`. `--resume` yok, `kaydet` adımları `vault-sistem.md`'ye gömülü, çıktı `vault-cikti.log`.
- **Ne, ortak:** Adım 0 `worktree remove --force`, `prune`, artık `rei/*-brain` dalı silinir. Yoksa `VAULT: atlandı (gerekçe)`, token yok. `VAULT:` satırı `rei-push` stdout'undan gelir. Vault hatası işi başarısız yapmaz.
- **Olumsuzsa (M5):** Kapı notu LLM'siz `00-Inbox/rei-<tarih>-<proje>.md` yazar.
- **Kim:** Kod, Yusuf terminali, Koordinatör (kural). **Doğrulama:** Not `main`'e gidiyor; `Vault notu: yok` işinde token harcanmıyor; ağaç reboot sonrası işi kilitlemiyor.

## Faz 3: telefon oturumu (Remote Control), ayrı karar (iptal)

**İptal (2026-11-02):** Rei telefon oturumu açmaz, `reioturum` kurulmaz, tam giriş verilmez, `sunucu/mac/deney-rei-mac.sh` Aşama 2 çalıştırılmaz. Telefondan kod işi Yusuf'un kendi oturumundan Remote Control ile yapılır.

Faz 1 ve 2 oturmadan karar verilmez. Tam giriş `rei`'ye hiç verilmez; yalnız ayrı uid `reioturum`'da, sandbox ile durur. **Geçit:** R6, R9, R11, R12 ölçümü geçmezse, ya da satırı `ÖLÇÜLMEDİ` kalırsa, oturum kurulmaz, tam giriş silinir, Faz 1'in Telegram konuşması kalır. R1, R2, R4 de zorunludur.

| # | Soru | Olumsuzsa |
|---|---|---|
| **R1** | `claude auth login` tutuyor mu, kimlik nerede, SSH oturumundan okunuyor mu | Kimlik dosyası yolu denenir, kapanmazsa kurulmaz |
| **R2** | TTY yok, `nohup` ortamında `--bg` başlıyor, terminal kapanınca yaşıyor mu | Root LaunchDaemon ile başlatma denenir |
| R3 | Worktree'de `Workspace not trusted`; `~/agaclar` güveni alt dizine geçiyor mu; auto ön onayı | Kapı id başına güven kaydı yazar |
| **R4** | `--bg` ile `--settings <dosya>`, `--strict-mcp-config`, `--disallowedTools`, `--append-system-prompt-file`, `--agents`, `--add-dir`, root kopya `claude` | Tutmayan bayrak tek tek çıkarılır; `--agents` ve `--settings` çıkarılamaz |
| R5 | Ask kuralı telefonda kart çıkarıyor mu, deny tutuyor mu, sınıflandırıcı reddi soru mu abort mu (elle) | Ask yerine `mac-is.md` yasağı, Bilinen açıklara yazılır |
| **R6** | `claude stop`, idle 1 saat duruşu, sonraki mesajla devam, Mac yeniden başlarsa ne olur; `respawnFlags` Bash ile kurcalanırsa yeniden doğuşta tutuyor mu (`ps`) | Sandbox bunu kapatmıyorsa root LaunchDaemon 30 sn'de karşılaştırır, farkta oturumu öldürür; o da yoksa kurulmaz |
| R7 | Env işçiye istemciden mi daemon'dan mı geçiyor; tur bitişinden idle'a gecikme; `rei` daemon'u varken root kopya `--bg` devralınıyor mu | Değişkenler `--settings` `env` anahtarına taşınır; devralma varsa kapı başta süreçleri öldürür |
| R8 | Alt ajan, Workflow, hook, `/compact`, `/usage` telefondan çalışıyor mu (elle) | Çalışmayan madde `is`'e ya da masaüstüne kalır |
| **R9** | Kimlik iş araçlarından okunamıyor: sandbox altında `cat .credentials.json`, `~/.claude.json`, `curl`, `~/.claude/jobs` yazımı reddediliyor; `disableClaudeAiConnectors:true` altında köprü açık | Ayrı, bağlayıcısız claude.ai hesabı; o da yoksa kurulmaz |
| R10 | `--cloud` sunucudan Mac oturumuna ulaşıyor mu | Rei oturuma mesaj atmaz, işe engel değil |
| **R11** | İş kendi kartını onaylayamıyor: kart açıkken aynı uid'den (a) `--cloud`, (b) ham `type:user`, (c) `control_response` | Kart "kolaylık, sınır değil" sayılır, ayrı hesap zorunlu |
| **R12** | `reioturum`'dan köprülü masaüstü scratch oturumuna `--cloud` mesajı gitmiyor (held ya da red) | `crossSessionInbound` `refuse`; tutmazsa ayrı hesap |

Betik R5 ve R8'i `ÖLÇÜLMEDİ (elle)` yazar. R6 kurcalama ve idle ile R11 (b)(c) elle ya da ayrı ölçümle tamamlanır, sonucu rapora eklenmeden geçit kapanmaz.
### F3.1 Kimlik ayrımı (ölçümden önce)
- **Dosya:** yeni `sunucu/mac/kur-rei-oturum.sh`, yeni `sunucu/mac/oturum-settings.json`, `sunucu/etc/managed-settings.json`.
- **Ne, uid:** `reioturum`: gizli, sudoers yok, ev 700, kapı dosyalarına erişim yok. `rei` setup-token'da kalır.
- **Ne, root ayar:** `sandbox.enabled`, `failIfUnavailable`, `allowUnsandboxedCommands:false`, `filesystem.denyRead` (`.credentials.json`, `~/.claude.json`), `~/.claude/**` yazım yasağı, ağ izin listesi, `Edit(./.claude/**)` deny, `disableClaudeAiConnectors:true`, `crossSessionInbound:"refuse"`.
- **Ne, diğer oturumlar:** `ziroo` kullanıcı ayarına ve sunucu managed ayarına `crossSessionInbound:"hold"`, sunucuda `Bash(*--cloud*)` ask kuralı.
- **Ne, runbook:** Oturumları iptal et, `/v1/code/triggers` rutinlerini ve bağlayıcı günlüğünü denetle. Kimlik iptali rutini silmez.
- **Kim:** Kod, Yusuf terminali, Koordinatör. **Doğrulama:** Aşama 2 tek komutla çalışıyor; bitince `auth logout` uyarısı rapor sonunda.
### F3.2 `oturum` aç
- **Dosya:** `rei-mac` yeni `oturum` kolu, yeni root betiği, yeni `oturum-sistem.md`.
- **Ne:** Kapı `sudo -u reioturum <root betiği>` ile açar. Not kapı uid'sine ait dosyadan kopyalanır, oturum uid'si yalnız okur. `--agents`, `jq -c` ile tek satır argüman (`--bg` dosyadan okumaz). `--bg --remote-control rei-<id>` ile başlar, `bridgeSessionId` ilk 30 sn'de okunur, `BAĞLANTI:` kapı dizinine kaydedilir, sonra yeniden okunmaz. Mutlak ömür 12 saat. Aynı anda tek oturum. `~/.local/bin/claude` yok; uid'de başta süreç varsa ret.
- **Kim:** Kod, Yusuf terminali. **Doğrulama:** Bağlantı telefonda açılıyor; ikinci `oturum` reddediliyor.
### F3.3 `bitir`, reionay yolundan
- **Dosya:** `rei-mac-onay`, `rei-push`, `sudoers-rei-push`.
- **Ne:** `bitir` `rei-mac-onay`'a taşınır: `reionay` anahtarı + root yardımcısı `claude stop`, `pkill -U reioturum`, sonra `yayinla`. `rei-push` upload-pack uid'si ve `klon_dogrula` yolu değişir. Kirli ağaçta `HATA`, push yok.
- **Kim:** Kod, Yusuf terminali. **Doğrulama:** `reioturum`'dan `yayinla` reddediliyor; yarıda kalan `nohup` süreci `bitir`'den sonra yaşamıyor.
### F3.4 Kurulum sırası ve kabul
- **Ne, sıra:** (1) F3.1; (2) `reioturum`'a tam giriş; (3) Aşama 2 + elle ölçümler; (4) Koordinatör geçidi okur; (5) geçerse F3.2, F3.3, geçmezse `auth logout` ve uid silinir.
- **Ne, kural:** Tartışma ve belirsiz iş `oturum`, net iş `is`; emin değilse Rei `ask` ile sorar.
- **Kabul:** `oturum rei high`, telefonda bir tartışma, bir izin kartı; `bitir`, `PUSH: dal`, düğme.

## Faz 4: paralel ve yeni projeler (F4.1-F4.4 iptal, F4.5 ihtiyaçla)

| Adım | Tetik | Ne | Kim | Doğrulama |
|---|---|---|---|---|
| F4.1 Paralel (iptal) | Faz 2'den sonra bir hafta veri | Yuva kilidi, `MAKS_IS=2`; push öncesi rebase, sıra rebase → inceleme → push | Kod, Yusuf terminali | İki iş çakışmıyor |
| F4.2 Sert inceleme (iptal) | İlk uygulama projesinden önce | Kapının başlattığı ayrı süreç, sabit brif, `--json-schema`, sha pin, `dal` argümanı | Kod, Yusuf terminali | Sahte brif izi geçemiyor |
| F4.3 Yeni proje (iptal) | F4.2'den sonra | `dal` modu, `MODLAR`, `KORUMALI_`, PAT'lar, klon, `sunucu.md` listesi | Kod, Yusuf terminali, Koordinatör | İlk proje dal + düğme |
| F4.4 Pano (iptal) | Karar 3 evet ise | `rei-transkript`, `ziroo → rei` tek satır sudoers, `mac-rei` kaynağı, arşive yazmadan `redact` | Kod, Yusuf terminali | Pano Mac işlerini gösteriyor, ham token yok |
| F4.5 Görünürlük | İhtiyaçla | `AŞAMA:`, alt ajan adım sayacı, bekleyen iş sabah satırı | Kod, Koordinatör | "Ne yapıyor" tek satırda |

## İtiraz kararları

`Faz 3 geçidi` diye işaretli maddeler Faz 3 ile düştü, `Uygulanır` diye işaretli maddeler kapıda duruyor.

### İkinci tur: güvenlik (13)

Yeniden sıralamanın nedeni bu turdur. Betiğin 7 itirazının (kilit, HUP, daemon koşulu, R7 örnekleyici, M4 kontrolü, `security -w`, ARA satırı) hepsi uygulandı.

| # | Önem | Konu | Karar | Yer |
|---|---|---|---|---|
| S1 | kritik | R9 kimliği değil araç yüzeyini ölçüyordu | Faz 3 geçidi: R9 yeniden yazıldı, sandbox, `disableClaudeAiConnectors`, runbook | R9, F3.1 |
| S2 | kritik | Kart ve mesaj aynı kimlikle sahtelenebilir | Uygulanır: yetki iddiası plandan çıktı. Faz 3 geçidi: R11, `crossSessionInbound:"refuse"` | R11, F3.1 |
| S3 | yüksek | Tam giriş diğer köprülü oturumlara uzanır | Faz 3 geçidi: R12, `ziroo` + managed `hold`, `--cloud` ask | R12, F3.1 |
| S4 | yüksek | `state.json` yeniden doğuş bayraklarını taşır | Faz 3 geçidi: R6 genişledi, sandbox yazım yasağı, LaunchDaemon yedeği, 12 saat ömür | R6, F3.1, F3.2 |
| S5 | yüksek | `bitir` kartı sınır değil | Uygulanır: G11 düzeltildi, brain pozitif listeye döndü, Bilinen açıklara yazıldı. Faz 3 geçidi: `reioturum` + `reionay` yolu | F1.9, F3.3 |
| S6 | yüksek | `is` ve `oturum` aynı kimliği paylaşır | Faz 1'de sorun kalkar, tam giriş yok. Faz 3 geçidi: ayrı uid, not kapı dosyası. "Aynı kilit" yedeği reddedilir, ayrı uid zorunlu | F3.1, F3.2 |
| S7 | yüksek | Deney tam girişi karardan önce kurar | Uygulanır: Aşama 2 kararla başlar, `reioturum`'da, uyarı basar, sıra ters döndü. `reideney` reddedilir, `reioturum` aynı işi görür. Otomatik logout reddedilir, geçit okunana dek ölçüm sürebilir | Faz 0, F3.4 |
| S8 | orta | `kural_tazele` kurcalamayı koruyordu | Uygulanır: `diff -r`, pozitif liste, atomik yazım, uid temizliği kilide bakar. `CLAUDE_CONFIG_DIR` reddedilir: `--resume` ve token sayımı transkripti sabit yerde arar | F1.2, F1.7 |
| S9 | orta | Daemon ve `rei`'nin ikilisi devralınabilir | Uygulanır: tek kopya root'ta. Faz 3 geçidi: R7, başta süreç reddi | F1.2, R7, F3.2 |
| S10 | orta | Ayraç öncesi blok da işin dosyasından | Uygulanır: `PUSH` ve yol listesi root dosyasından, desen doğrulaması. `INCELEME` ve `BAĞLANTI` için kapı uid'si Faz 3'te; Faz 1'de `INCELEME` kalite göstergesi kalır | F1.6, F1.9, F3.2 |
| S11 | orta | Betik numaraları plana uymuyordu | Uygulanır: numaralar eşit, `ÖLÇÜLMEDİ` satırı, geçit yalnız bu satırlardan | Faz 3, betik |
| S12 | düşük | Pozitif liste korumalı listeyi yazmıyordu | Uygulanır: "listede VE korumalı değil", doğrulamaya `CLAUDE.md` | F1.9 |
| S13 | düşük | `--bg` `--agents` dosyasını okumaz, auto ön onay ister | Faz 3 geçidi: `jq -c`, `--agents` çıkarılamaz, ön onay R3'te | R3, R4, F3.2 |

### Birinci tur (31)

G güvenlik, D gerçeklik raporu. **Sonuç:** 30 uygulanır, 1 gereksizleşir. Tabloda yalnız alt önerisi reddedilen ya da sonradan değişen satırlar var.

| # | Önem | Konu | Karar | Yer |
|---|---|---|---|---|
| G1 | kritik | `_overview` düğmesiz main | Uygulanır: `_overview` her zaman dal, `<kaynak>` root'tan | F1.9, F2.1 |
| G6 | yüksek | `rei` deny listesi yetersiz | Uygulanır: pozitif liste, plandaki "dal zorlaması yok" kararı döndü | F1.9 |
| G7 | orta | `INCELEME` gizlenebilir | Uygulanır: ayrı dosya, dürüst metin, sert inceleme uygulama projesinden önce | F1.6, F1.9, F4.2 |
| G8 | orta | Sahte `VAULT:`/`PUSH:` | Uygulanır: ayraç, `-brain` kontrolü, stdout, root dosyası | F1.6, F1.10, F2.3 |
| G9 | orta | `$D/ayar` ile yükseltme | Uygulanır: argv, kartlı `devam`, root `claude` | Ortak ayar, F1.1 |
| G10 | orta | Arta kalan süreç | Uygulanır: grup temizliği, `cron.deny`. Çalışan turda uid öldürme reddedilir, uyarıya döner | F1.7 |
| G11 | orta | `Bırak` yine push edebilir | Uygulanır: `Bırak` kapıda işlenir. Kart sınır değildir (S5) | F1.8 |
| G13 | orta | `origin/main` güvenilir değil | Uygulanır: sabit URL, rastgele çit, `baglam.md`. `.git/config` şablonu reddedilir, sabit URL yeter | F2.2 |
| D5 | yüksek | Yarım işte kayıp | Uygulanır: ağaç işe özel, `belirsiz` köprüsü. Otomatik commit reddedilir, ağaç zaten koruyor | F1.4, F1.8 |
| D12 | orta | Ana oturum hook metni | Gereksizleşir: eski F1.5 kalktı; `is`'te kural + süre tavanı | F1.3 |

**Değişmeden uygulanan 21:** vault G2, G12, G14, G15, D9, D10, D11, D14 (Faz 2); hafıza ve proje `.claude` G4, G5, D16 (F1.1-F1.4); kurulum D4, D13, D15 (F1.1, F1.2, F1.11); durdurma ve hata D1, D2, D6 (F1.7, F1.8); bekleme G3, D3, D7 (F1.10); iz ölçütü D8 (F1.9).

## Yusuf'un kararları

1. **Faz 3 tam girişi: yapılsın mı, ne zaman?** Öneri: Faz 1-2 oturduktan sonra karar. Telegram konuşması yetiyorsa hiç açılmaz; bedeli hesabın tamamını ayrı uid'ye açmak. **Sonuç (2026-11-02):** Hayır, Faz 3 iptal.
2. **Kota eşikleri %80 ve %95 uygun mu?** Öneri: evet, F2.5 sonucuna göre gerekirse değişir. **Sonuç (2026-11-02):** Karar verilmedi, F2.5 ile birlikte yalnız `is` düzenli kullanılırsa açılır.
3. **Faz 4'te pano için `ziroo → rei` tek amaçlı sudoers satırı kabul mü?** Öneri: evet, argümansız ve aşağı yönlü. Reddedilirse token takibi `durum` ve sabah özetiyle kalır. **Sonuç (2026-11-02):** Karar verilmedi, F4.4 ile iptal.
4. **İlk uygulama projesi hangisi?** Öneri: `diskort`, `dal` modunda. Rei'nin deploy kapısı zaten yalnız onda var; PAT'ları sen üretirsin. **Sonuç (2026-11-02):** İptal, kapıya yeni proje eklenmez; Rei'nin deploy yetkisi de aynı gün kalktı.
