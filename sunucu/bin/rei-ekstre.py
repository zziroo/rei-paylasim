#!/home/rei/whisper/bin/python
"""Rei: kredi kartı ekstresi (PDF) → yapılandırılmış veri → Ziroobank karşılaştırması.

Kullanım:
  rei-ekstre <ekstre.pdf> [--ziroobank <kayitlar.json>] [--json <çıktı.json>] [--ayrinti] [--keep]
Çıktı: stdout'a Telegram'a uygun kısa özet (yalnız sayılar ve kişi başı toplamlar; satır
bazlı finans verisi YOK); --ayrinti ile olası/kayıtsız kalemler de basılır (Rei'nin okuması
için, Telegram'a olduğu gibi gitmez); --json ile tam rapor (işlem satırları dahil) dosyaya.
PDF yalnız inbox altındaysa ve ayrıştırma beyanla tutuyorsa silinir (--keep verilmezse). Ziroobank JSON'u ana VPS'ten `rei-vps ziroobank-oku` ile gelir.
Kural: ekstre ayı k → Ziroobank ayı k+1 (taksitler bir sonraki ay toplanır).
"""
import argparse, json, os, re, sys
from collections import defaultdict

AYLAR = ["Ocak","Şubat","Mart","Nisan","Mayıs","Haziran","Temmuz","Ağustos","Eylül","Ekim","Kasım","Aralık"]
AY_NO = {a: i+1 for i, a in enumerate(AYLAR)}
TUTAR = r"\+?[\d.]+,\d{2}"
RE_ISLEM = re.compile(
    rf"^(\d{{2}}) ({'|'.join(AYLAR)}) (\d{{4}}) (.+?) ({TUTAR})(?: ({TUTAR}) / (\d+))?(?: (\d+))?(?: [^\d]+)?$")  # sonda "Temassız İşlem" gibi not olabilir
RE_TAKSIT = re.compile(rf"^({TUTAR}) TL'lik (işlemin|iadenin) (\d+) / (\d+) taksidi$")
RE_ALAN = re.compile(r"^(Hesap Kesim Tarihi|Son Ödeme Tarihi|Dönem Borcu|Dönem İçi Harcamalar|Dönem İçi Ödemeler|Kalan Toplam Taksit Tutarı|Önceki Dönem Hesap Özeti Borcu) :(.+?)(?: TL\b.*)?$")

def kurus(s):
    s = s.strip()
    isaret = -1 if s.startswith("+") else 1      # ekstrede + = iade/ödeme (borcu azaltır)
    s = s.lstrip("+").replace(".", "").replace(",", "")
    return isaret * int(s)

def tl(k):
    isaret = "-" if k < 0 else ""
    k = abs(k)
    return f"{isaret}{k//100:,}".replace(",", ".") + f",{k%100:02d} TL"

def ay_ekle(ym, n):
    y, m = map(int, ym.split("-")); m += n
    return f"{y + (m-1)//12}-{(m-1)%12+1:02d}"

def pdf_oku(yol):
    import pdfplumber
    alanlar, islemler = {}, []
    with pdfplumber.open(yol) as pdf:
        satirlar = [l for p in pdf.pages for l in (p.extract_text() or "").splitlines()]
    for l in satirlar:
        l = l.strip()
        m = RE_ALAN.match(l)
        if m:
            alanlar.setdefault(m.group(1), m.group(2).strip()); continue
        m = RE_ISLEM.match(l)
        if m:
            g, ay, yil, acik, tutar, kalan, kalan_n, puan = m.groups()
            islemler.append({
                "tarih": f"{yil}-{AY_NO[ay]:02d}-{g}", "aciklama": acik.strip(),
                "tutar_kurus": kurus(tutar), "puan": int(puan) if puan else None,
                "kalan_kurus": kurus(kalan) if kalan else None,
                "kalan_taksit": int(kalan_n) if kalan_n else None,
                "odeme": acik.startswith("ÖDEME"), "taksit": None,
            }); continue
        m = RE_TAKSIT.match(l)
        if m and islemler:
            toplam, tur, k, n = m.groups()
            islemler[-1]["taksit"] = {"toplam_kurus": kurus(toplam), "k": int(k), "n": int(n), "iade": tur == "iadenin"}
    return alanlar, islemler

def kesim_ayi(alanlar):
    m = re.match(rf"(\d+) ({'|'.join(AYLAR)}) (\d{{4}})", alanlar.get("Hesap Kesim Tarihi", ""))
    return f"{m.group(3)}-{AY_NO[m.group(2)]:02d}" if m else None

def karsilastir(islemler, zb, ay):
    """Ekstredeki taksitli işlemleri Ziroobank kayıtlarıyla eşleştirir.
    Ziroobank'ta peşin fiyat (TL'ye yuvarlı) yazılıyor, kartta vade farkı ekli: kart/ZB oranı
    [0.995, 1.15] ve taksit sayısı farkı ≤1 ise aday; oran 1'e en yakın olan seçilir.
    Oran ≤ 1.001 ve taksit sayısı eşitse "kesin", değilse "olası" (Rei açıklamalara bakıp karar verir)."""
    # Yalnız k+1 ayında aktif (başlamış, bitmemiş) ve tutarı/taksiti geçerli kayıtlar aday.
    aktif = [k for k in zb["kayitlar"] if k["total_kurus"] > 0 and k["installments"] > 0
             and k["start_month"] <= ay <= ay_ekle(k["start_month"], k["installments"] - 1)]
    taksitli = [i for i in islemler if i["taksit"] and not i["taksit"]["iade"]]
    ciftler = []
    for ix, i in enumerate(taksitli):
        t = i["taksit"]
        for k in aktif:
            r = t["toplam_kurus"] / k["total_kurus"]
            if 0.995 <= r <= 1.15 and abs(k["installments"] - t["n"]) <= 1:
                ciftler.append((abs(r - 1) + (0 if k["installments"] == t["n"] else 0.05), ix, k["id"], r))
    # En iyi çiftten başlayarak ata (açgözlü ama sıradan bağımsız): her işlem ve kayıt en çok bir kez.
    ciftler.sort()
    atanan_islem, atanan_kayit, eslesen = {}, set(), []
    kayit_by_id = {k["id"]: k for k in aktif}
    for puan, ix, kid, r in ciftler:
        if ix in atanan_islem or kid in atanan_kayit: continue
        atanan_islem[ix] = kid; atanan_kayit.add(kid)
        i, k, t = taksitli[ix], kayit_by_id[kid], taksitli[ix]["taksit"]
        eslesen.append({"islem": i, "kayit": k, "oran": round(r, 4),
                        "derece": "kesin" if r <= 1.001 and k["installments"] == t["n"] else "olası",
                        "taksit_uyumsuz": k["installments"] != t["n"]})
    eslesmeyen = [i for ix, i in enumerate(taksitli) if ix not in atanan_islem]
    bos = [k for k in aktif if k["id"] not in atanan_kayit]
    # k+1 ayında kişi başı tahsilat: o ay aktif kayıtların aylık tutarı (Ziroobank kuralı: Aylık = Toplam/Taksit)
    tahsilat = defaultdict(int)
    for k in aktif:
        tahsilat[k["person"]] += k["total_kurus"] // k["installments"]
    return eslesen, eslesmeyen, bos, dict(tahsilat)

def main():
    p = argparse.ArgumentParser()
    p.add_argument("pdf"); p.add_argument("--ziroobank"); p.add_argument("--json")
    p.add_argument("--ayrinti", action="store_true"); p.add_argument("--keep", action="store_true")
    a = p.parse_args()
    alanlar, islemler = pdf_oku(a.pdf)
    harcama = sum(i["tutar_kurus"] for i in islemler if not i["odeme"])
    beyan = kurus(alanlar["Dönem İçi Harcamalar"]) if "Dönem İçi Harcamalar" in alanlar else None
    ay_k = kesim_ayi(alanlar); ay_k1 = ay_ekle(ay_k, 1) if ay_k else None
    taksitli = [i for i in islemler if i["taksit"] and not i["taksit"]["iade"]]
    rapor = {"alanlar": alanlar, "kesim_ayi": ay_k, "ziroobank_ayi": ay_k1, "islem_sayisi": len(islemler),
             "harcama_toplam_kurus": harcama, "beyan_harcama_kurus": beyan, "islemler": islemler}
    ozet = [f"Ekstre {ay_k}: dönem borcu {alanlar.get('Dönem Borcu','?')}, {len(islemler)} işlem, {len(taksitli)} taksitli.",
            ("Satır toplamı ekstreyle TUTUYOR." if beyan == harcama else
             f"UYARI: satır toplamı {tl(harcama)} ≠ beyan {tl(beyan) if beyan is not None else '?'} (ayrıştırma eksik olabilir).")]
    if a.ziroobank and not ay_k1:
        ozet.append("UYARI: Hesap Kesim Tarihi okunamadı, Ziroobank karşılaştırması atlandı.")
    elif a.ziroobank:
        with open(a.ziroobank, encoding="utf-8") as f: zb = json.load(f)
        eslesen, eslesmeyen, bos, tahsilat = karsilastir(islemler, zb, ay_k1)
        toplam_tahsilat = sum(tahsilat.values())
        donem_borcu = kurus(alanlar["Dönem Borcu"]) if "Dönem Borcu" in alanlar else None
        ozet.append(f"Ziroobank {ay_k1}: " + ", ".join(f"{k} {tl(v)}" for k, v in sorted(tahsilat.items())) +
                    f"; toplam {tl(toplam_tahsilat)}" + (f", Yusuf'a kalan {tl(donem_borcu - toplam_tahsilat)}" if donem_borcu is not None else ""))
        ozet.append(f"Eşleşen taksit {len(eslesen)}, kartta olup Ziroobank'ta olmayan {len(eslesmeyen)}, "
                    f"Ziroobank'ta aktif olup bu ekstrede görünmeyen {len(bos)}.")
        kesin = sum(1 for e in eslesen if e["derece"] == "kesin")
        ozet[-1] += f" (kesin {kesin}, olası {len(eslesen)-kesin})"
        ayrinti = []
        for e in eslesen:
            if e["taksit_uyumsuz"] or e["derece"] == "olası":
                ayrinti.append(f"  {e['derece'].capitalize()}: {e['kayit']['person']} '{e['kayit']['description']}' ZB {tl(e['kayit']['total_kurus'])}×{e['kayit']['installments']} ↔ kart {tl(e['islem']['taksit']['toplam_kurus'])}×{e['islem']['taksit']['n']} ({e['islem']['taksit']['k']}. taksit).")
        for i in eslesmeyen:
            ayrinti.append(f"  Kayıtsız: {i['taksit']['k']}/{i['taksit']['n']} taksit, toplam {tl(i['taksit']['toplam_kurus'])} ({i['tarih']}).")
        for k in bos:
            ayrinti.append(f"  Ekstrede yok: {k['person']} '{k['description']}' ZB {tl(k['total_kurus'])}×{k['installments']} ({k['start_month']}'den).")
        rapor.update({"eslesen": eslesen, "eslesmeyen": eslesmeyen, "ziroobankta_fazla": bos,
                      "tahsilat_kurus": tahsilat, "ayrinti": ayrinti})
        if a.ayrinti: ozet += ayrinti
    if a.json:
        with open(a.json, "w", encoding="utf-8") as f: json.dump(rapor, f, ensure_ascii=False, indent=1)
        ozet.append(f"Tam rapor: {a.json}")
    print("\n".join(ozet))
    if not a.keep:
        inbox = os.path.realpath(os.path.expanduser("~/.claude/channels/telegram/inbox"))
        gercek = os.path.realpath(a.pdf)
        if not gercek.startswith(inbox + os.sep):
            print("[rei-ekstre] PDF inbox dışında, silinmedi.", file=sys.stderr)
        elif not islemler or beyan != harcama:
            print("[rei-ekstre] Ayrıştırma doğrulanamadı, PDF silinmedi.", file=sys.stderr)
        else:
            try: os.remove(gercek)
            except OSError as e: print(f"[rei-ekstre] PDF silinemedi: {e}", file=sys.stderr)

if __name__ == "__main__":
    main()
