package net.ziroo.reisms

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import net.ziroo.reisms.cekirdek.Kimlik
import net.ziroo.reisms.cekirdek.Kuyruk
import net.ziroo.reisms.cekirdek.KuyrukDeposu
import net.ziroo.reisms.cekirdek.KuyrukKaydi

/** Uygulamaya özel kuyruk, süreç ölümünü ve yeniden açılışı geçer. `gorulen`de metin yok, yalnız anahtarlı özet (`id`) durur. */
class Veritabani(baglam: Context) : SQLiteOpenHelper(baglam.applicationContext, AD, null, SURUM), KuyrukDeposu {
    override fun onConfigure(db: SQLiteDatabase) {
        // Silinen mesajın metni dosyada kalmasın.
        db.rawQuery("PRAGMA secure_delete = ON", null).use { it.moveToFirst() }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE kuyruk (sira INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL UNIQUE, tur TEXT NOT NULL, govde BLOB NOT NULL, eklenme INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE gorulen (id TEXT PRIMARY KEY, zaman INTEGER NOT NULL) WITHOUT ROWID")
        db.execSQL("CREATE INDEX gorulen_zaman ON gorulen (zaman)")
        db.execSQL("CREATE TABLE sayac (ad TEXT PRIMARY KEY, deger INTEGER NOT NULL) WITHOUT ROWID")
        db.execSQL("CREATE TABLE ayar (ad TEXT PRIMARY KEY, deger BLOB NOT NULL) WITHOUT ROWID")
    }

    override fun onUpgrade(db: SQLiteDatabase, eski: Int, yeni: Int) = Unit

    override fun <T> islem(blok: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val sonuc = blok()
            db.setTransactionSuccessful()
            return sonuc
        } finally {
            db.endTransaction()
        }
    }

    override fun gorulduMu(id: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM gorulen WHERE id = ?", arrayOf(id)).use { it.moveToFirst() }

    override fun gorulduYaz(id: String, zaman: Long) {
        writableDatabase.execSQL("INSERT OR IGNORE INTO gorulen (id, zaman) VALUES (?, ?)", arrayOf(id, zaman))
    }

    override fun eskiGorulenleriSil(esik: Long) {
        writableDatabase.delete("gorulen", "zaman < ?", arrayOf(esik.toString()))
    }

    override fun kuyrugaYaz(kayit: KuyrukKaydi) {
        val d = ContentValues().apply {
            put("id", kayit.id)
            put("tur", kayit.tur)
            put("govde", kayit.govde)
            put("eklenme", kayit.eklenme)
        }
        writableDatabase.insertOrThrow("kuyruk", null, d)
    }

    override fun kuyrukAdedi(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM kuyruk", null).use { it.moveToFirst(); it.getInt(0) }

    override fun yoklamaVarMi(): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM kuyruk WHERE tur = ? LIMIT 1", arrayOf(Kuyruk.TUR_YOKLAMA)).use { it.moveToFirst() }

    override fun enEskiler(adet: Int): List<KuyrukKaydi> =
        readableDatabase.rawQuery("SELECT id, tur, govde, eklenme FROM kuyruk ORDER BY sira LIMIT ?", arrayOf(adet.toString())).use { c ->
            buildList { while (c.moveToNext()) add(KuyrukKaydi(c.getString(0), c.getString(1), c.getBlob(2), c.getLong(3))) }
        }

    override fun kuyruktanSil(id: String) {
        writableDatabase.delete("kuyruk", "id = ?", arrayOf(id))
    }

    override fun enEskiyiSil(adet: Int) {
        writableDatabase.execSQL("DELETE FROM kuyruk WHERE sira IN (SELECT sira FROM kuyruk ORDER BY sira LIMIT ?)", arrayOf(adet))
    }

    override fun eskiKuyruguSil(esik: Long): Int =
        writableDatabase.delete("kuyruk", "eklenme < ?", arrayOf(esik.toString()))

    override fun sayacArtir(ad: String, miktar: Long) {
        writableDatabase.execSQL(
            "INSERT INTO sayac (ad, deger) VALUES (?, ?) ON CONFLICT(ad) DO UPDATE SET deger = deger + excluded.deger",
            arrayOf(ad, miktar),
        )
    }

    override fun degerYaz(ad: String, deger: Long) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO sayac (ad, deger) VALUES (?, ?)", arrayOf(ad, deger))
    }

    override fun deger(ad: String): Long =
        readableDatabase.rawQuery("SELECT deger FROM sayac WHERE ad = ?", arrayOf(ad)).use { if (it.moveToFirst()) it.getLong(0) else 0 }

    /** `id` üreten anahtar telefonda doğar, telefonu terk etmez. Veri silinirse yenisi üretilir, görüldü kaydı da onunla gider. */
    fun ozetAnahtari(): ByteArray = islem {
        val var_olan = readableDatabase.rawQuery("SELECT deger FROM ayar WHERE ad = ?", arrayOf(OZET_ANAHTARI)).use {
            if (it.moveToFirst()) it.getBlob(0) else null
        }
        var_olan ?: Kimlik.rastgeleBayt(OZET_BAYT).also { yeni ->
            val d = ContentValues().apply {
                put("ad", OZET_ANAHTARI)
                put("deger", yeni)
            }
            writableDatabase.insertOrThrow("ayar", null, d)
        }
    }

    private companion object {
        const val AD = "rei-sms.db"
        const val SURUM = 1
        const val OZET_ANAHTARI = "ozet_anahtari"
        const val OZET_BAYT = 32
    }
}
