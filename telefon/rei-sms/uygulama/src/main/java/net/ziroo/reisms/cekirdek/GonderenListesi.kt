package net.ziroo.reisms.cekirdek

/**
 * Türkiye'deki banka, ödeme ve kripto gönderenleri. Girdi `Filtre.katla`dan geçmiş metindir.
 * Metin bu listeden geçen mesajda sunucuya gitmez, yalnız gönderen ve saat gider.
 */
internal object GonderenListesi {
    private val SOZCUK = Regex("[a-z0-9]+")

    // Boşluk ve noktalama atılmış gönderenin içinde aranır: "GARANTIBBVA", "Yapı Kredi", "Is Bankasi".
    // "bank" ile biten adlar burada yok, sonEk kuralı onları yakalar.
    private val BITISIK = listOf(
        // Bankalar ve katılım bankaları
        "garanti", "akbank", "isbank", "isbankasi", "yapikredi", "ziraat", "vakifkatilim", "emlakkatilim",
        "turkiyefinans", "kuveytturk", "albaraka", "fibabanka", "odeabank", "sekerbank", "hayatfinans",
        "dunyakatilim", "getirfinans", "qnbfinans", "cardfinans", "enpara", "cepteteb", "juzdan",
        "bankkart", "worldcard",
        // Ödeme ve e-para kuruluşları
        "papara", "paycell", "iyzico", "hepsipay", "masterpass",
        "bkmexpress", "paypal", "payoneer", "westernunion", "moneygram", "vodafonepay", "turkcellpay",
        "paycore", "payguru", "paynet", "birlesikodeme", "ozanelektronik",
        "tompay", "pazaramapay", "trendyolpay", "moneypay", "aktifbank", "paybull", "paratika", "esnekpos",
        "parampos", "revolut",
        // Kripto ve borsa
        "binance", "btcturk", "bitexen", "icrypex",
    )

    // Gönderenin ayrı bir sözcüğü olarak aranır. Kısa olduğundan bitişik aranamaz: "ing" -> "ingiliz", "nkolay" -> "Sercan Kolay".
    // Yan yana iki sözcük de sınanır: "N Kolay".
    private val SOZCUK_ESIT = setOf(
        "teb", "ing", "qnb", "hsbc", "ykb", "icbc", "tfkb", "bkm", "citi", "citibank", "abank", "burgan",
        "okx", "nkolay", "tosla", "paytr", "sipay", "lidio", "pokus", "bitlo", "ininal", "paribu",
    )

    // Yalnız gönderenin tamamıysa sayılır: "Param", "Moka" başka bir kişi ya da işletme adının parçası olabilir.
    private val TAM_AD = setOf(
        "param", "moka", "ozan", "paraf", "bonus", "axess", "wings", "maximum", "world", "fiba", "odea",
        "wise", "ria", "midas", "vakif", "halk",
    )

    fun finansal(katlanmis: String): Boolean {
        val sozcukler = SOZCUK.findAll(katlanmis).map { it.value }.toList()
        if (sozcukler.isEmpty()) return false
        val bitisik = sozcukler.joinToString("")
        return BITISIK.any { it in bitisik } ||
            sozcukler.any { it in SOZCUK_ESIT || sonEk(it) } ||
            sozcukler.zipWithNext { a, b -> a + b }.any { it in SOZCUK_ESIT } ||
            bitisik in TAM_AD
    }

    private fun sonEk(sozcuk: String) = sozcuk.endsWith("bank") || sozcuk.endsWith("bankasi")
}
