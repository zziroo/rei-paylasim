package net.ziroo.reisms.cekirdek

/** Testler için SQLite'ın bellek karşılığı. */
class BellekDepo : KuyrukDeposu {
    val kuyruk = ArrayList<KuyrukKaydi>()
    val gorulen = LinkedHashMap<String, Long>()
    val sayac = HashMap<String, Long>()

    override fun <T> islem(blok: () -> T): T = blok()
    override fun gorulduMu(id: String) = id in gorulen
    override fun gorulduYaz(id: String, zaman: Long) {
        gorulen[id] = zaman
    }
    override fun eskiGorulenleriSil(esik: Long) {
        gorulen.entries.removeIf { it.value < esik }
    }
    override fun kuyrugaYaz(kayit: KuyrukKaydi) {
        kuyruk.add(kayit)
    }
    override fun kuyrukAdedi() = kuyruk.size
    override fun yoklamaVarMi() = kuyruk.any { it.tur == Kuyruk.TUR_YOKLAMA }
    override fun enEskiler(adet: Int) = kuyruk.take(adet)
    override fun kuyruktanSil(id: String) {
        kuyruk.removeIf { it.id == id }
    }
    override fun enEskiyiSil(adet: Int) {
        repeat(adet) { kuyruk.removeAt(0) }
    }
    override fun eskiKuyruguSil(esik: Long): Int {
        val once = kuyruk.size
        kuyruk.removeIf { it.eklenme < esik }
        return once - kuyruk.size
    }
    override fun sayacArtir(ad: String, miktar: Long) {
        sayac[ad] = (sayac[ad] ?: 0) + miktar
    }
    override fun degerYaz(ad: String, deger: Long) {
        sayac[ad] = deger
    }
    override fun deger(ad: String) = sayac[ad] ?: 0
}
