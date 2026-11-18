package net.ziroo.reisms

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** İki iş: tekil gönderim (ağ koşullu, üstel geri çekilmeli) ve 6 saatlik yoklama. */
object Zamanlayici {
    private const val GONDERIM = "gonderim"
    private const val YOKLAMA = "yoklama"
    private const val GERI_CEKILME_SANIYE = 30L
    private const val YOKLAMA_SAATI = 6L

    private val agli = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /**
     * Çalışan iş kuyruğu boş görüp bitmek üzereyken yeni mesaj gelirse KEEP onu 6 saat bekletirdi.
     * Bu yüzden çalışanın ardına tek bir iş eklenir, zaten bekleyen varsa zincir uzatılmaz.
     * Engelleyici çağrı içerir: ana iş parçacığından çağrılmaz.
     */
    @Synchronized
    fun tetikle(c: Context) {
        val wm = WorkManager.getInstance(c)
        val bekleyen = wm.getWorkInfosForUniqueWork(GONDERIM).get()
            .count { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
        if (bekleyen > 0) return
        val istek = temel().setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
        wm.enqueueUniqueWork(GONDERIM, ExistingWorkPolicy.APPEND_OR_REPLACE, istek)
    }

    /** Art arda hatadan sonra: WorkManager'ın geri çekilmesi 5 saate çıkmasın, bağlantı dönünce mesajlar geç kalmasın. */
    fun sonra(c: Context, dakika: Long) {
        val istek = temel().setInitialDelay(dakika, TimeUnit.MINUTES).build()
        WorkManager.getInstance(c).enqueueUniqueWork(GONDERIM, ExistingWorkPolicy.APPEND_OR_REPLACE, istek)
    }

    fun periyodikKur(c: Context) {
        val istek = PeriodicWorkRequestBuilder<YoklamaIsi>(YOKLAMA_SAATI, TimeUnit.HOURS).build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(YOKLAMA, ExistingPeriodicWorkPolicy.KEEP, istek)
    }

    private fun temel(): OneTimeWorkRequest.Builder =
        OneTimeWorkRequestBuilder<GonderimIsi>()
            .setConstraints(agli)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, GERI_CEKILME_SANIYE, TimeUnit.SECONDS)
}
