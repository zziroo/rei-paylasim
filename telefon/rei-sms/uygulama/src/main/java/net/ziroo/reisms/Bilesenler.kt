package net.ziroo.reisms

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.util.Log
import java.util.Base64
import net.ziroo.reisms.cekirdek.GonderimDongusu
import net.ziroo.reisms.cekirdek.HttpsTasiyici
import net.ziroo.reisms.cekirdek.Kuyruk
import net.ziroo.reisms.cekirdek.MesajIsleyici

/** Süreç başına tek depo. Hedef ve sırlar yalnız burada `BuildConfig`'ten okunur. */
object Bilesenler {
    const val ETIKET = "ReiSms"

    private val saat: () -> Long = System::currentTimeMillis

    @Volatile private var db: Veritabani? = null

    fun depo(c: Context): Veritabani =
        db ?: synchronized(this) { db ?: Veritabani(c).also { db = it } }

    fun kuyruk(c: Context) = Kuyruk(depo(c), saat)

    fun isleyici(c: Context): MesajIsleyici {
        val d = depo(c)
        return MesajIsleyici(d, Kuyruk(d, saat), d.ozetAnahtari(), saat)
    }

    fun dongu(c: Context): GonderimDongusu {
        val d = depo(c)
        val cozucu = Base64.getDecoder()
        val tasiyici = HttpsTasiyici(BuildConfig.ADRES, BuildConfig.PORT, cozucu.decode(BuildConfig.SERTIFIKA_B64))
        return GonderimDongusu(d, Kuyruk(d, saat), tasiyici, cozucu.decode(BuildConfig.ANAHTAR_B64), saat) { Thread.sleep(it) }
    }

    fun erisimVar(c: Context): Boolean =
        c.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(ComponentName(c, Dinleyici::class.java))

    /** Aşama 0: güncellemeden sonra bağ kopuyor, bu çağrı geri getiriyor. Erişim yoksa etkisizdir. */
    fun bagiYenile(c: Context) {
        try {
            NotificationListenerService.requestRebind(ComponentName(c, Dinleyici::class.java))
        } catch (t: RuntimeException) {
            Log.w(ETIKET, "requestRebind: ${t.javaClass.simpleName}")
        }
    }
}
