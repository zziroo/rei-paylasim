package net.ziroo.reisms.cekirdek

/**
 * Bildirimden gelen ham mesajı filtreden geçirip kuyruğa koyar.
 * `EXTRA_MESSAGES` sohbetin geçmişini de taşır: yeni mesajı görüldü kaydı ayırır, kayıt diskte olduğundan yeniden açılışı geçer.
 */
class MesajIsleyici(
    private val depo: KuyrukDeposu,
    private val kuyruk: Kuyruk,
    private val ozetAnahtari: ByteArray,
    private val saat: () -> Long,
) {
    /** Dizi eskiden yeniye sıralıdır. Döner: kuyruğa giren kayıt sayısı. */
    fun isle(mesajlar: List<HamMesaj>): Int {
        var eklenen = 0
        mesajlar.forEachIndexed { i, m -> if (isleBir(m, i == mesajlar.lastIndex)) eklenen++ }
        return eklenen
    }

    private fun isleBir(m: HamMesaj, enYeni: Boolean): Boolean {
        if (m.kendi) return false
        val metin = m.metin?.takeIf { it.isNotBlank() } ?: return false
        // Görüldü kaydı 30 günde silinir. Daha eski mesaj yeniden gelirse ikinci kez gitmesin.
        if (m.zaman <= 0 || m.zaman < saat() - Kuyruk.TEKRAR_SURESI) return false
        val gonderen = m.gonderen?.takeIf { it.isNotBlank() } ?: Govde.BILINMEYEN

        val sohbet = Kimlik.sohbetSade(m.sohbet)
        // Sade başlıkta sayı eki kırpılır, "(2 kod)" gibi sinyal gider. Ham başlıkta da tam ad eşleşmez. İkisine bakılır, sıkı olan geçer.
        val karar = minOf(Filtre.degerlendir(gonderen, metin, m.sohbet), Filtre.degerlendir(gonderen, metin, sohbet))
        if (karar == Karar.ELE) {
            // Kod için hiçbir kayıt açılmaz, sayaç yalnız adettir. Geçmişteki kod her bildirimde sayılmasın diye en yenide artar.
            if (enYeni) depo.sayacArtir(Sayaclar.ELENEN)
            return false
        }

        val id = Kimlik.mesajId(ozetAnahtari, m.paket, sohbet, gonderen, m.zaman, metin)
        val gizli = karar == Karar.GIZLI
        val govde = Govde.mesaj(id, m.zaman, m.uyg, gonderen, metin, gizli, sohbet)
        val yeni = kuyruk.ekleYeniyse(KuyrukKaydi(id, Kuyruk.TUR_MESAJ, govde, saat()))
        if (yeni && gizli) depo.sayacArtir(Sayaclar.GIZLENEN)
        return yeni
    }
}
