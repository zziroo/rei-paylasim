import com.android.build.api.dsl.VariantDimension
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Sırlar repoda yok, ~/.config/rei/sms/ altından yalnız release derlemesi okur. Değerler build/ altındaki BuildConfig'e girer, ekrana basılmaz.
val sirDizini = File(System.getProperty("user.home"), ".config/rei/sms")

fun sirDosyasi(ad: String): File {
    val f = File(sirDizini, ad)
    if (!f.isFile) throw GradleException("Eksik sır dosyası: ~/.config/rei/sms/$ad")
    return f
}

fun envOku(f: File): Map<String, String> =
    f.readLines().map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
        .associate { it.substringBefore("=").trim() to it.substringAfter("=").trim().trim('"', '\'') }

fun base64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)

val SIR_DOSYA_IZNI = PosixFilePermissions.fromString("rw-------")

// Anahtar yoksa üretilir. Parola komut satırına değil, keytool'a ortam değişkeniyle gider. Tek dosya eksikse üretilmez: var olan imza ezilmesin.
fun imzaHazirla(): Map<String, String> {
    val depo = File(sirDizini, "imza.jks")
    val env = File(sirDizini, "imza.env")
    if (depo.isFile != env.isFile) throw GradleException("imza.jks ve imza.env birlikte bulunmalı: ~/.config/rei/sms/")
    if (!depo.isFile) {
        val parola = ByteArray(32).also { SecureRandom().nextBytes(it) }
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val gecici = File(sirDizini, "imza.jks.gecici").also { it.delete() }
        val keytool = File(System.getProperty("java.home"), "bin/keytool").path
        val surec = ProcessBuilder(
            keytool, "-genkeypair", "-keystore", gecici.path, "-storetype", "pkcs12",
            "-storepass:env", "REI_IMZA_PAROLA", "-keypass:env", "REI_IMZA_PAROLA",
            "-alias", "rei-sms", "-keyalg", "RSA", "-keysize", "4096", "-validity", "36500",
            "-dname", "CN=Rei SMS",
        ).redirectErrorStream(true).apply { environment()["REI_IMZA_PAROLA"] = parola }.start()
        surec.inputStream.readBytes()
        if (surec.waitFor() != 0 || !gecici.isFile) {
            gecici.delete()
            throw GradleException("keytool imza anahtarını üretemedi")
        }
        Files.setPosixFilePermissions(gecici.toPath(), SIR_DOSYA_IZNI)
        val geciciEnv = File(sirDizini, "imza.env.gecici")
        Files.deleteIfExists(geciciEnv.toPath())
        Files.createFile(geciciEnv.toPath(), PosixFilePermissions.asFileAttribute(SIR_DOSYA_IZNI))
        geciciEnv.writeText("REI_IMZA_PAROLA=$parola\nREI_IMZA_TAKMA_AD=rei-sms\n")
        Files.move(geciciEnv.toPath(), env.toPath(), StandardCopyOption.ATOMIC_MOVE)
        Files.move(gecici.toPath(), depo.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }
    return envOku(env)
}

val BOSLUK = setOf<Byte>(9, 10, 11, 12, 13, 32)

class Hedef(val anahtar: ByteArray, val sertifika: ByteArray, val adres: String, val port: Int)

fun hedefOku(): Hedef {
    val anahtar = sirDosyasi("anahtar").readBytes().dropWhile { it in BOSLUK }.dropLastWhile { it in BOSLUK }.toByteArray()
    if (anahtar.size < 32) throw GradleException("~/.config/rei/sms/anahtar kısa: sunucu en az 32 bayt ister")

    val sertifika = sirDosyasi("tls.crt").readBytes()
    if (!String(sertifika).contains("BEGIN CERTIFICATE")) throw GradleException("~/.config/rei/sms/tls.crt PEM sertifika değil")

    val sunucu = envOku(sirDosyasi("sunucu.env"))
    val adres = sunucu["REI_SMS_ADRES"].orEmpty()
    val port = sunucu["REI_SMS_PORT"]?.toIntOrNull() ?: 0
    if (!Regex("[0-9A-Za-z.-]+").matches(adres)) throw GradleException("sunucu.env: REI_SMS_ADRES yok ya da geçersiz")
    if (port !in 1..65535) throw GradleException("sunucu.env: REI_SMS_PORT yok ya da geçersiz")
    return Hedef(anahtar, sertifika, adres, port)
}

// Debug ve sırsız yapılandırma bu sabitle derlenir. Boş sertifikayla gönderim başlamaz, adres ayrılmış `.invalid` alanındadır.
val SAHTE_HEDEF = Hedef(ByteArray(32), ByteArray(0), "sahte.invalid", 443)

fun VariantDimension.hedefYaz(h: Hedef) {
    buildConfigField("String", "ANAHTAR_B64", "\"${base64(h.anahtar)}\"")
    buildConfigField("String", "SERTIFIKA_B64", "\"${base64(h.sertifika)}\"")
    buildConfigField("String", "ADRES", "\"${h.adres}\"")
    buildConfigField("int", "PORT", h.port.toString())
}

// Sırlar yalnız adında release geçen görev istenince okunur. `build` ya da `aR` gibi yollar aşağıdaki kapıya takılır.
val surumIsteniyor = gradle.startParameter.taskNames.any { it.substringAfterLast(":").contains("release", ignoreCase = true) }
val surumHedefi = if (surumIsteniyor) hedefOku() else null
val imza = if (surumIsteniyor) imzaHazirla() else null

gradle.taskGraph.whenReady {
    val surumGorevi = allTasks.firstOrNull { Regex("(assemble|bundle|package)Release(Bundle)?").matches(it.name) }
    if (!surumIsteniyor && surumGorevi != null) {
        throw GradleException("${surumGorevi.name} sahte hedefle derlenmez: görevi tam adıyla çağır, örnek assembleRelease")
    }
}

android {
    namespace = "net.ziroo.reisms"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.ziroo.reisms"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (imza != null) {
            create("rei") {
                storeFile = File(sirDizini, "imza.jks")
                storeType = "pkcs12"
                storePassword = imza.getValue("REI_IMZA_PAROLA")
                keyAlias = imza.getValue("REI_IMZA_TAKMA_AD")
                keyPassword = imza.getValue("REI_IMZA_PAROLA")
            }
        }
    }

    buildTypes {
        debug {
            hedefYaz(SAHTE_HEDEF)
        }
        release {
            isMinifyEnabled = false
            hedefYaz(surumHedefi ?: SAHTE_HEDEF)
            if (imza != null) signingConfig = signingConfigs.getByName("rei")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "META-INF/**"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.work:work-runtime:2.10.5")
    testImplementation("junit:junit:4.13.2")
}
