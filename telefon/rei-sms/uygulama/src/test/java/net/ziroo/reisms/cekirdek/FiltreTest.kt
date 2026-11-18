package net.ziroo.reisms.cekirdek

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tablodaki veriler sahtedir, biçimleri gerçek mesajlardan alınmıştır. */
class FiltreTest {
    private fun sina(beklenen: Karar, tablo: List<Pair<String, String>>) {
        for ((gonderen, metin) in tablo) {
            assertEquals("gönderen=[$gonderen] metin=[$metin]", beklenen, Filtre.degerlendir(gonderen, metin, null))
        }
    }

    private fun sinaUclu(beklenen: Karar, tablo: List<Triple<String, String, String?>>) {
        for ((gonderen, metin, sohbet) in tablo) {
            assertEquals("gönderen=[$gonderen] metin=[$metin] sohbet=[$sohbet]", beklenen, Filtre.degerlendir(gonderen, metin, sohbet))
        }
    }

    @Test fun turkceKodMesajlariElenir() = sina(
        Karar.ELE,
        listOf(
            "Bilinmeyen" to "Giriş kodunuz: 482913. Bu kodu kimseyle paylaşmayın.",
            "Trendyol" to "Doğrulama kodunuz 751204. Kod 3 dakika geçerlidir.",
            "Getir" to "DOĞRULAMA KODUNUZ 123 456",
            "e-Devlet" to "Tek kullanımlık şifreniz 12-34-56",
            "Turkcell" to "TEK KULLANIMLIK ŞİFRE: 5521",
            "Hepsiburada" to "Onay kodu: 4821",
            "Yemeksepeti" to "Sipariş onay kodunuz 998877",
            "Bilinmeyen" to "Parolanız 77331100 olarak belirlendi",
            "Bilinmeyen" to "Giriş şifreniz: 90817263",
            "Bilinmeyen" to "Kodunuz 123456 120 saniye geçerlidir",
            "Bilinmeyen" to "Kodunuz: 12 34 56 78",
        ),
    )

    @Test fun ingilizceKodMesajlariElenir() = sina(
        Karar.ELE,
        listOf(
            "Google" to "G-482913 is your Google verification code.",
            "Instagram" to "123 456 is your Instagram code",
            "Amazon" to "Your OTP: 90817263",
            "Discord" to "Passcode 1111",
            "Microsoft" to "Use verification 445566 to sign in",
        ),
    )

    @Test fun turkceHarfTuzaklari() = sina(
        Karar.ELE,
        listOf(
            "x" to "ŞİFRE 1234",
            "x" to "ŞIFRE 1234",
            "x" to "SIFRE 1234",
            "x" to "şifre 1234",
            "x" to "ŞİFRENİZ 1234",
            "x" to "DOĞRULAMA 1234",
            "x" to "DOGRULAMA 1234",
            "x" to "doğrulama 1234",
            "x" to "TEK KULLANIMLIK 1234",
            "x" to "Tek Kullanimlik 1234",
            "x" to "tek kullanımlık 1234",
            "x" to "ONAY 1234",
            "x" to "KOD 1234",
            "x" to "OTP 1234",
            // Birleşen işaretli yazım
            "x" to "şifre 1234",
            "x" to "ŞİFRE 1234",
            // Görünmez karakter ve bölünmez boşluk
            "x" to "k​od 1234",
            "x" to "tek  kullanımlık 1234",
            // Arap rakamları ve tam genişlik rakamlar
            "x" to "kod ٤٨٢٩١٣",
            "x" to "kod １２３４",
        ),
    )

    @Test fun yalnizHaneMesajiElenir() = sina(
        Karar.ELE,
        listOf(
            "Ahmet" to "1234",
            "Ahmet" to "12345678",
            "Ahmet" to "123 456",
            "Ahmet" to "12-34-56",
            "Ahmet" to "  7788 ",
            "Ahmet" to "482913.",
        ),
    )

    @Test fun gonderendekiKodSozcuguDeSayilir() = sina(
        Karar.ELE,
        listOf("OTP Servisi" to "Numaranız 5678", "Doğrulama" to "Giriş için 123456"),
    )

    @Test fun bankadanGelenKodTamamenElenir() = sina(
        Karar.ELE,
        listOf(
            "Garanti BBVA" to "Tek kullanımlık şifreniz 482913. Bu şifreyi kimseyle paylaşmayınız.",
            "AKBANK" to "İnternet şubesi giriş şifreniz: 123456",
            "ISBANK" to "Doğrulama kodu 7755",
            "QNB" to "9988 7766 onay kodunuzdur",
            "Papara" to "Papara doğrulama kodunuz 112233",
        ),
    )

    @Test fun bankaGonderenleriGizlenir() = sina(
        Karar.GIZLI,
        listOf(
            "Garanti BBVA" to "Size özel kampanyayı kaçırmayın",
            "GARANTIBBVA" to "Bu ay kredi faiz oranlarımız düştü",
            "Akbank" to "Yeni şubemiz açıldı",
            "İşbank" to "Merhaba",
            "İş Bankası" to "Merhaba",
            "ISBANK" to "Merhaba",
            "Yapı Kredi" to "Merhaba",
            "YAPIKREDI" to "Merhaba",
            "YKB" to "Merhaba",
            "Ziraat Bankası" to "Merhaba",
            "ZIRAATBANK" to "Merhaba",
            "VakıfBank" to "Merhaba",
            "Halkbank" to "Merhaba",
            "HALK BANKASI" to "Merhaba",
            "QNB" to "Merhaba",
            "QNB Finansbank" to "Merhaba",
            "ENPARA" to "Merhaba",
            "Enpara.com" to "Merhaba",
            "DenizBank" to "Merhaba",
            "Deniz Bank" to "Merhaba",
            "TEB" to "Merhaba",
            "ING" to "Merhaba",
            "ING Bank" to "Merhaba",
            "HSBC" to "Merhaba",
            "Kuveyt Türk" to "Merhaba",
            "KUVEYTTURK" to "Merhaba",
            "Albaraka Türk" to "Merhaba",
            "Türkiye Finans" to "Merhaba",
            "TFKB" to "Merhaba",
            "Fibabanka" to "Merhaba",
            "Odeabank" to "Merhaba",
            "Şekerbank" to "Merhaba",
            "SEKERBANK" to "Merhaba",
            "Papara" to "Merhaba",
            "ininal" to "Merhaba",
            "Tosla" to "Merhaba",
            "Paycell" to "Merhaba",
            "iyzico" to "Merhaba",
            "Param" to "Merhaba",
            "Moka" to "Merhaba",
            "PayTR" to "Merhaba",
            "Sipay" to "Merhaba",
            "Hepsipay" to "Merhaba",
            "BtcTurk" to "Merhaba",
            "Binance" to "Merhaba",
            "Bilinmeyen Bank" to "Merhaba",
        ),
    )

    @Test fun finansMetniGondereneBakmadanGizlenir() = sina(
        Karar.GIZLI,
        listOf(
            "Bilinmeyen" to "****1234 nolu kartınızdan 1.250,75 TL harcama yapılmıştır.",
            "Bilinmeyen" to "5401 **** **** 1234 kart ile MIGROS isyerinden 245,90 TL",
            "Bilinmeyen" to "Kredi kartınız için ödeme hatırlatması",
            "Bilinmeyen" to "Ekstreniz hazır. Son ödeme tarihi 15/11",
            "Bilinmeyen" to "Asgari ödeme tutarınız 1.500,00 TL",
            "Bilinmeyen" to "Kullanılabilir limitiniz 25.000 TL",
            "Bilinmeyen" to "Hesabınıza 3.000,00 TL havale yapıldı",
            "Bilinmeyen" to "Hesabınızdan 150 TL EFT gönderildi",
            "Ahmet" to "IBAN: TR33 0006 1005 1978 6457 8413 26",
            "Ahmet" to "TR330006100519786457841326",
            "Ahmet" to "İBAN'ımı atıyorum",
            "Unknown" to "Available balance 150.00 USD",
            "Unknown" to "Debit card payment declined",
        ),
    )

    @Test fun siradanMesajlarGecer() = sina(
        Karar.ACIK,
        listOf(
            "Anne" to "Eve gelirken ekmek al",
            "Ahmet" to "Yarın saat 14:30'da buluşalım",
            "Ahmet" to "14:30",
            "Ahmet" to "Atatürk Cad. No 1234 Kadıköy adresindeyim",
            "Ahmet" to "2026 yılında görüşürüz",
            "Ahmet" to "Bugün 5000 adım attım",
            "Ahmet" to "Numaram 0532 123 45 67",
            "Ahmet" to "Toplantı 15.00'te, 3 kişi geliyoruz",
            "Ahmet" to "Rei, deploy diskort yap",
            "Deniz" to "Akşam 1000 kişilik salon tuttuk",
            "Ayşe Garip" to "Tamam",
            "Ingrid" to "Merhaba 1234",
            "Tebessüm Kafe" to "Masanız hazır",
            "Ahmet" to "Müşteri kodu 1234567890",
            "Ahmet" to "Kod 123",
            "Kargo" to "Barkod 12345 teslim edildi",
            "Kargo" to "Barcode 12345 delivered",
            "Ahmet" to "hotpot 1234 yedik",
            "Instagram" to "Hesabınıza yeni bir cihazdan giriş yapıldı",
            "Ahmet" to "Kartal'da 100 kişi vardı",
            "Ahmet" to "ekstrem sporlar",
            "Ahmet" to "Kahve 50 TL oldu",
        ),
    )

    @Test fun yeniKodSozcukleriElenir() = sina(
        Karar.ELE,
        listOf(
            "x" to "Your password is 482913",
            "x" to "One-time password: 482913",
            "x" to "one time 482913",
            "x" to "PIN: 4821",
            "x" to "SMS PIN'iniz 482913",
            "x" to "Use 482913 to verify your account",
            "x" to "Telefonunuzu 482913 ile doğrulayın",
            "x" to "482913 is your login token",
            "x" to "Şifren 4821",
            "x" to "2FA 482913",
        ),
    )

    @Test fun sozcukBasiVePinSiniriKorunur() = sina(
        Karar.ACIK,
        listOf(
            "Ahmet" to "pinterest 1234 kişi topladı",
            "Pınar" to "Pınar 1234 numarada",
            "Ahmet" to "spin 1234",
            "Ahmet" to "pin kodu istemiyorum",
            "Ahmet" to "A blok 1204 numara",
        ),
    )

    @Test fun zayifSozcukYalnizTamAltiHaneyleElenir() {
        sina(
            Karar.ELE,
            listOf(
                "x" to "Giriş için 482913 girin",
                "x" to "Giriş için 123 456 girin",
                "x" to "login 482913",
                "Login" to "482913 girin",
            ),
        )
        sina(
            Karar.ACIK,
            listOf(
                "Ahmet" to "Giriş kapısında 1500'de buluşalım",
                "Ahmet" to "login sayfası 2024'te değişti",
                "Ahmet" to "Giriş 0532 123 45 67 numarasından",
            ),
        )
    }

    @Test fun haneZinciriAyraclari() {
        sina(
            Karar.ELE,
            listOf(
                "x" to "Doğrulama kodunuz: 123.456",
                "x" to "Doğrulama kodunuz: 123 - 456",
                "x" to "Doğrulama kodunuz: 123–456",
                "x" to "Doğrulama kodunuz: 123—456",
                "x" to "Doğrulama kodunuz: 123\u2010456",
                "x" to "Doğrulama kodunuz: 123\u2011456",
                "Ahmet" to "123–456",
            ),
        )
        sina(Karar.ACIK, listOf("Ahmet" to "12.30", "Ahmet" to "14:30", "Ahmet" to "Kod 14:30"))
    }

    @Test fun harfRakamKodlariElenir() = sina(
        Karar.ELE,
        listOf(
            "x" to "Doğrulama kodunuz: A7K2Q9",
            "x" to "Giriş kodunuz: X4Y7",
            "x" to "Steam Guard code: F3K9P",
            "x" to "A7K2Q9 doğrulama kodunuzdur",
            "x" to "Sipariş kodun TY1234 kargoda",
            "x" to "Kod ${"a".repeat(28)} X4Y7",
        ),
    )

    @Test fun uzaktakiYaDaUygunsuzJetonGecer() = sina(
        Karar.ACIK,
        listOf(
            "Ahmet" to "Kod ${"a".repeat(32)} X4Y7",
            "Ahmet" to "Onay bekliyorum, ABCD yazdı",
            "Ahmet" to "A7K2Q9 yazıyor, ne demek",
            "Ahmet" to "Kod AB12CD34E geldi",
        ),
    )

    @Test fun herAlanAyriAyriDenetlenir() {
        sinaUclu(
            Karar.ELE,
            listOf(
                // Başlıkta kod, yedek yolda metin olarak gelir.
                Triple("Doğrulama kodunuz 482913", "Ayrıntı için dokunun", null),
                Triple("Doğrulama kodunuz: A7K2Q9", "Ayrıntı için dokunun", null),
                Triple("Giriş 482913", "Dokunun", null),
                // Sohbet adında kod.
                Triple("Ahmet", "Selam", "Kodunuz 482913"),
                Triple("Ahmet", "Selam", "Kodunuz: A7K2Q9"),
                // Sözcük sohbette, hane metinde.
                Triple("Ahmet", "Numaranız 5678", "OTP Servisi"),
                Triple("Ahmet", "482913", "Giriş"),
                // Ters çapraz: hane başlıkta, sözcük metinde. Yedek yolda kod gönderen alanıyla gider.
                Triple("G-482913", "Doğrulama kodunuz", null),
                Triple("482913", "Doğrulama kodunuz", null),
                Triple("4821", "Tek kullanımlık şifreniz", null),
                Triple("Ahmet", "Doğrulama kodunuz", "482913"),
                Triple("482913", "Giriş yapın", null),
                // Harf-rakam jetonu sözcükten ayrı alanda.
                Triple("Doğrulama", "A7K2Q9", null),
                Triple("A7K2Q9", "Doğrulama kodunuz", null),
                Triple("Ahmet", "A7K2Q9", "Doğrulama"),
                // Sözcük ve hane gönderen ile sohbet arasında bölünmüş.
                Triple("Doğrulama", "Merhaba", "482913"),
                Triple("482913", "Merhaba", "Doğrulama"),
                // Kısa gönderici koddan ayırt edilemez, kod sözcüğüyle elenir.
                Triple("4646", "Onay verdim", null),
            ),
        )
        sinaUclu(
            Karar.ACIK,
            listOf(
                // Numaranın haneleri metindeki sözcükle birleşmez.
                Triple("+90 532 123 45 67", "Kapı kodunu unuttum", null),
                Triple("05321234567", "Giriş kapısındayım", null),
                Triple("(0532) 123-45-67", "Doğrulama yapıldı", null),
                Triple("4646", "Paketiniz yenilendi", null),
                Triple("Ahmet", "Kapı kodunu unuttum", "+90 532 123 45 67"),
                Triple("482913", "Merhaba", "4646"),
                Triple("Kod Grubu", "Merhaba", "Doğrulama Ekibi"),
            ),
        )
    }

    @Test fun etiketliDegerElenir() = sina(
        Karar.ELE,
        listOf(
            "Bilinmeyen" to "Geçici şifreniz: Xk29fLq7vB",
            "Bilinmeyen" to "Yeni şifreniz: Ab3\$xY9!",
            "Bilinmeyen" to "Şifreniz: maviKedi",
            "Bilinmeyen" to "Doğrulama kodunuz: KPQXTM",
            "Steam" to "Your Steam Guard code is: BDKQR",
            "Steam" to "Your code is BDKQR",
            "Bilinmeyen" to "Doğrulama kodunuz: A1B-2C3",
            "Bilinmeyen" to "Doğrulama kodunuz: ABC-123",
            "Bilinmeyen" to "Kodunuz: A7K 2Q9",
            "Bilinmeyen" to "Doğrulama kodunuz aşağıdadır, bu kodu kimseyle paylaşmayın lütfen: A7K2Q9",
            "Bilinmeyen" to "Doğrulama linki: https://ornek.test/d?token=ab12cd34ef",
            "Bilinmeyen" to "Giriş: 4821",
        ),
    )

    @Test fun yeniKodBicimleriElenir() = sina(
        Karar.ELE,
        listOf(
            "Bilinmeyen" to "Use 482913 to log in",
            "Bilinmeyen" to "Use 482913 to sign in",
            "Bilinmeyen" to "Teyit numaranız 482913",
            "Bilinmeyen" to "Aktivasyon: 4821",
            "Bilinmeyen" to "Güvenlik numaranız: 4821",
            "Bilinmeyen" to "Tek seferlik numaranız 4821",
            "Bilinmeyen" to "Oturum açmak için 482913",
            "Bilinmeyen" to "MFA 482913",
            "Bilinmeyen" to "SMSKODU: 482913",
            "Bilinmeyen" to "SMSOTP 482913",
            "Bilinmeyen" to "GirisKodu: 4821",
            "Bilinmeyen" to "Pininiz 4821",
            "Bilinmeyen" to "Kart pinin 4821",
            "Bilinmeyen" to "Tu código es 482913",
            "Bilinmeyen" to "Ваш код 482913",
            "Bilinmeyen" to "Giriş için A7K2Q9 girin",
            "Bilinmeyen" to "Giriş için 4821 girin",
        ),
    )

    @Test fun hanelerHerAyraclaBirlesir() = sina(
        Karar.ELE,
        listOf(
            "Bilinmeyen" to "Doğrulama kodunuz: 482,913",
            "Bilinmeyen" to "Doğrulama kodunuz: 482/913",
            "Bilinmeyen" to "Doğrulama kodunuz: 482_913",
            "Bilinmeyen" to "Doğrulama kodunuz: 482·913",
            "Bilinmeyen" to "Doğrulama kodunuz: 482--913",
            "Bilinmeyen" to "Doğrulama kodunuz: 482‒913",
            "Bilinmeyen" to "Doğrulama kodunuz: 482―913",
            "Bilinmeyen" to "Doğrulama kodunuz: 482−913",
            "Bilinmeyen" to "Doğrulama kodunuz: 4\u00018\u00012\u00019",
        ),
    )

    @Test fun birlesikOnaySozcuguMaskelenmez() = sinaUclu(
        Karar.ELE,
        listOf(
            Triple("OnayKodu", "Numaranız 482913", null),
            Triple("Onaykodunuz", "Numaranız 482913", null),
            Triple("ONAYKODU", "A7K2Q9 girin", null),
            Triple("Onaylama", "Numaranız 482913", null),
            Triple("Ahmet", "Numaranız 482913", "OnayKodu"),
        ),
    )

    @Test fun zayifSozcukVirguldenEtkilenmez() = sinaUclu(
        Karar.ELE,
        listOf(
            Triple("Bilinmeyen", "Login 482 913, 60 sn", null),
            Triple("Bilinmeyen", "Giriş 482-913, 60 saniye geçerli", null),
            Triple("Bilinmeyen", "Giriş 482 913/2024", null),
            Triple("Bilinmeyen", "Login 48-29-13, 120 sn", null),
            Triple("Giris", "482 913, 60 sn geçerli", null),
            Triple("Ahmet", "482 913, 60 sn geçerli", "Login"),
        ),
    )

    @Test fun adlarEtiketSayilmaz() = sina(
        Karar.ACIK,
        listOf("Bilinmeyen" to "Pınar: geliyorum", "Bilinmeyen" to "Kodlama: bugün başladım"),
    )

    @Test fun satirSonuSozcukleriBirlestirmez() {
        assertEquals(Karar.ACIK, Filtre.degerlendir("Ahmet", "Bu ko\nd değil, 1500 kişi", null))
    }

    @Test fun kisiAdiSohbetiEtmez() = sinaUclu(
        Karar.ACIK,
        listOf(
            Triple("Onay Yılmaz", "2024 mezunuyum", null),
            Triple("Onay Yılmaz", "Numaram 0532 123 45 67", null),
            Triple("Onay Yılmaz", "Selam", "Mezunlar 2012"),
            Triple("Ahmet", "Selam", "Kodlama Kursu 2024"),
            Triple("Metin İnal", "Selam", null),
            Triple("Pari Bulut", "Selam", null),
            Triple("Ahmet", "Onay: bekliyorum", null),
            Triple("Ahmet", "Kod: yok", null),
        ),
    )

    @Test fun finansGondereniKodTasimaz() = sinaUclu(
        Karar.ELE,
        listOf(
            Triple("Akbank 482913", "Dokunun", null),
            Triple("Akbank A7K2Q9", "Dokunun", null),
        ),
    )

    @Test fun yeniTutarlarGizlenir() = sina(
        Karar.GIZLI,
        listOf(
            "Bilinmeyen" to "Hesabınıza 500 Euro yatırıldı",
            "Bilinmeyen" to "500 USDT yatırıldı",
            "Bilinmeyen" to "Hesaptan 5.000 TL gönderildi",
            "Bilinmeyen" to "Hesapta 5.000 TL kaldı",
            "Bilinmeyen" to "1.500 TL ödeyin",
        ),
    )

    @Test(timeout = 2000) fun maskeSelindeKilitlenmez() {
        for (c in listOf("x", "*", "•")) {
            for (n in listOf(400, 4000, 15_000)) Filtre.degerlendir("Ahmet", c.repeat(n), null)
        }
        for (d in listOf("xx ", "x.", "**-", "1a ", "a1:", "kod: ", "tl 1 ")) {
            Filtre.degerlendir("Ahmet", d.repeat(2500), null)
        }
    }

    @Test fun maskeBicimleriTutar() = sina(
        Karar.GIZLI,
        listOf(
            "Bilinmeyen" to "**** **** **** 1234 harcama",
            "Bilinmeyen" to "xxxx1234 işlem",
            "Bilinmeyen" to "**1234",
            "Bilinmeyen" to "4821 ****",
        ),
    )

    @Test fun tavaniAsanMesajElenir() {
        assertEquals(Karar.ELE, Filtre.degerlendir("Ahmet", "a ".repeat(9000), null))
    }

    @Test fun sohbetAdiBankaysaGizlenir() = sinaUclu(
        Karar.GIZLI,
        listOf(
            Triple("Ahmet", "Merhaba", "Akbank"),
            Triple("Ahmet", "Merhaba", "Garanti BBVA"),
            Triple("Ahmet", "Merhaba", "Hesabınızdan 5.000 TL çekildi"),
            Triple("Ahmet", "Merhaba", "Kredi kartı grubu"),
        ),
    )

    @Test fun gonderenAlaniSizdirmaz() {
        sinaUclu(
            Karar.ELE,
            listOf(
                Triple("Hesabınızdan 5.000 TL çekildi", "Ayrıntı için dokunun", null),
                Triple("Kredi kartı ekstreniz hazır", "Dokunun", null),
                Triple("IBAN: TR33 0006 1005 1978 6457 8413 26", "Dokunun", null),
                Triple("Akbank Onay", "Merhaba", null),
                Triple("Şifre Merkezi", "Kartınızdan 100 TL harcama", null),
                Triple("Şifre Merkezi", "Merhaba", "Akbank"),
            ),
        )
    }

    @Test fun cekimliOdemeGizlenir() = sina(
        Karar.GIZLI,
        listOf(
            "Bilinmeyen" to "150 TL ödendi",
            "Bilinmeyen" to "150 TL ödenmiştir",
            "Bilinmeyen" to "Faturanız 150 TL olarak ödenecek",
        ),
    )

    @Test fun birlesikAdlarSozcukSiniriniAsmaz() {
        sina(
            Karar.ACIK,
            listOf("Sercan Kolay" to "Tamam", "Mustafa Toslak" to "Tamam", "Kişi Payı" to "Tamam", "Ali Bitlow" to "Tamam"),
        )
        sina(
            Karar.GIZLI,
            listOf(
                "Nkolay" to "Merhaba",
                "N Kolay" to "Merhaba",
                "Tosla" to "Merhaba",
                "Bitlo" to "Merhaba",
                "Pokus" to "Merhaba",
                "Lidio" to "Merhaba",
            ),
        )
    }

    @Test fun hataOlursaMesajGonderilmez() {
        assertEquals(Karar.ELE, Filtre.korumali { throw IllegalStateException("deneme") })
        assertEquals(Karar.ELE, Filtre.korumali { throw StackOverflowError() })
    }

    @Test fun cokUzunMetinKuraldanKacmaz() {
        val dolgu = "a ".repeat(7_000)
        assertEquals(Karar.ELE, Filtre.degerlendir("x", dolgu + "kodunuz 482913", null))
        assertEquals(Karar.GIZLI, Filtre.degerlendir("x", dolgu + "kredi kartı", null))
        assertEquals(Karar.ELE, Filtre.degerlendir("x", "a ".repeat(50_000) + "kredi kartı", null))
    }

    @Test fun katlamaYerelAyaraBagliDegil() {
        val eski = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
            assertEquals("sifre", Filtre.katla("ŞİFRE"))
            assertEquals("sifre", Filtre.katla("ŞIFRE"))
            assertEquals("igcsou", Filtre.katla("İĞÇŞÖÜ"))
            assertEquals("igcsou", Filtre.katla("ığçşöü"))
        } finally {
            java.util.Locale.setDefault(eski)
        }
    }
}
