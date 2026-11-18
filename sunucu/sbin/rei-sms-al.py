#!/usr/bin/env python3
"""Telefondaki Rei SMS uygulamasının mesajlarını alır, günlük JSONL dosyasına ekler. Plan: docs/sms-plani.md.
İnternete açıktır: TLS 1.3, `POST /m`, 8 KB gövde, HMAC-SHA256 imza, 10 dakika zaman farkı. Kimliği doğrulanmayan istek boş 404 alır ya da yanıtsız kapanır, gövde günlüğe yazılmaz.
Sırlar systemd LoadCredential ile gelir: anahtar, tls.crt, tls.key. reisms kullanıcısıyla çalışır, yalnız /var/lib/rei-sms'e yazar; dizin setgid olduğundan dosyalar rei grubuna düşer, Rei okur, yazamaz.
Kurulu yer: /usr/local/sbin/rei-sms-al (root 755). Birim: rei-sms-al.service."""
import hashlib
import hmac
import json
import os
import re
import socket
import ssl
import sys
import threading
import time
from collections import OrderedDict
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DIZIN = os.environ.get("REI_SMS_DIZIN", "/var/lib/rei-sms")
GOVDE_TAVANI = 8192
BAGLANTI_BUTCESI = 12 * 1024
ZAMAN_FARKI = 600
EL_SIKISMA_SURESI = 5
SOKET_SURESI = 8
BAGLANTI_TAVANI = 15
ESZAMANLI = 8
DAKIKADA_YAZMA = 60
GUNLUK_BAYT = 20 * 1024 * 1024
HATIRLANAN_ID = 5000
UYGULAMALAR = {"sms", "whatsapp", "instagram", "discord", "outlook", "teams", "linkedin"}
MESAJ_ALANLARI = {"id", "tur", "zaman", "uyg", "gonderen", "metin", "gizli"}
YOKLAMA_ALANLARI = {"id", "tur", "zaman"}
ID_DESENI = re.compile(r"[0-9a-f]{32}")
ZAMAN_DESENI = re.compile(r"[0-9]{10}")
IMZA_DESENI = re.compile(r"[0-9a-f]{64}")


def imza_dogru(anahtar, zaman, govde, imza):
    if not ZAMAN_DESENI.fullmatch(zaman) or not IMZA_DESENI.fullmatch(imza):
        return False
    beklenen = hmac.new(anahtar, zaman.encode("ascii") + b"\n" + govde, hashlib.sha256).hexdigest()
    return hmac.compare_digest(beklenen, imza)


def zaman_yakin(zaman, simdi):
    return abs(int(zaman) - simdi) <= ZAMAN_FARKI


def metin_mi(m, tavan):
    return isinstance(m, str) and len(m) <= tavan


def kayit_gecerli(o):
    if not isinstance(o, dict) or o.get("tur") not in ("mesaj", "yoklama"):
        return False
    if not isinstance(o.get("id"), str) or not ID_DESENI.fullmatch(o["id"]):
        return False
    if isinstance(o.get("zaman"), bool) or not isinstance(o.get("zaman"), int) or o["zaman"] <= 0:
        return False
    if o["tur"] == "yoklama":
        return set(o) == YOKLAMA_ALANLARI
    alanlar = set(o)
    if alanlar != MESAJ_ALANLARI and alanlar != MESAJ_ALANLARI | {"sohbet"}:
        return False
    if not isinstance(o["uyg"], str) or o["uyg"] not in UYGULAMALAR or not isinstance(o["gizli"], bool):
        return False
    if not metin_mi(o["gonderen"], 120) or not o["gonderen"]:
        return False
    if "sohbet" in o and not metin_mi(o["sohbet"], 120):
        return False
    return o["metin"] is None if o["gizli"] else metin_mi(o["metin"], 4000)


class Depo:
    def __init__(self, dizin):
        self.dizin = dizin
        self.kilit = threading.Lock()
        self.gorulen = OrderedDict()
        self.yazmalar = []
        for ad in sorted(f for f in os.listdir(dizin) if f.endswith(".jsonl"))[-2:]:
            self._hatirla_dosyadan(os.path.join(dizin, ad))

    def _hatirla_dosyadan(self, yol):
        with open(yol, encoding="ascii", errors="replace") as f:
            for satir in f:
                try:
                    self._hatirla(json.loads(satir)["id"])
                except (ValueError, KeyError, TypeError):
                    continue

    def _hatirla(self, kimlik):
        self.gorulen[kimlik] = True
        while len(self.gorulen) > HATIRLANAN_ID:
            self.gorulen.popitem(last=False)

    def ekle(self, kayit, simdi):
        """True: yazıldı ya da zaten vardı. False: tavan doldu. OSError çağırana gider."""
        with self.kilit:
            if kayit["id"] in self.gorulen:
                return True
            self.yazmalar = [t for t in self.yazmalar if simdi - t < 60]
            if len(self.yazmalar) >= DAKIKADA_YAZMA:
                return False
            yol = os.path.join(self.dizin, time.strftime("%Y-%m-%d", time.gmtime(simdi)) + ".jsonl")
            satir = (json.dumps(dict(kayit, alindi=int(simdi))) + "\n").encode("ascii")
            fd = os.open(yol, os.O_WRONLY | os.O_APPEND | os.O_CREAT | os.O_NOFOLLOW | os.O_CLOEXEC, 0o640)
            try:
                onceki = os.fstat(fd).st_size
                if onceki + len(satir) > GUNLUK_BAYT:
                    return False
                if os.write(fd, satir) != len(satir):
                    os.ftruncate(fd, onceki)
                    raise OSError("yarım yazma")
            finally:
                os.close(fd)
            self.yazmalar.append(simdi)
            self._hatirla(kayit["id"])
            return True


class Sayac:
    """Başarısız istekleri dakikada en çok bir satırla bildirir."""

    def __init__(self):
        self.kilit = threading.Lock()
        self.adet = 0
        self.son = 0

    def artir(self, neden):
        simdi = time.time()
        with self.kilit:
            self.adet += 1
            if simdi - self.son >= 60:
                try:
                    print(f"reddedilen istek: {self.adet} (son neden: {neden})", file=sys.stderr, flush=True)
                except (OSError, ValueError):
                    pass
                self.adet = 0
                self.son = simdi


class SinirliOkuyucu:
    """Başlık ayrıştırıcısı küçük girdiyi yüzlerce MB nesneye büyütebilir: istek satırı, başlık ve gövde tek bağlantı bütçesinden düşer."""

    def __init__(self, okuyucu, butce):
        self.okuyucu = okuyucu
        self.kalan = butce

    def _dus(self, veri):
        self.kalan -= len(veri)
        return veri

    def readline(self, n=-1):
        if self.kalan <= 0:
            raise OSError("bütçe doldu")
        return self._dus(self.okuyucu.readline(self.kalan if n < 0 else min(n, self.kalan)))

    def read(self, n):
        if n > self.kalan:
            raise OSError("bütçe doldu")
        return self._dus(self.okuyucu.read(n))

    def close(self):
        self.okuyucu.close()


class Isleyici(BaseHTTPRequestHandler):
    timeout = SOKET_SURESI
    server_version = ""
    sys_version = ""

    def setup(self):
        super().setup()
        self.rfile = SinirliOkuyucu(self.rfile, BAGLANTI_BUTCESI)

    def log_message(self, *a):
        pass

    def yanit(self, kod):
        self.close_connection = True
        try:
            self.send_response_only(kod)
            self.send_header("Content-Length", "0")
            self.end_headers()
            self.wfile.flush()
        except OSError:
            pass

    def send_error(self, code, message=None, explain=None):
        self.server.sayac.artir("istek")
        self.yanit(404)

    def reddet(self):
        self.send_error(404)

    def do_POST(self):
        uzunluk = self.headers.get("Content-Length", "")
        if self.path != "/m" or "Transfer-Encoding" in self.headers or not re.fullmatch(r"[0-9]{1,5}", uzunluk):
            return self.reddet()
        if not 0 < int(uzunluk) <= GOVDE_TAVANI:
            return self.reddet()
        govde = self.rfile.read(int(uzunluk))
        zaman = self.headers.get("X-Rei-Zaman", "")
        simdi = time.time()
        if len(govde) != int(uzunluk) or not imza_dogru(self.server.anahtar, zaman, govde, self.headers.get("X-Rei-Imza", "")):
            return self.reddet()
        if not zaman_yakin(zaman, simdi):
            return self.reddet()
        try:
            kayit = json.loads(govde.decode("utf-8"))
        except (ValueError, RecursionError):
            return self.yanit(400)
        if not kayit_gecerli(kayit):
            return self.yanit(400)
        try:
            self.yanit(200 if self.server.depo.ekle(kayit, simdi) else 429)
        except OSError as e:
            print(f"yazılamadı: {type(e).__name__}", file=sys.stderr, flush=True)
            self.yanit(503)


class Sunucu(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 16

    def __init__(self, adres, baglam, anahtar, depo):
        super().__init__(adres, Isleyici)
        self.baglam = baglam
        self.anahtar = anahtar
        self.depo = depo
        self.sayac = Sayac()
        self.yuva = threading.BoundedSemaphore(ESZAMANLI)

    def handle_error(self, request, client_address):
        self.sayac.artir(sys.exc_info()[0].__name__)

    def process_request(self, request, client_address):
        if not self.yuva.acquire(blocking=False):
            self.sayac.artir("yuva dolu")
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, client_address)
        except BaseException:
            self.yuva.release()
            raise

    def process_request_thread(self, request, client_address):
        # TLS el sıkışması kabul döngüsünü bekletmesin diye iş parçacığında yapılır; damla damla yazan istemciyi tavan keser.
        tls = None
        kes = None
        try:
            request.settimeout(EL_SIKISMA_SURESI)
            tls = self.baglam.wrap_socket(request, server_side=True, do_handshake_on_connect=False)
            kes = threading.Timer(BAGLANTI_TAVANI, kapat, [tls])
            kes.daemon = True
            kes.start()
            tls.do_handshake()
            super().process_request_thread(tls, client_address)
        except (OSError, ValueError) as e:
            self.sayac.artir(type(e).__name__)
            self.shutdown_request(tls or request)
        finally:
            if kes:
                kes.cancel()
            self.yuva.release()


def kapat(baglanti):
    try:
        baglanti.shutdown(socket.SHUT_RDWR)
    except (OSError, ValueError):
        pass


def baglam_kur(sertifika, gizli):
    b = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    b.minimum_version = ssl.TLSVersion.TLSv1_3
    b.load_cert_chain(sertifika, gizli)
    return b


def main():
    kimlik = os.environ["CREDENTIALS_DIRECTORY"]
    with open(os.path.join(kimlik, "anahtar"), "rb") as f:
        anahtar = f.read().strip()
    if len(anahtar) < 32:
        sys.exit("anahtar kısa")
    baglam = baglam_kur(os.path.join(kimlik, "tls.crt"), os.path.join(kimlik, "tls.key"))
    port = int(os.environ["REI_SMS_PORT"])
    adres = os.environ.get("REI_SMS_ADRES", "0.0.0.0")
    Sunucu((adres, port), baglam, anahtar, Depo(DIZIN)).serve_forever()


if __name__ == "__main__":
    main()
