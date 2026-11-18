#!/usr/bin/env python3
"""Ana VPS'te çalışır: Ziroobank DB'sini SALT OKUNUR açar, silinmemiş kayıtları JSON basar.
rei-kapi'nin `ziroobank-oku` alt komutu. Yazma yok, docker yok, root gerekmez
(DB dosyaları herkesçe okunabilir; WAL modunda mode=ro ile okunur)."""
import json, sqlite3, sys
DB = "/opt/ziroobank/data/ziroobank.db"
c = sqlite3.connect(f"file:{DB}?mode=ro", uri=True)
c.row_factory = sqlite3.Row
out = {
    "kullanicilar": [dict(r) for r in c.execute(
        "SELECT username, display_name, is_payer, active FROM users ORDER BY id")],
    "kayitlar": [dict(r) for r in c.execute(
        "SELECT id, person, description, total_kurus, installments, start_month "
        "FROM entries WHERE deleted_at IS NULL ORDER BY start_month, id")],
    "aylar": [dict(r) for r in c.execute("SELECT month, paid FROM months ORDER BY month")],
}
json.dump(out, sys.stdout, ensure_ascii=False)
