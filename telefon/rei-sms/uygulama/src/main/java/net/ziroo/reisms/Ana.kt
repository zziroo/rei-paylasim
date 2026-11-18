package net.ziroo.reisms

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import net.ziroo.reisms.cekirdek.Sayaclar

/** Tek ekran: durum ve iki düğme. Mesaj metni, gönderen, adres ve port burada görünmez. */
class Ana : Activity() {
    private lateinit var durum: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        // Güncelleme ya da otomatik başlatma reddinden sonra bağı yeniler.
        Bilesenler.bagiYenile(this)
        Zamanlayici.periyodikKur(this)
        setContentView(arayuz())
    }

    override fun onResume() {
        super.onResume()
        tazele()
    }

    private fun arayuz(): ScrollView {
        val bosluk = (BOSLUK_DP * resources.displayMetrics.density).toInt()
        durum = TextView(this).apply { textSize = YAZI_SP }
        val erisim = Button(this).apply {
            setText(R.string.dugme_erisim)
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        val baglan = Button(this).apply {
            setText(R.string.dugme_baglan)
            setOnClickListener {
                Bilesenler.bagiYenile(context)
                postDelayed(::tazele, TAZELEME_MS)
            }
        }
        val not = TextView(this).apply {
            setText(R.string.not_hyperos)
            setPadding(0, bosluk, 0, 0)
        }
        val sutun = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(bosluk, bosluk, bosluk, bosluk)
            addView(durum)
            addView(erisim)
            addView(baglan)
            addView(not)
        }
        return ScrollView(this).apply {
            addView(sutun)
            // Hedef SDK 36'da içerik sistem çubuklarının altına uzanır.
            setOnApplyWindowInsetsListener { v, kenar ->
                val i = kenar.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(i.left, i.top, i.right, i.bottom)
                kenar
            }
        }
    }

    private fun tazele() {
        val d = Bilesenler.depo(this)
        val erisimVar = Bilesenler.erisimVar(this)
        val sonBasari = d.deger(Sayaclar.SON_BASARI)
        val sonYanit = d.deger(Sayaclar.SON_YANIT)
        durum.text = listOf(
            getString(R.string.durum_erisim, getString(if (erisimVar) R.string.`var` else R.string.yok)),
            getString(R.string.durum_dinleyici, getString(if (Dinleyici.bagli) R.string.bagli else R.string.bagli_degil)),
            getString(R.string.durum_kuyruk, d.kuyrukAdedi()),
            getString(
                R.string.durum_son_basari,
                if (sonBasari > 0) DateFormat.format(TARIH_BICIMI, sonBasari).toString() else getString(R.string.hic),
            ),
            getString(
                R.string.durum_son_yanit,
                when (sonYanit) {
                    0L -> getString(R.string.hic)
                    Sayaclar.AG_HATASI -> getString(R.string.yanit_ag)
                    else -> sonYanit.toString()
                },
            ),
            getString(R.string.durum_dusen, d.deger(Sayaclar.DUSEN_RET), d.deger(Sayaclar.DUSEN_TAVAN)),
            getString(R.string.durum_elenen, d.deger(Sayaclar.ELENEN)),
            getString(R.string.durum_gizlenen, d.deger(Sayaclar.GIZLENEN)),
            getString(R.string.durum_atlanan, d.deger(Sayaclar.ATLANAN_YOKLAMA)),
        ).joinToString("\n")
    }

    private companion object {
        const val BOSLUK_DP = 16
        const val YAZI_SP = 16f
        const val TAZELEME_MS = 1500L
        const val TARIH_BICIMI = "dd.MM.yyyy HH:mm"
    }
}
