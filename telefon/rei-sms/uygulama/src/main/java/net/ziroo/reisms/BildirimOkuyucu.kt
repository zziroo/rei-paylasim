package net.ziroo.reisms

import android.app.Notification
import android.app.Person
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import net.ziroo.reisms.cekirdek.HamMesaj
import net.ziroo.reisms.cekirdek.Kisi
import net.ziroo.reisms.cekirdek.Yol
import net.ziroo.reisms.cekirdek.Yollar
import net.ziroo.reisms.cekirdek.kendiMi

/** Bildirimi Android'siz çekirdeğin anladığı ham mesajlara çevirir. Yol kararı `Yollar.sec`'te. */
object BildirimOkuyucu {
    fun oku(sbn: StatusBarNotification, uyg: String): List<HamMesaj> {
        val n = sbn.notification
        val e = n.extras ?: return emptyList()
        val ozet = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val surekli = sbn.isOngoing || (n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        @Suppress("DEPRECATION")
        val dizi: Array<Parcelable>? = e.getParcelableArray(Notification.EXTRA_MESSAGES)
        return when (Yollar.sec(uyg, ozet, !dizi.isNullOrEmpty(), surekli)) {
            Yol.ASIL -> asil(sbn, uyg, e, dizi.orEmpty())
            Yol.YEDEK -> yedek(sbn, uyg, e)
            Yol.YOK -> emptyList()
        }
    }

    private fun asil(sbn: StatusBarNotification, uyg: String, e: Bundle, dizi: Array<out Parcelable>): List<HamMesaj> {
        val sohbet = e.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        val sahip = kisi(e.kisiOku(Notification.EXTRA_MESSAGING_PERSON))
        val sahipAdi = e.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME)?.toString()
        return dizi.filterIsInstance<Bundle>().map { b ->
            val ad = b.getCharSequence("sender")?.toString()
            val kisi = b.kisiOku("sender_person")
            val gonderen = when {
                kisi != null -> Kisi(kisi.name?.toString() ?: ad, kisi.key)
                ad != null -> Kisi(ad, null)
                else -> null
            }
            HamMesaj(
                paket = sbn.packageName,
                uyg = uyg,
                sohbet = sohbet,
                gonderen = gonderen?.ad,
                kendi = kendiMi(gonderen, sahip, sahipAdi),
                zaman = b.getLong("time").takeIf { it > 0 } ?: bildirimZamani(sbn),
                metin = b.getCharSequence("text")?.toString(),
            )
        }
    }

    /** Biçimi ölçülmemiş uygulamalar için başlık + metin. Yalnız burada. */
    private fun yedek(sbn: StatusBarNotification, uyg: String, e: Bundle): List<HamMesaj> {
        val baslik = e.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val uzun = e.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        val metin = uzun ?: e.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        return listOf(HamMesaj(sbn.packageName, uyg, null, baslik, false, bildirimZamani(sbn), metin))
    }

    private fun bildirimZamani(sbn: StatusBarNotification): Long =
        sbn.notification.`when`.takeIf { it > 0 } ?: sbn.postTime

    private fun kisi(p: Person?): Kisi? = p?.let { Kisi(it.name?.toString(), it.key) }

    @Suppress("DEPRECATION")
    private fun Bundle.kisiOku(ad: String): Person? = getParcelable(ad) as? Person
}
