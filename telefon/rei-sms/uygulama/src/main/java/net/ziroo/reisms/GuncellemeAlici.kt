package net.ziroo.reisms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Güncellemeden sonra bildirim bağı kopar, Aşama 0'da ölçüldü. */
class GuncellemeAlici : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Bilesenler.bagiYenile(c)
        Zamanlayici.periyodikKur(c)
    }
}
