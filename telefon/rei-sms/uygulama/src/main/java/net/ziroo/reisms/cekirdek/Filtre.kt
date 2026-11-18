package net.ziroo.reisms.cekirdek

import java.text.Normalizer

enum class Karar { ELE, GIZLI, ACIK }

/**
 * Hassas içerik kuralı, plan 5. bölüm. Android'e bağlı değildir, her uygulamaya uygulanır.
 * Sıra: önce kod, sonra banka. Bankadan gelen kod da tamamen elenir. Hata olursa mesaj gönderilmez.
 * Gönderen, metin ve sohbet ayrı ayrı denetlenir. Düz birleştirilmez: numara gönderen ile metindeki "kod" sözcüğü kod sayılmamalı.
 * Yanlış pozitif bedeli az: elenen mesaj telefonda bildirim olarak kalır, kaçan kod ise sunucuya gider.
 */
object Filtre {
    private const val EN_AZ_HANE = 4
    private const val EN_COK_HANE = 8
    private const val ZAYIF_HANE = 6
    private const val JETON_UZAKLIGI = 30
    private const val EN_AZ_NUMARA_HANE = 9

    // Regex'ler doğrusal kalsa da süre sınırı yoktur: tavanı aşan mesaj elenir, kırpılmaz, kırpılan kuyrukta kod kalabilir.
    private const val GIRDI_TAVANI = 16_000

    // Tek başına hane mesajında yalnız boşluk ve tire ayraç sayılır: `12.30`, `12/30`, `12,30` saat, tarih ya da tutardır.
    private const val TIRELER = "-‐‑‒–—―−"

    // Hane zincirinde boşluğa ek olarak kabul edilen ayraçlar, yalnız kod sözcüğü varken. `:` bilerek yok, `Kod 14:30` geçer.
    private const val AYRACLAR = "$TIRELER.,/_·"

    // Zayıf sözcükte toplam tam 6 hane aranır: `,` ve `/` zincire girerse "Login 482 913, 60 sn" toplamı bozulur.
    private const val DAR_AYRACLAR = "$TIRELER."

    private const val SINIR = "(?<![a-z])"

    // Sözcük başında aranır: "barkod", "barcode", "hotpot" tetiklemesin. "sifren" ayrıca yazılmaz, aşağıdaki "sifre" yakalar.
    private val KOD_SOZCUK_BASI = listOf(
        "kod(?!lama)", "otp", "code", "codigo", "onay", "password", "verify", "dogrula", "token", "2fa", "mfa(?![a-z])",
        "one-time", "one time", "tek seferlik", "teyit", "aktivasyon", "guvenlik no", "guvenlik numara",
        "log in", "sign in", "sign-in", "signin", "oturum ac", "giris icin", "pin(?:iniz|in|i)?(?![a-z])",
    )

    // Başka sözcüğün içinde görülmez, birleşik yazımı da yakalar.
    private val KOD_HER_YERDE = listOf(
        "sifre", "dogrulama", "tek kullanimlik", "parola", "verification", "passcode", "smskod", "smsotp", "giriskod", "код",
    )

    private val KOD_GUCLU = Regex(SINIR + "(?:" + KOD_SOZCUK_BASI.joinToString("|") + ")|" + KOD_HER_YERDE.joinToString("|"))

    // Olağan sözcükler: yalnız tam 6 haneyle kod sayılır, "giriş kapısında 1500'de" geçer.
    private val KOD_ZAYIF = Regex(SINIR + "(?:giris|login)")

    // Gönderen ve sohbette ad olabilir ("Onay Yılmaz"): başka alandaki haneyle çapraz eşleşmez.
    private val AD_OLABILEN = Regex(SINIR + "onay(?![a-z])")

    // `A7K2Q9` gibi kod adayı. Harf ve rakam denetimi eşleşmeden sonra yapılır.
    private val JETON = Regex("""(?<![a-z0-9])[a-z0-9]{4,8}(?![a-z0-9])""")

    // İki noktadan sonra gelen değer: harfli şifre, `Şifreniz: maviKedi`, `Kod: ABC-123`. `onay` yok, `Onay: bekliyorum` sohbettir.
    private const val DEGER_SOZCUGU =
        "(?:sifre[a-z]*|parola[a-z]*|password|passcode|kod(?!lama)[a-z]*|code|otp|token|pin(?:iniz|in|i)?|giris|login|dogrulama(?: [a-z]+)?)"
    private val ETIKETLI_DEGER = Regex(SINIR + DEGER_SOZCUGU + """(?: is)? ?[:=] ?\S{4,}|""" + SINIR + """code is \S{4,}""")
    private val BOLUNMUS_DEGER = Regex(SINIR + DEGER_SOZCUGU + """ ?[:=] ?([a-z0-9]{2,4} [a-z0-9]{2,4})(?![a-z0-9])""")
    private val IKI_NOKTA_JETONU = Regex("""[:=] ?([a-z0-9]{4,12})(?![a-z0-9])""")

    private val NUMARA = Regex("""\+?[0-9 ().-]+""")

    private val FINANS_KESIN = Regex(
        SINIR + "(?:" + listOf(
            "iban", "ekstre(?!m)", "kredi kart", "banka kart", "sanal kart", "kart limit", "kullanilabilir limit",
            "kalan limit", "limitiniz", "limit asim", "son odeme tarihi", "asgari odeme", "hesap hareket",
            "hesap ozet", "hesap bakiye", "bakiyeniz", "credit card", "debit card", "card ending",
            "available balance", "account balance",
        ).joinToString("|") + ")",
    )

    private val FINANS_ZAYIF = Regex(
        SINIR + "(?:" + listOf(
            "harcama", "odeme", "oden", "bakiye", "havale", "eft(?![a-z])", "fast(?![a-z])", "swift(?![a-z])",
            "para transfer", "bank transfer", "wire transfer", "transaction", "hesa[bp]", "kart(?:i|in|iniz[a-z]*|tan|la|a)?(?![a-z])",
            "limit", "tahsilat", "cekim", "cekildi", "yatirildi", "taksit", "kredi", "islem", "tutar", "atm(?![a-z])",
            "ode(?:di|yin|yiniz)",
        ).joinToString("|") + ")",
    )

    private val TUTAR = Regex(
        """\d[\d.,]{0,20} ?(?:tl|try|trl|usdt?|eur|euro|avro|gbp|sterlin|lira[a-z]{0,4}|dolar[a-z]{0,4}|₺|\$|€|£)(?![a-z])""" +
            """|(?:tl|try|usd|eur|₺|\$|€|£) ?\d""",
    )
    private val TR_IBAN = Regex("""(?<![a-z])tr ?\d{2}(?: ?\d){18,28}""")

    // Sahiplenici nicelik ve geriye bakış şart: yoksa `xxxx…` dizisi dört gruba her biçimde bölünür, 400 karakterde filtre dakikalarca kilitlenir.
    private val KART_MASKESI = Regex("""(?<![*x•])(?:[*x•]{2,}+[ .-]?){1,4}\d{4}(?!\d)|\d{4,6}[ .-]?[*x•]{2,}""")

    /**
     * Gizli kayıtta gönderen yine gider: başlığı gönderen sayan yedek yolda finans metni, kod sözcüğü ya da kod bu alandan sızar, o mesaj elenir.
     * Sohbet gizli kayıtta yazılmaz, `Govde.mesaj` düşürür.
     */
    fun degerlendir(gonderen: String, metin: String, sohbet: String?): Karar = korumali {
        if (gonderen.length + metin.length + sohbet.orEmpty().length > GIRDI_TAVANI) return@korumali Karar.ELE
        val g = katla(gonderen)
        val m = katla(metin)
        val s = katla(sohbet.orEmpty())
        val finansal = GonderenListesi.finansal(g) || GonderenListesi.finansal(s) || finansMetni(m) || finansMetni(s)
        when {
            kodMu(g, m, s) || finansMetni(g) -> Karar.ELE
            finansal -> if (KOD_GUCLU.containsMatchIn(g) || kodTasir(numaraDisi(g))) Karar.ELE else Karar.GIZLI
            else -> Karar.ACIK
        }
    }

    /** Kuralın kendisi hata verirse mesaj elenir, gönderilmez. */
    internal fun korumali(blok: () -> Karar): Karar = try {
        blok()
    } catch (_: Throwable) {
        Karar.ELE
    }

    /**
     * Büyük-küçük harf ve Türkçe harf farkını siler. Yerel ayara dayanmaz: `İ`, `I`, `ı`, `i` hepsi `i` olur.
     * Görünmez karakterleri atar, boşlukları teke indirir, Unicode rakamlarını ASCII'ye çevirir.
     */
    fun katla(s: String): String {
        val ayrik = Normalizer.normalize(s, Normalizer.Form.NFKD)
        val b = StringBuilder(ayrik.length)
        var i = 0
        while (i < ayrik.length) {
            val k = ayrik.codePointAt(i)
            i += Character.charCount(k)
            when {
                Character.isWhitespace(k) || Character.isSpaceChar(k) -> if (b.isNotEmpty() && b.last() != ' ') b.append(' ')
                atilirMi(k) -> Unit
                Character.isDigit(k) -> b.append('0' + Character.digit(k, 10))
                k == 'ı'.code -> b.append('i')
                else -> b.appendCodePoint(Character.toLowerCase(k))
            }
        }
        return b.toString().trimEnd()
    }

    private fun atilirMi(k: Int): Boolean = when (Character.getType(k).toByte()) {
        Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK, Character.FORMAT, Character.CONTROL -> true
        else -> false
    }

    private fun kodMu(g: String, m: String, s: String): Boolean {
        val gHane = numaraDisi(g)
        val sHane = numaraDisi(s)
        val gSozcuk = AD_OLABILEN.replace(g, " ")
        val sSozcuk = AD_OLABILEN.replace(s, " ")
        return yalnizHane(m) || alanKodMu(g) || alanKodMu(m) || alanKodMu(s) ||
            caprazKodMu(gSozcuk, m) || caprazKodMu(sSozcuk, m) || caprazKodMu(m, gHane) || caprazKodMu(m, sHane) ||
            caprazKodMu(gSozcuk, sHane) || caprazKodMu(sSozcuk, gHane)
    }

    /** Sözcük ve değer aynı alanda. Numara olan gönderende sözcük yoktur, haneleri kod sayılmaz. */
    private fun alanKodMu(k: String): Boolean =
        (KOD_GUCLU.containsMatchIn(k) && (haneGrubuVar(k) || jetonVar(k))) || (KOD_ZAYIF.containsMatchIn(k) && altiHaneVar(k)) ||
            etiketliDegerVar(k)

    /** Sözcük bir alanda, haneler ya da jeton ötekinde. Başlığı gönderen sayan yedek yolda kod `gonderen`le gidebilir. */
    private fun caprazKodMu(sozcuk: String, hane: String): Boolean =
        (KOD_GUCLU.containsMatchIn(sozcuk) && kodTasir(hane)) || (KOD_ZAYIF.containsMatchIn(sozcuk) && altiHaneVar(hane))

    private fun kodTasir(k: String): Boolean = haneGrubuVar(k) || jetonlar(k).any()

    /** `Şifreniz: maviKedi`, `Kod: ABC-123`, `token=…`, `Kodunuz: A7K 2Q9`. Sözcük uzaksa iki noktadan sonraki harf-rakam jetonu da sayılır. */
    private fun etiketliDegerVar(k: String): Boolean =
        ETIKETLI_DEGER.containsMatchIn(k) ||
            BOLUNMUS_DEGER.findAll(k).any { d -> d.groupValues[1].any { it.isAsciiHane() } } ||
            (KOD_GUCLU.containsMatchIn(k) && IKI_NOKTA_JETONU.findAll(k).any { j -> karisikMi(j.groupValues[1]) })

    /** Telefon numarası (9+ hane) kod taşımaz, boş alan döner. 4-8 haneli kısa gönderici koddan ayırt edilemez, kod sayılır. */
    private fun numaraDisi(k: String): String =
        if (NUMARA.matches(k) && k.count { it.isAsciiHane() } >= EN_AZ_NUMARA_HANE) "" else k

    /** Harf ve rakam karışık 4-8 karakterlik jetonlar. */
    private fun jetonlar(k: String): Sequence<MatchResult> = JETON.findAll(k).filter { karisikMi(it.value) }

    private fun karisikMi(j: String): Boolean = j.any { it.isAsciiHane() } && j.any { !it.isAsciiHane() }

    /** Güçlü kod sözcüğüne en çok 30 karakter uzakta jeton. */
    private fun jetonVar(k: String): Boolean {
        val sozcukler = KOD_GUCLU.findAll(k).map { it.range }.toList()
        return jetonlar(k).any { j ->
            sozcukler.any { s -> j.range.first - s.last - 1 <= JETON_UZAKLIGI && s.first - j.range.last - 1 <= JETON_UZAKLIGI }
        }
    }

    /** Mesajın tamamı 4-8 hane mi? `14:30` ve `12.30` gibi saatler ayraç yüzünden sayılmaz. */
    private fun yalnizHane(k: String): Boolean {
        val govde = k.trim(' ', '.', ',', '!')
        if (govde.isEmpty() || !govde.first().isAsciiHane() || !govde.last().isAsciiHane()) return false
        if (govde.any { !it.isAsciiHane() && it != ' ' && it !in TIRELER }) return false
        return govde.count { it.isAsciiHane() } in EN_AZ_HANE..EN_COK_HANE
    }

    /**
     * Tek parça 4-8 hane ya da ayraçla bölünmüş `123 456`, `12-34-56`, `123.456`.
     * Bitişik sayı dizisi bütün olarak değil, ardışık parçalar olarak sınanır: "123456 120 saniye" 9 hane diye kaçmasın.
     */
    private fun haneGrubuVar(k: String): Boolean =
        haneZincirleri(k).any { z -> z.any { it in EN_AZ_HANE..EN_COK_HANE } || zincirKodMu(z) }

    /** Tam 6 hane: tek parça ya da zincirin tamamı. Telefon numarasının parçaları sayılmaz. */
    private fun altiHaneVar(k: String): Boolean =
        haneZincirleri(k, DAR_AYRACLAR).any { z -> ZAYIF_HANE in z || z.sum() == ZAYIF_HANE }

    /** Ayraçla bağlı sayıların uzunlukları, zincir başına bir liste. */
    private fun haneZincirleri(k: String, ayraclar: String = AYRACLAR): List<List<Int>> {
        val zincirler = ArrayList<List<Int>>()
        var zincir = ArrayList<Int>()
        var i = 0
        while (i < k.length) {
            if (!k[i].isAsciiHane()) {
                i++
                continue
            }
            val bas = i
            while (i < k.length && k[i].isAsciiHane()) i++
            zincir.add(i - bas)
            val sonraki = ayracSonu(k, i, ayraclar)
            if (sonraki > i) {
                i = sonraki
            } else {
                zincirler.add(zincir)
                zincir = ArrayList()
            }
        }
        return zincirler
    }

    /** `i`deki boşluk, ayraç ya da ` - ` bir sonraki haneye bağlanıyorsa o hanenin yeri, bağlanmıyorsa `i`. */
    private fun ayracSonu(k: String, i: Int, ayraclar: String): Int {
        var j = i
        if (j < k.length && k[j] == ' ') j++
        if (j < k.length && k[j] in ayraclar) {
            while (j < k.length && k[j] in ayraclar) j++
            if (j < k.length && k[j] == ' ') j++
        }
        return if (j < k.length && k[j].isAsciiHane()) j else i
    }

    private fun zincirKodMu(parcalar: List<Int>): Boolean {
        for (bas in parcalar.indices) {
            var toplam = parcalar[bas]
            for (son in bas + 1 until parcalar.size) {
                toplam += parcalar[son]
                if (toplam in EN_AZ_HANE..EN_COK_HANE) return true
                if (toplam > EN_COK_HANE) break
            }
        }
        return false
    }

    private fun finansMetni(k: String): Boolean =
        FINANS_KESIN.containsMatchIn(k) || TR_IBAN.containsMatchIn(k) || KART_MASKESI.containsMatchIn(k) ||
            (TUTAR.containsMatchIn(k) && FINANS_ZAYIF.containsMatchIn(k))

    private fun Char.isAsciiHane() = this in '0'..'9'
}
