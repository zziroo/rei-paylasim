package net.ziroo.reisms

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import net.ziroo.reisms.cekirdek.DonguSonucu
import net.ziroo.reisms.cekirdek.Sayaclar
import net.ziroo.reisms.cekirdek.bagBekle

/** Kuyruğu boşaltır. Tekil iş, yalnız `Zamanlayici` kurar. */
class GonderimIsi(baglam: Context, parametre: WorkerParameters) : Worker(baglam, parametre) {
    override fun doWork(): Result {
        val c = applicationContext
        val sonuc = try {
            Bilesenler.dongu(c).calistir { isStopped }
        } catch (t: Exception) {
            Log.w(Bilesenler.ETIKET, "gönderim: ${t.javaClass.simpleName}")
            DonguSonucu.TEKRAR
        }
        return when (sonuc) {
            DonguSonucu.BITTI -> Result.success()
            DonguSonucu.DEVAM -> {
                if (!isStopped) Zamanlayici.tetikle(c)
                Result.success()
            }
            DonguSonucu.TEKRAR -> if (runAttemptCount < UST_DENEME) {
                Result.retry()
            } else {
                Zamanlayici.sonra(c, BEKLEME_DAKIKA)
                Result.success()
            }
        }
    }

    private companion object {
        const val UST_DENEME = 5
        const val BEKLEME_DAKIKA = 30L
    }
}

/**
 * 6 saatte bir: bağı yeniler, yoklama kaydını kuyruğa koyar. Ağ koşulu yok: bağ denetimi çevrimdışı da çalışmalı, gönderim işi ağı bekler.
 * Sunucu yoklamayı temas sayar: izin ya da bağ yoksa yazılmaz, sessizlik uyarısı çalışsın. Bir sonraki periyotta yeniden dener.
 * Gönderim yine tetiklenir: kuyrukta bekleyen mesajların tek güvencesi bazen bu iştir.
 */
class YoklamaIsi(baglam: Context, parametre: WorkerParameters) : Worker(baglam, parametre) {
    override fun doWork(): Result {
        val c = applicationContext
        Bilesenler.bagiYenile(c)
        return try {
            val kuyruk = Bilesenler.kuyruk(c)
            kuyruk.bakim()
            val canli = Bilesenler.erisimVar(c) && bagBekle({ Dinleyici.bagli }, { isStopped }) { Thread.sleep(it) }
            if (canli) kuyruk.yoklamaEkle() else Bilesenler.depo(c).sayacArtir(Sayaclar.ATLANAN_YOKLAMA)
            Zamanlayici.tetikle(c)
            Result.success()
        } catch (t: Exception) {
            Log.w(Bilesenler.ETIKET, "yoklama: ${t.javaClass.simpleName}")
            Result.retry()
        }
    }
}
