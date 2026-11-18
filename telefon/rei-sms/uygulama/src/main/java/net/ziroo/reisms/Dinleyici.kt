package net.ziroo.reisms

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.Executors
import net.ziroo.reisms.cekirdek.Paketler

/** Bildirim dinleyicisi. Beyaz listede olmayan pakete bakılmaz. Mesaj metni ve gönderen günlüğe yazılmaz. */
class Dinleyici : NotificationListenerService() {
    override fun onListenerConnected() {
        bagli = true
        Zamanlayici.periyodikKur(this)
        // Bağ kopukken gelip duran bildirimler. Görüldü kaydı eskisini eler.
        try {
            activeNotifications?.forEach(::isle)
        } catch (t: RuntimeException) {
            Log.w(Bilesenler.ETIKET, "etkin bildirimler: ${t.javaClass.simpleName}")
        }
    }

    override fun onListenerDisconnected() {
        bagli = false
    }

    override fun onDestroy() {
        bagli = false
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        isle(sbn)
    }

    private fun isle(sbn: StatusBarNotification) {
        val uyg = Paketler.uygulama(sbn.packageName) ?: return
        val c = applicationContext
        isci.execute {
            try {
                val hamlar = BildirimOkuyucu.oku(sbn, uyg)
                if (hamlar.isNotEmpty() && Bilesenler.isleyici(c).isle(hamlar) > 0) Zamanlayici.tetikle(c)
            } catch (t: Throwable) {
                Log.w(Bilesenler.ETIKET, "işlenemedi: ${t.javaClass.simpleName}")
            }
        }
    }

    companion object {
        @Volatile var bagli = false
            private set

        // Tek iş parçacığı: bildirim sırası korunur, ana iş parçacığı veritabanı beklemez.
        private val isci = Executors.newSingleThreadExecutor()
    }
}
