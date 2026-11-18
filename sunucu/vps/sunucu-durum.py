#!/usr/bin/env python3
"""Ana VPS'te root olarak çalışır (`rei-oku sunucu-durum`): konteynerlerin Node sürümü, bekleyen paketler, Docker,
yeniden başlatma, unattended-upgrades. Salt okunur, LLM yok; karşılaştırma deterministik.
İlk satır `ÖZET: sorun yok` ya da sorunların tek satır özetidir; rei-sabah.sh bu satırı okur.
"sorun yok" yalnız her adım gerçekten doğrulandıysa yazılır: okunamayan her şey özette "kontrol edilemedi" olur.
Konteyner çıktısı ve nodejs.org verisi güvenilmezdir: rapora yalnız regex'ten geçen sürüm metni girer, çıktı sınırlı okunur."""
import concurrent.futures, datetime, glob, json, os, re, subprocess, sys, threading

DIST = "https://nodejs.org/dist/index.json"
# Desteklenen ana hatlar ve destek bitişi (github.com/nodejs/Release schedule.json, 2026-11-01). Tabloda olmayan ya da
# bitişi geçmiş hat "destek dışı" sayılır: yeni hat eklenene kadar yanlış alarm verir, yanlış "güncel" demez.
HATLAR = {22: "2027-04-30", 24: "2028-04-30", 26: "2029-04-30"}
SURUM = re.compile(r"v([0-9]{1,4})\.([0-9]{1,4})\.([0-9]{1,6})")
AD = re.compile(r"[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}")
DOCKER = "/usr/bin/docker"
KUMA = "kuma"
# Ağ ve apt okuması root'la değil nobody ile. setpriv exec eder: calistir'ın süre sınırı asıl sürece ulaşır (runuser fork eder).
NOBODY = ["/usr/bin/setpriv", "--reuid=65534", "--regid=65534", "--clear-groups", "--no-new-privs"]
ORTAM = {"PATH": "/usr/sbin:/usr/bin:/sbin:/bin", "HOME": "/root", "LC_ALL": "C"}
YENIDEN = "/var/run/reboot-required"
# Liste yaşı damgadan değil depo dosyalarından: `apt-get update` geçici indirme hatasında da 0 döner, damga tazelenir.
# mtime depo tarafının tarihidir; bir tanesi yeni olması yeter (noble-updates günde birkaç kez yayımlanır).
APT_LISTE = "/var/lib/apt/lists/*_InRelease"
UU_LOG = "/var/log/unattended-upgrades/unattended-upgrades.log"
BAYAT_GUN = 3  # apt listesi ya da unattended-upgrades bundan eskiyse sorun


def calistir(argv, sure, sinir, hata=subprocess.DEVNULL):
    """(çıkış kodu, stdout). Çıktı en fazla `sinir` bayt okunur, süre dolunca süreç öldürülür; başarısızlık -1 ya da negatif.
    `hata=subprocess.STDOUT` stderr'i de okur (docker logs konteynerin stderr'ini oraya yazar)."""
    try:
        p = subprocess.Popen(argv, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=hata, env=ORTAM)
    except OSError:
        return -1, ""
    zaman = threading.Timer(sure, p.kill)
    zaman.start()
    with p.stdout:
        ham = p.stdout.read(sinir + 1)
    fazla = len(ham) > sinir
    if fazla:
        p.kill()
    rc = p.wait()
    zaman.cancel()
    return (-1 if fazla else rc), ham.decode("utf-8", "replace")


def konteynerler():
    """Çalışan konteyner adları; docker okunamazsa None. status=running süzgeci yok: yeniden başlayan ya da duraklatılmış
    konteyner listede kalsın, exec başarısız olup "kontrol edilemedi"ye düşsün."""
    rc, cikti = calistir([DOCKER, "ps", "--format", "{{.Names}}"], 8, 65536)
    return cikti.split() if rc == 0 else None


def node_surumu(ad):
    """Sürüm metni; konteynerde node yoksa None; okunamazsa "" (süre doldu, biçim bozuk, konteyner yeniden başlıyor).
    "Yok" sayılan tek durum 127 + "bulunamadı" iletisidir: 126 ve diğer exec hataları doğrulanmamış demektir, atlanmaz."""
    if not AD.fullmatch(ad):
        return ""
    # "node yok" iletisi docker'dan stdout'a uzun metin olarak gelir; sınır 127 çıkış kodunu yutmasın diye geniş.
    rc, cikti = calistir([DOCKER, "exec", ad, "node", "-v"], 8, 4096)
    if rc == 127 and "executable file not found" in cikti:
        return None
    s = cikti.strip()
    return s if rc == 0 and SURUM.fullmatch(s) else ""


def guvenlik_surumleri(veri):
    """index.json listesinden {ana hat: en son güvenlik sürümü (a, b, c)}. Biçimi tutmayan kayıt atlanır (rc/alpha dahil)."""
    son = {}
    for kayit in veri if isinstance(veri, list) else ():
        if not isinstance(kayit, dict) or kayit.get("security") is not True:
            continue
        s = kayit.get("version")
        m = SURUM.fullmatch(s) if isinstance(s, str) else None
        if m:
            t = tuple(map(int, m.groups()))
            son[t[0]] = max(son.get(t[0], t), t)
    return son


def dist_oku():
    """nodejs.org'dan güvenlik sürümleri; okunamazsa boş sözlük (çağıran "kontrol edilemedi" der). Yönlendirme izlenmez."""
    rc, cikti = calistir(NOBODY + ["/usr/bin/curl", "-q", "-fsS", "--proto", "=https", "--max-time", "15",
                                   "--max-filesize", "4000000", DIST], 18, 4000000)
    if rc != 0:
        return {}
    try:
        return guvenlik_surumleri(json.loads(cikti))
    except (ValueError, RecursionError):
        return {}


def degerlendir(s, guvenlik, bugun):
    """("guncel"|"eski"|"destek-disi"|"bilinmiyor", ek bilgi). Ölçüt: hattın en son güvenlik sürümünden geride olmak;
    güvenlik işaretsiz daha yeni sürüm (24.22.1 varken 24.22.2) ölçüt değildir."""
    m = SURUM.fullmatch(s) if s else None
    if not m:
        return "bilinmiyor", ""
    t = tuple(map(int, m.groups()))
    bitis = HATLAR.get(t[0])
    if bitis is None or bugun > bitis:
        return "destek-disi", ""
    son = guvenlik.get(t[0])
    if son is None:
        return "bilinmiyor", ""
    return ("eski", ".".join(map(str, son))) if t < son else ("guncel", "")


def apt_say(cikti):
    """`apt-get -s dist-upgrade` çıktısından (güvenlik, toplam); özet satırı yoksa ya da sayı tutmuyorsa None (boş ya da
    tanınmayan çıktı "0 bekleyen" sayılmasın). Yükseltme satırı `Inst ad [eski] (yeni köken [mimari])`."""
    guvenlik = toplam = 0
    for satir in cikti.splitlines():
        m = re.match(r"Inst \S+ \[.*\((.*?)\)", satir)
        if m:
            toplam += 1
            guvenlik += "-security" in m.group(1)
    ozet = re.search(r"^([0-9]+) upgraded, [0-9]+ newly installed", cikti, re.M)
    return (guvenlik, toplam) if ozet and int(ozet[1]) == toplam else None


def uu_son(metin):
    """Günlükteki son anlamlı çalışma: (zaman, sonuç) ya da None. Sonuç: "bekleyen yok" | "kuruldu" | "yarım kaldı" | "hata".
    Hiçbir şey yapmadan biten çalışma (elle kesilen --dry-run) atlanır; kurulum başlayıp bitmediyse "yarım kaldı"dır."""
    son = zaman = sonuc = None
    for satir in metin.splitlines():
        m = re.match(r"(\d{4}-\d\d-\d\d \d\d:\d\d):\d\d,\d+ (\w+) (.*)", satir)
        if not m:
            continue
        if m[3].startswith("Starting unattended upgrades script"):
            zaman, sonuc = m[1], None
        elif zaman is None or sonuc == "hata":
            pass
        elif m[2] == "ERROR":
            sonuc = "hata"
        elif m[3].startswith("All upgrades installed"):
            sonuc = "kuruldu"
        elif m[3].startswith("Packages that will be upgraded"):
            sonuc = "yarım kaldı"
        elif m[3].startswith("No packages found"):
            sonuc = "bekleyen yok"
        if zaman and sonuc:
            son = (zaman, sonuc)
    return son


def liste(ogeler):
    return ", ".join(ogeler[:4]) + (f" +{len(ogeler) - 4}" if len(ogeler) > 4 else "")


def kuma_hata_say():
    """Son 24 saatte Kuma'nın bildirim gönderememe sayısı; günlük okunamazsa None. Kuma başarılı gönderimi hiç yazmaz, hatayı yalnız burada bırakır:
    ops botu token'ı bozulursa alarmlar sessizce kaybolur, bunu Rei botunun sabah özeti haber verir."""
    rc, cikti = calistir([DOCKER, "logs", "--since", "24h", KUMA], 5, 30000000, subprocess.STDOUT)
    return cikti.count("Cannot send notification") if rc == 0 else None


def rapor(nodelar, guvenlik, apt, apt_gun, docker, yeniden, uu, simdi, kuma_hata=0):
    """Rapor satırları. nodelar: {ad: sürüm|""} ya da None; apt: (güvenlik, toplam) ya da None; apt_gun: liste yaşı (gün)
    ya da None; docker: sürüm ya da ""; uu: uu_son sonucu; kuma_hata: kuma_hata_say sonucu. Yan etkisiz; sorunlar ilk satırda toplanır."""
    bugun = simdi.strftime("%Y-%m-%d")
    sorunlar, govde = [], []

    hatlar = ", ".join(".".join(map(str, guvenlik[h])) for h in sorted(guvenlik) if h in HATLAR)
    govde.append(f"Node (en son güvenlik: {hatlar}):" if hatlar else "Node (nodejs.org okunamadı, karşılaştırma yapılamadı):")
    if not guvenlik:
        sorunlar.append("Node kontrol edilemedi (nodejs.org okunamadı)")
    if nodelar is None:
        govde.append("  konteynerler okunamadı")
        sorunlar.append("konteynerler okunamadı")
    elif not nodelar:
        govde.append("  Node içeren konteyner bulunamadı")
        sorunlar.append("Node içeren konteyner bulunamadı")
    else:
        eski, disi, bilinmez = [], [], []
        for ad in sorted(nodelar):
            s = nodelar[ad]
            durum, ek = degerlendir(s, guvenlik, bugun)
            if durum == "guncel":
                govde.append(f"  {ad}: {s} güncel")
            elif durum == "eski":
                eski.append(f"{ad} {s}")
                govde.append(f"  {ad}: {s} ESKİ (hattın en son güvenlik sürümü {ek})")
            elif durum == "destek-disi":
                disi.append(f"{ad} {s}")
                govde.append(f"  {ad}: {s} DESTEK DIŞI")
            else:
                bilinmez.append(ad)
                govde.append(f"  {ad}: {s or 'sürüm okunamadı'} kontrol edilemedi")
        if eski:
            sorunlar.append("Node eski: " + liste(eski))
        if disi:
            sorunlar.append("Node destek dışı: " + liste(disi))
        if bilinmez and guvenlik:
            sorunlar.append("Node kontrol edilemedi: " + liste(bilinmez))

    if apt is None:
        govde += ["Bekleyen güvenlik paketi: kontrol edilemedi", "Bekleyen toplam paket: kontrol edilemedi"]
        sorunlar.append("apt kontrol edilemedi")
    else:
        govde += [f"Bekleyen güvenlik paketi: {apt[0]}", f"Bekleyen toplam paket: {apt[1]}"]
        if apt[0]:
            sorunlar.append(f"{apt[0]} güvenlik paketi bekliyor")
    if apt_gun is None or apt_gun < 0:  # ileri tarihli dosya (saat kayması) taze sayılmaz
        govde.append("apt listesi: yaşı kontrol edilemedi")
        sorunlar.append("apt listesi yaşı kontrol edilemedi")
    else:
        govde.append(f"apt listesi: {apt_gun} gün önce yenilendi")
        if apt_gun >= BAYAT_GUN:
            sorunlar.append(f"apt listesi {apt_gun} gündür yenilenmiyor")

    govde.append(f"Docker: {docker or 'kontrol edilemedi'}")
    if not docker:
        sorunlar.append("Docker sürümü kontrol edilemedi")
    govde.append("Yeniden başlatma: " + ("GEREKİYOR" if yeniden else "işaret yok"))
    if yeniden:
        sorunlar.append("yeniden başlatma bekliyor")

    if uu is None:
        govde.append("unattended-upgrades: son çalışma bulunamadı")
        sorunlar.append("unattended-upgrades son çalışması bulunamadı")
    else:
        govde.append(f"unattended-upgrades: son çalışma {uu[0]}, sonuç: {uu[1]}")
        gun = (simdi - datetime.datetime.strptime(uu[0], "%Y-%m-%d %H:%M")).days
        if uu[1] in ("hata", "yarım kaldı"):
            sorunlar.append(f"unattended-upgrades: {uu[1]}")
        elif gun >= BAYAT_GUN:
            sorunlar.append(f"unattended-upgrades {gun} gündür çalışmamış")

    if kuma_hata is None:
        govde.append("Kuma bildirimleri: kontrol edilemedi")
        sorunlar.append("Kuma günlüğü kontrol edilemedi")
    else:
        govde.append(f"Kuma bildirim hatası (24 saat): {kuma_hata}")
        if kuma_hata:
            sorunlar.append(f"Kuma bildirim gönderemedi ({kuma_hata} kez, alarmlar gitmiyor olabilir)")

    return ["ÖZET: " + ("; ".join(sorunlar) if sorunlar else "sorun yok")] + govde


def main():
    simdi = datetime.datetime.now()
    adlar = konteynerler()
    nodelar = None
    if adlar is not None:
        with concurrent.futures.ThreadPoolExecutor(8) as havuz:
            surumler = list(havuz.map(node_surumu, adlar))
        nodelar = {a: s for a, s in zip(adlar, surumler) if s is not None}
    guvenlik = dist_oku()
    rc, cikti = calistir(NOBODY + ["/usr/bin/apt-get", "-s", "dist-upgrade"], 20, 4000000)
    apt = apt_say(cikti) if rc == 0 else None
    try:
        apt_gun = int((simdi.timestamp() - max(map(os.path.getmtime, glob.glob(APT_LISTE)))) // 86400)
    except (OSError, ValueError):
        apt_gun = None
    rc, cikti = calistir([DOCKER, "version", "--format", "{{.Server.Version}}"], 5, 64)
    docker = cikti.strip() if rc == 0 and re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", cikti.strip()) else ""
    try:
        with open(UU_LOG, encoding="utf-8", errors="replace") as f:
            uu = uu_son(f.read())
    except OSError:
        uu = None
    satirlar = rapor(nodelar, guvenlik, apt, apt_gun, docker, os.path.exists(YENIDEN), uu, simdi, kuma_hata_say())
    sys.stdout.buffer.write(("\n".join(satirlar) + "\n").encode("utf-8"))


if __name__ == "__main__":
    main()
