# Rei: gün içi asistan taraması

Tarih: 2026-11-02. Durum: kuruldu, `rei-tarama.timer` açık. İlk gerçek tur 2026-11-02 22:00, görülmedi.
İstek (Yusuf): "Bir asistan gibi ayarlamak, belki günde birden fazla." "En yakın asistanım, en yakın arkadaşım olacak, çok resmi konuşmasına gerek yok."

## 1. Kısa cevap

Günde dört kez sunucu telefon mesajlarına, Gmail'e ve takvime bakar. Önemli bir şey varsa Rei botundan samimi, kısa bir mesaj gelir, yoksa susar. Kanal oturumu bu işi yapmaz ve okunan metni görmez. Hata sessiz kalmaz, tarama izi sabah özetinde her gün görünür.

## 2. Yusuf'un kararları (2026-11-02)

| Soru | Karar |
|---|---|
| Kapsam | Genel tarama, ana kaynak mesaj ve Gmail, takvim de var |
| Sıklık | 09, 13, 18, 22 İstanbul. Gece 22'den sonra sessiz, gece gelenler 09:00 turunda |
| Önemli | Cevap bekleyen, randevu ve zaman, iş, para dışı acil; ayrıca her durumu kollayan genel analiz |
| Kişisel mesajlar | Hepsi taranır, metin Anthropic'e gider |
| Önemli yoksa | Susar |
| Anlık haber | Yok |
| Bildirim listesi | 8 uygulama kalır, eksik görülürse ad ad eklenir |
| Kim kimdir | Rei çıkarır, Yusuf düzeltir: `sunucu/etc/tanisma.md` |
| Mail | Tam metin okunabilir |
| Ton | Samimi, arkadaş gibi, resmi değil |

"Bildirimlerin hepsi gelmiyor mu" sorusunun cevabı hayır: yalnız beyaz listedeki 8 uygulama gelir, Telegram, banka, ödeme ve doğrulama bilerek kapalıdır. Kara listeye çevirmek seçildi değil: yeni kurulan bir banka uygulaması listede yoksa içerik sunucuya akar.

## 3. Akış

```
rei-tarama.timer (09, 13, 18, 22) → rei-tarama.sh (root)
  → rei-tarama.py (rei)
     1. rei-sms yeni <imleç>: son turdan beri gelen mesajlar
     2. Bağlayıcı süreci (claude -p, haiku, yalnız okuma araçları): Gmail araması, takvim, yeni mailler için get_thread PLAIN_TEXT
     3. Hassas mail eleme: banka, ödeme, kod, fatura biçimli mailin metni okunmaz
     4. Yorum süreci (claude -p, sonnet, araçsız): JSON, ref + tür + tek cümle
     5. Kod metni kurar: gönderen, uygulama ve saat kayıttan, cümle süzgeçten
  → tg-gonder.sh → Telegram
  → rei-tarama.py --onayla: imleçler ancak gönderimden sonra ilerler
```

## 4. Ölçülenler (2026-11-02, Frankfurt)

- **Bağlayıcı `-p` kipinde:** `--setting-sources project` bağlayıcıları yüklemez, `user` yükler. `ENABLE_TOOL_SEARCH=false` gerekir, yoksa araçlar ertelenir ve `--tools ''` altında ToolSearch olmadığından görünmez.
- **Açılış yarışı:** Soru hemen giderse bağlayıcılar `pending`, araç sayısı çoğu denemede 0, model araç çağrısını metin olarak uydurur. `CLAUDE_CODE_MCP_CONNECTOR_PREWAIT_MS` ve `CLAUDE_CODE_MCP_STARTUP_WAIT_MS` işe yaramadı. Akışlı girdiyle (`--input-format stream-json`) soru 6 sn geciktirilince 7/7 hazır. Kod `init` satırında ikisinin de `connected` olduğuna bakar, değilse hata sayar.
- **İzin:** `--permission-mode dontAsk` + `--allowedTools`: izin dışı `list_labels` çağrısı reddedildi (`permission_denials`). `--disallowedTools` yazma araçlarını listeden de çıkarır: 56 araçtan 15'e, önbellek yazımı 58 binden 22 bin jetona. `create_event` gibi takvim yazmaları `--disallowedTools` ile çıkar.
- **Alanlar:** `search_threads` yalnız `id`, `sender` (çoğu kez çıplak adres), `subject` verir, tarih ve parça yok. `get_thread` `PLAIN_TEXT` ayrıca `plaintextBody` verir, `snippet`, `internalDate`, `labelIds` da gelir. Takvimde `events[].start.dateTime` ya da `date`.
- **Aynı süreçte ikinci soru:** Akışlı girdide `result`tan sonra yeni soru çalışır, bağlayıcı yeniden bağlanmaz.
- **Maliyet (liste fiyatı, mail parçasıyla ölçüldü, tam metin bağlayıcı tarafını biraz artırır):** Son 24 saat, 40 mesaj ve 23 mail ile tek tur: haiku yorum 0,014 $, sonnet yorum 0,051 $. Bu en kötü pencereye yakın: normal turda veri dörtte bir. Günlük tahmin 0,15 $ civarı, haftalık limitin içinde önemsiz. Boş turda yalnız bağlayıcı çalışır (yaklaşık 0,01 $), mesaj ve mail yoksa yorum süreci hiç açılmaz.
- **Kurulu sürüm, tam mail metniyle:** 39 mesaj ve 23 mail, 4 haber, 0,09 $ (ilk tur gibi en kötü pencere).
- **Model:** Aynı veride haiku bir iş sorusunu kaçırdı ve 8 satır gürültülü sonuç verdi, sonnet 6 satırda soruyu yakaladı, tonu daha iyiydi. Bağlayıcı haiku kalır, yorum sonnet.

## 5. Güvenlik

Mesaj ve mail metni güvenilmezdir, SMS'i herkes atabilir.

- **Yorum sürecinin aracı yok:** `--tools ''`, `--strict-mcp-config`, `--setting-sources project`, kanca yok (`REI_KANCASIZ`). Ne yazarsa yazsın hiçbir şey çalıştıramaz. Çıktısı yalnız JSON'dur.
- **Bağlayıcı sürecinin yazısı okunmaz:** Mailin tam metni bu sürecin bağlamına girer, ama izinli üç araç salt okunurdur. Python veriyi modelin metninden değil, `tool_result` içeriğinden alır ve yalnız beklenen girdiyle yapılan ilk çağrıyı kullanır. Mail metni modeli başka bir arama yapmaya kandırsa da sonuç yok sayılır. Gönderme, taslak, etiket ve takvim yazma araçları hem izinli değil hem listeden çıkarılmıştır, managed deny de geçerlidir.
- **Metni kod kurar:** LLM yalnız `ref`, `tur`, `metin` verir. Gönderen, uygulama ve saat kayıttan gelir, bilinmeyen `ref` ya da tür düşer. LLM kaydı olmayan bir gönderen uyduramaz.
- **Sahte önem kalan risk:** Saldırgan kendi adıyla "acil" gibi görünen bir mesaj atarsa yorum onu öne çıkarabilir. Gönderen adı da sahte olabilir: WhatsApp ve SMS başlığı, mail görünen adı. Cümle `ref`'e bağlı değildir: aynı turda annenin kaydı varken saldırganın mesajı modeli onun `ref`'iyle kendi cümlesini yazmaya ikna edebilir. Başlık doğru, cümle sahte olur. Bu yüzden özet komut ya da link önermez ve Yusuf'a yalnız bilgi verir, Rei kanal oturumu bu metni görmez, karar Yusuf'tadır.
- **Süzgeç `suz` ve `ad_suz`:** Cümlede link, IBAN, 4+ hane, telefon, harf-rakam kod, tutar ya da komut biçimi varsa cümle tamamen atılır, yerine sabit metin gider. Gönderen adı ve takvim başlığı LLM'siz geçtiği için aynı desenlerden geçer; düz numara gönderen kimliğidir, gösterilir. Alan adı genel desenle değil listeyle aranır: genel desen `server.py`, `README.md` gibi iş cümlelerini düşürür, listede olmayan alan adı geçer. Kara listedir, tek güvence değil. `tg-gonder.sh` bağlantı önizlemesini kapatır: Telegram sunucusu metindeki adrese gitmez.
- **Hassas mail:** Banka, ödeme ve kripto gönderenler (telefondaki listenin karşılığı) ve kod, şifre, fatura, ödeme biçimli konular: `get_thread` çağrılmaz, konu yorum sürecine gitmez, yalnız gönderen gider. Liste kara listedir, tam kapanmaz: metin de `parca_suz`'dan geçer, ilk 300 karakterde tutar ya da hane + kod sözcüğü varsa mail gizlenir, metnin geri kalanındaki link, kod ve tutar biçimleri silinir, yoruma en çok 2000 karakter gider. Konu ve gönderen bağlayıcı sürecinin bağlamından geçer, çünkü elemeyi arama sonucu yapar; o sürecin yazısı okunmaz. Sağlık, hukuk ve kimlik içeriğinde ayrım modele bırakılmıştır, tür söylenir, içerik özetlenmez.
- **Veri nerede kalır:** `/var/lib/rei-tarama/durum.json` (rei 700): imleçler, görülen kimlikler, 72 saat geçmiş cümle, sayaçlar. Ham metin yazılmaz. Geçmiş cümle sonraki tura veri olarak gider.
- **Kişi notu:** `/etc/rei/tanisma.md` root:rei 640. Rei okur, yazamaz: güvenilmez metin notu zehirleyemez. Sistem istemine girer, repodadır (repo gizli).

## 6. Hatalar ve görünürlük

- **Yorum alınamazsa:** Sabit metin gider, imleçler yerinde kalır, aynı kayıtlar sonraki turda gelir. Çıktı üst üste 3 tur kullanılamazsa (bozan kayıt) kayıtlar atlanır ve sayısı söylenir, yoksa tarama kalıcı tıkanır. Zaman aşımı ve ağ kesintisi bu sayaca girmez, kayıt kaybettirmez.
- **Gmail ya da takvim okunamazsa:** Mesaj kısmı yine gider, satır eklenir, mail imleçleri ilerlemez. Mail metni gelmezse ya da ikinci soru düşerse takvim korunur, o mailler görüldü sayılmaz, sonraki turda yeniden denenir, 2 günde kendiliğinden düşer.
- **Betik düşerse:** `OnFailure=rei-hata@rei-tarama`, Rei botundan tek mesaj.
- **Telegram gönderimi düşerse:** `--onayla` çalışmaz, sonraki tur eski `bekleyen.json`'u siler ve aynı kayıtları yeniden okur.
- **Sessiz tur izi:** `--durum` sabah özetine her gün tek satır yazar: dünkü tur, haber, hata sayısı, son tur. Son tur 14 saatten eskiyse uyarıya döner.
- **Dedupe:** Mesaj `alindi` + aynı saniyedeki kimlikler, mail son mesaj kimliği (7 gün), etkinlik `id|başlangıç` (7 gün). Etkinlik bir kez söylenir, önümüzdeki 16 saatteyse.

## 7. Kabul

1. `--kuru` gerçek veride okunur çıktı verir (2026-11-02, son 24 saatle).
2. `--deneme` kötü niyetli kayıtlar: talimat, sahte sistem mesajı, `</veri>` kaçışı, link ve IBAN isteği talimat gibi davranmadı, link geçmedi (2026-11-02, 7 kayıt).
3. Aynı turda ikinci çalıştırma aynı şeyi söylemez.
4. Önemsiz turda mesaj gitmez.
5. Sabah özetinde tarama satırı görünür.

## 8. Sınırlar

- Yusuf'un kendi cevapları görünmez: WhatsApp ve SMS'te yazdığını bilmez, "cevap bekliyor" derken zaten cevaplamış olabilir.
- Gelmeyen bildirim taranmaz: açık sohbet, sessize alınmış uygulama.
- Mailin tam metni okunur (Yusuf'un kararı, 2026-11-02), yoruma ilk 2000 karakter gider. Ekler okunmaz.
- Tur başına 12 mailin metni okunur, kalanın yalnız konusu gider. 150 mesajı aşan kısım sonraki tura kalır.
- Metni hiç okunamayan mailin deneme sınırı yok: her tur uyarı gider, 2 gün sonra sorgudan düşer, adı söylenmez. Mail konusu ya da metni bağlayıcı modeli `get_thread` çağırmamaya ikna ederse o turun mailleri böyle kalır.
- Anlık uyarı yok: en kötü gecikme gündüz 5 saat, gece 11 saat.

## 9. Değiştirmek

- **Saat:** `sunucu/systemd/rei-tarama.timer`, `sunucu/kur.sh kur`.
- **Kişi notu:** `sunucu/etc/tanisma.md`, `sunucu/kur.sh kur`. `rei.service` gerekmez, her tur okunur.
- **Model:** `sunucu/lib/rei-tarama.py` `YORUM_MODEL`, `BAGLAYICI_MODEL`.
- **Hassas liste:** `BITISIK`, `SOZCUK_ESIT`, `KONU_HASSAS`; telefondaki `GonderenListesi.kt` ile birlikte düşünülür. Bu dosya güvenlik sınırıdır: inceleme effort high, orta üstü bulguda şüpheci.
