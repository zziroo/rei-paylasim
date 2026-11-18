#!/usr/bin/env python3
"""Rei'nin Claude girişinin bitiş tarihleri: `<refresh epoch sn> <access epoch sn>` basar; dosya yoksa `yok`, okunamazsa ya da alan yoksa `bilinmiyor`.
`bilinmiyor` arıza sayılmaz: yazılırken okuma ya da CLI'nin dosya biçimini değiştirmesi sağlıklı Rei'ye kalıcı yanlış alarm üretmesin.
Yalnız iki tarih alanı alınır, token değerleri hiçbir yere yazılmaz. rei kullanıcısıyla çalışır (root rei yolunda dosya açmaz).
Çağıranlar: rei-ping.sh, rei-sabah.sh. Kurulu yer: /usr/local/lib/rei/rei-giris.py (root 644)."""
import json

YOL = "/home/rei/.claude/.credentials.json"


def main():
    try:
        with open(YOL, encoding="utf-8") as f:
            o = json.load(f)["claudeAiOauth"]
        print(int(o["refreshTokenExpiresAt"]) // 1000, int(o["expiresAt"]) // 1000)
    except FileNotFoundError:
        print("yok")
    except (OSError, ValueError, KeyError, TypeError):
        print("bilinmiyor")


if __name__ == "__main__":
    main()
