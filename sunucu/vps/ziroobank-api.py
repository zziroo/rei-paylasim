#!/usr/bin/env python3
"""Ana VPS'te `deploy` olarak çalışır (rei-kapi `ziroobank-api` alt komutu).
Ziroobank API'sine (127.0.0.1:3100, uygulamanın kendi girişiyle) tek istek atar:
  ziroobank-api.py <GET|POST|PUT|DELETE> </yol> [<json gövde>]
Kimlik: /etc/rei/ziroobank.env (root:deploy 640): ZB_USER, ZB_PASS. Şifre hiç yazdırılmaz.
Yanıt: ilk satır HTTP kodu, sonra gövde. Her istekte giriş + çıkış yapılır (oturum kalmaz)."""
import json, re, sys, urllib.request, urllib.error

BASE = "http://127.0.0.1:3100/ziroobank/api"
ENV = "/etc/rei/ziroobank.env"
# Pozitif beyaz liste (yöntem, tam yol regex'i). Kullanıcı/şifre/giriş uçları bilerek yok.
# Yol noktalı segment ("." / "..") ve "//" içeremez: sunucu new URL() ile bunları normalize eder,
# yasak-listesi yaklaşımı /./users ile atlatılabilirdi (inceleme bulgusu 2026-11-01).
IZINLI = [("GET", r"/(me|state|audit|mail/status)"), ("POST", r"/(entries|mail/resend)"),
          ("PUT", r"/entries/\d{1,12}"), ("DELETE", r"/entries/\d{1,12}"), ("PUT", r"/months/\d{4}-\d{2}")]

def env():
    d = {}
    with open(ENV, encoding="utf-8") as f:
        for l in f:
            l = l.strip()
            if l and not l.startswith("#") and "=" in l:
                k, v = l.split("=", 1); d[k] = v
    return d["ZB_USER"], d["ZB_PASS"]

def main():
    if len(sys.argv) not in (3, 4): print("kullanım: METHOD /yol [json]", file=sys.stderr); return 2
    method, path = sys.argv[1].upper(), sys.argv[2]
    if method not in ("GET", "POST", "PUT", "DELETE"): print("yöntem reddedildi", file=sys.stderr); return 2
    if len(path) > 200 or not path.startswith("/") or "//" in path or "." in path \
            or not any(m == method and re.fullmatch(rx, path) for m, rx in IZINLI):
        print("yol reddedildi (yalnız me/state/audit, entries, months, mail/status, mail/resend)", file=sys.stderr); return 2
    body = None
    if len(sys.argv) == 4:
        try: body = json.dumps(json.loads(sys.argv[3])).encode()
        except ValueError: print("gövde geçerli JSON değil", file=sys.stderr); return 2
    user, pw = env()
    # Çerez `Secure` işaretli (uygulama Caddy arkasında https sayar); http.cookiejar http'de göndermez,
    # bu yüzden oturum çerezi Set-Cookie'den elle alınıp başlıkla gönderilir.
    # /mail/resend sunucuda sıra bekleyip gönderim yaptığı için uzun sürebilir; zaman aşımı ona göre.
    hdr = {"Content-Type": "application/json", "X-Requested-With": "ziroobank"}
    def istek(m, p, b=None):
        req = urllib.request.Request(BASE + p, data=b, method=m, headers=hdr)
        try:
            with urllib.request.urlopen(req, timeout=60 if p == "/mail/resend" else 20) as r: return r.status, r.read().decode(), r.headers.get("Set-Cookie", "")
        except urllib.error.HTTPError as e: return e.code, e.read().decode(), ""
    kod, giris, cerez = istek("POST", "/login", json.dumps({"username": user, "password": pw}).encode())
    m = re.match(r"zb_session=([A-Za-z0-9_-]+)", cerez)
    if kod != 200 or not m: print(f"giriş başarısız: {kod}", file=sys.stderr); return 1
    hdr["Cookie"] = f"zb_session={m.group(1)}"
    try:
        kod, govde, _ = istek(method, path, body)
        print(kod); print(govde)
    finally:
        istek("POST", "/logout")
    return 0 if kod < 400 else 1

if __name__ == "__main__":
    sys.exit(main())
