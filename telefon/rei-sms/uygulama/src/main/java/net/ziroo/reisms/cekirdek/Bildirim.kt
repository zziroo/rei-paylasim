package net.ziroo.reisms.cekirdek

/** Paket beyaz listesi. Burada olmayan paketin bildirimine hiç bakılmaz. Banka, ödeme, Telegram ve kimlik doğrulama girmez. */
object Paketler {
    private val LISTE = mapOf(
        "com.google.android.apps.messaging" to "sms",
        "com.whatsapp" to "whatsapp",
        "com.instagram.android" to "instagram",
        "com.discord" to "discord",
        "com.microsoft.office.outlook" to "outlook",
        "com.microsoft.teams" to "teams",
        "com.linkedin.android" to "linkedin",
    )

    fun uygulama(paket: String): String? = LISTE[paket]
}

enum class Yol { ASIL, YEDEK, YOK }

/** Bir bildirimin hangi yoldan okunacağının tek karar noktası. */
object Yollar {
    private val YALNIZ_ASIL = setOf("sms", "whatsapp")

    /**
     * Asıl yol `EXTRA_MESSAGES`: kategoriye bakılmaz (Aşama 0: WhatsApp mesajında `category` boş,
     * Google Mesajlar'ın `category=service` satırlarında dizi yok). Öteki uygulamaların biçimi ölçülmedi,
     * onlar için başlık + metin yedeği var.
     */
    fun sec(uyg: String, ozet: Boolean, mesajVar: Boolean, surekli: Boolean): Yol = when {
        ozet -> Yol.YOK
        mesajVar -> Yol.ASIL
        uyg in YALNIZ_ASIL || surekli -> Yol.YOK
        else -> Yol.YEDEK
    }
}

class Kisi(val ad: String?, val anahtar: String?)

/** Kendi gönderdiğimiz mesaj: gönderen yok ya da cihaz sahibi. Anahtar varsa ad eşitliğine bakılmaz, aynı adlı kişi elenmesin. */
fun kendiMi(gonderen: Kisi?, sahip: Kisi?, sahipAdi: String?): Boolean {
    if (gonderen == null) return true
    if (gonderen.anahtar != null && sahip?.anahtar != null) return gonderen.anahtar == sahip.anahtar
    val ad = gonderen.ad ?: return false
    return ad == sahip?.ad || ad == sahipAdi
}

class HamMesaj(
    val paket: String,
    val uyg: String,
    val sohbet: String?,
    val gonderen: String?,
    val kendi: Boolean,
    val zaman: Long,
    val metin: String?,
)
