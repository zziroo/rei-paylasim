package net.ziroo.reisms.cekirdek

import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.Socket
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

/**
 * Tek hedef, yalnız TLS 1.3, tek güven kaynağı derlemede gömülen sertifika. Sistem kökleri kullanılmaz.
 * Ana makine doğrulayıcısına dokunulmaz: sertifikadaki IP SAN denetimi açık kalır. Yönlendirme izlenmez.
 */
class HttpsTasiyici(adres: String, port: Int, sertifikaPem: ByteArray) : Tasiyici {
    private val url = URL("https", adres, port, "/m")
    private val fabrika: SSLSocketFactory = Tls13Fabrika(sabitBaglam(sertifikaPem).socketFactory)

    override fun gonder(govde: ByteArray, zaman: String, imza: String): Int {
        val b = url.openConnection() as HttpsURLConnection
        try {
            b.sslSocketFactory = fabrika
            b.connectTimeout = BAGLANTI_SURESI
            b.readTimeout = OKUMA_SURESI
            b.requestMethod = "POST"
            b.doOutput = true
            b.instanceFollowRedirects = false
            b.useCaches = false
            b.setFixedLengthStreamingMode(govde.size)
            b.setRequestProperty("Content-Type", "application/json")
            b.setRequestProperty("Connection", "close")
            b.setRequestProperty("X-Rei-Zaman", zaman)
            b.setRequestProperty("X-Rei-Imza", imza)
            b.outputStream.use { it.write(govde) }
            return b.responseCode
        } finally {
            b.disconnect()
        }
    }

    private fun sabitBaglam(pem: ByteArray): SSLContext {
        val sertifika = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(pem))
        val depo = KeyStore.getInstance(KeyStore.getDefaultType())
        depo.load(null, null)
        depo.setCertificateEntry("rei", sertifika)
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(depo)
        return SSLContext.getInstance(TLS_13).apply { init(null, tmf.trustManagers, null) }
    }

    /** `SSLContext.getInstance("TLSv1.3")` eski sürümleri kapatmaz, sürüm soket üzerinde daraltılır. */
    private class Tls13Fabrika(private val ic: SSLSocketFactory) : SSLSocketFactory() {
        private fun daralt(s: Socket): Socket {
            (s as SSLSocket).enabledProtocols = arrayOf(TLS_13)
            return s
        }

        override fun getDefaultCipherSuites(): Array<String> = ic.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = ic.supportedCipherSuites
        override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean) = daralt(ic.createSocket(s, host, port, autoClose))
        override fun createSocket(host: String, port: Int) = daralt(ic.createSocket(host, port))
        override fun createSocket(host: String, port: Int, yerel: InetAddress, yerelPort: Int) = daralt(ic.createSocket(host, port, yerel, yerelPort))
        override fun createSocket(host: InetAddress, port: Int) = daralt(ic.createSocket(host, port))
        override fun createSocket(host: InetAddress, port: Int, yerel: InetAddress, yerelPort: Int) = daralt(ic.createSocket(host, port, yerel, yerelPort))
    }

    private companion object {
        const val TLS_13 = "TLSv1.3"
        const val BAGLANTI_SURESI = 10_000
        const val OKUMA_SURESI = 15_000
    }
}
