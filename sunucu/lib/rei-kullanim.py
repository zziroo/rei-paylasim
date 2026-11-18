#!/usr/bin/env python3
# Rei transkriptlerinden bir günün kullanımını özetler: rei-kullanim.py <projects-kökü> <YYYY-MM-DD>. rei-sabah.sh çağırır.
import glob, json, sys, datetime
kok, gun = sys.argv[1], sys.argv[2]
tz = datetime.timezone(datetime.timedelta(hours=3))
cik = gir = yusuf = cevap = oturum = 0
gorulen = set(); cikti = {}  # Claude Code bir asistan mesajını içerik bloğu başına ayrı satıra yazar; usage aynı message.id ile tekrarlanır
for f in glob.glob(kok + '/*/*.jsonl'):
    gordu = False
    for satir in open(f, errors='ignore'):
        try: d = json.loads(satir)
        except Exception: continue
        ts = d.get('timestamp')
        if not ts: continue
        try: t = datetime.datetime.fromisoformat(ts.replace('Z', '+00:00')).astimezone(tz)
        except Exception: continue
        if t.strftime('%Y-%m-%d') != gun: continue
        gordu = True
        m = d.get('message') or {}
        if d.get('type') == 'user' and isinstance(m.get('content'), str): yusuf += 1
        u = m.get('usage') if isinstance(m, dict) else None
        if u:
            mid = m.get('id') or (f, ts)
            # Aynı mesajın akış satırlarında output_tokens kısmi olabilir: id başına en büyüğü alınır; girdi alanları ilk satırda sabit.
            if mid in gorulen:
                cikti[mid] = max(cikti.get(mid, 0), u.get('output_tokens', 0)); continue
            gorulen.add(mid); cikti[mid] = u.get('output_tokens', 0)
            cevap += 1
            gir += u.get('input_tokens', 0) + u.get('cache_read_input_tokens', 0) + u.get('cache_creation_input_tokens', 0)
    if gordu: oturum += 1
cik = sum(cikti.values())
if cevap == 0: print("dün hiç konuşma olmadı")
else: print(f"{oturum} oturum, {yusuf} mesaj, {cevap} model çağrısı, {cik//1000}k çıktı / {gir//1000}k girdi token")
