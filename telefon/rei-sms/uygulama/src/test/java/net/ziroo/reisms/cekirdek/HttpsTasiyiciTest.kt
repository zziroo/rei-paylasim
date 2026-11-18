package net.ziroo.reisms.cekirdek

import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.security.KeyStore
import java.util.Base64
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Taşıma kuralları yerel TLS sunucusuna karşı, JVM TLS yığınıyla sınanır. Telefon Conscrypt kullanır:
 * sürüm ve sertifika mantığı aynı kaynak koddan geçer, gerçek telefon sınaması canlı denemededir.
 * Anahtarlar her çalışmada keytool ile geçici dizinde üretilir, repoya girmez.
 */
class HttpsTasiyiciTest {
    @get:Rule val gecici = TemporaryFolder()

    private val parola = "deneme-parolasi".toCharArray()
    private val sunucular = ArrayList<SSLServerSocket>()
    private lateinit var dogru: File
    private lateinit var yanlisAd: File

    private class Gelen(val yontem: String, val yol: String, val zaman: String?, val imza: String?, val govde: ByteArray)

    @Volatile private var gelen: Gelen? = null

    @Before fun kur() {
        dogru = anahtarUret("dogru", "IP:127.0.0.1")
        yanlisAd = anahtarUret("yanlis", "IP:127.0.0.2")
    }

    @After fun kapat() {
        sunucular.forEach { it.close() }
    }

    private fun anahtarUret(ad: String, san: String): File {
        val f = gecici.newFolder().resolve("$ad.p12")
        val keytool = File(System.getProperty("java.home"), "bin/keytool").path
        val s = ProcessBuilder(
            keytool, "-genkeypair", "-keystore", f.path, "-storetype", "pkcs12", "-storepass", String(parola),
            "-alias", "a", "-keyalg", "EC", "-groupname", "secp256r1", "-validity", "2", "-dname", "CN=Deneme", "-ext", "SAN=$san",
        ).redirectErrorStream(true).start()
        s.inputStream.readBytes()
        assertEquals(0, s.waitFor())
        return f
    }

    private fun pem(depo: File): ByteArray {
        val ks = KeyStore.getInstance("PKCS12")
        depo.inputStream().use { ks.load(it, parola) }
        val d = Base64.getMimeEncoder().encodeToString(ks.getCertificate("a").encoded)
        return "-----BEGIN CERTIFICATE-----\n$d\n-----END CERTIFICATE-----\n".toByteArray()
    }

    private fun sunucu(depo: File, surum: String, kod: Int = 200): Int {
        val ks = KeyStore.getInstance("PKCS12")
        depo.inputStream().use { ks.load(it, parola) }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, parola)
        val bag = SSLContext.getInstance("TLS")
        bag.init(kmf.keyManagers, null, null)
        val s = bag.serverSocketFactory.createServerSocket(0, 8, InetAddress.getByName("127.0.0.1")) as SSLServerSocket
        s.enabledProtocols = arrayOf(surum)
        sunucular.add(s)
        thread(isDaemon = true) {
            try {
                while (true) {
                    val b = s.accept() as SSLSocket
                    try {
                        karsila(b, kod)
                    } catch (_: IOException) {
                        // El sıkışması reddi beklenen sonuçtur.
                    } finally {
                        b.close()
                    }
                }
            } catch (_: IOException) {
                // Soket kapandı.
            }
        }
        return s.localPort
    }

    private fun karsila(b: SSLSocket, kod: Int) {
        b.soTimeout = 5000
        val giris = b.inputStream
        val bas = StringBuilder()
        while (!bas.endsWith("\r\n\r\n")) {
            val c = giris.read()
            if (c < 0) return
            bas.append(c.toChar())
        }
        val satirlar = bas.toString().trim().split("\r\n")
        val istek = satirlar.first().split(' ')
        val basliklar = satirlar.drop(1).associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
        val alinan = giris.readNBytes(basliklar.getValue("content-length").toInt())
        gelen = Gelen(istek[0], istek[1], basliklar["x-rei-zaman"], basliklar["x-rei-imza"], alinan)
        b.outputStream.write("HTTP/1.1 $kod X\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        b.outputStream.flush()
    }

    private val govde = "{}".toByteArray()

    @Test fun sabitSertifikaylaGider() {
        val port = sunucu(dogru, "TLSv1.3")
        val kod = HttpsTasiyici("127.0.0.1", port, pem(dogru)).gonder(govde, "1762070400", "ab")
        assertEquals(200, kod)
        val g = gelen!!
        assertEquals("POST", g.yontem)
        assertEquals("/m", g.yol)
        assertEquals("1762070400", g.zaman)
        assertEquals("ab", g.imza)
        assertArrayEquals(govde, g.govde)
    }

    @Test fun sunucuKoduAynenDoner() {
        val port = sunucu(dogru, "TLSv1.3", kod = 404)
        assertEquals(404, HttpsTasiyici("127.0.0.1", port, pem(dogru)).gonder(govde, "1", "ab"))
    }

    @Test fun baskaSertifikaReddedilir() {
        val port = sunucu(dogru, "TLSv1.3")
        val t = HttpsTasiyici("127.0.0.1", port, pem(yanlisAd))
        assertThrows(IOException::class.java) { t.gonder(govde, "1", "ab") }
        assertEquals(null, gelen)
    }

    @Test fun ipSanDenetimiAcik() {
        // Sertifika güvenilir ama 127.0.0.2 için üretilmiş.
        val port = sunucu(yanlisAd, "TLSv1.3")
        val t = HttpsTasiyici("127.0.0.1", port, pem(yanlisAd))
        assertThrows(IOException::class.java) { t.gonder(govde, "1", "ab") }
        assertEquals(null, gelen)
    }

    @Test fun tls12Reddedilir() {
        val port = sunucu(dogru, "TLSv1.2")
        val t = HttpsTasiyici("127.0.0.1", port, pem(dogru))
        assertThrows(IOException::class.java) { t.gonder(govde, "1", "ab") }
        assertTrue(gelen == null)
    }
}
