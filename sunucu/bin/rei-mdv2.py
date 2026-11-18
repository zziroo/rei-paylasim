#!/usr/bin/env python3
"""Rei: sade Markdown → Telegram MarkdownV2 (kaçışlı). stdin → stdout.
Desteklenen: **kalın**, _italik_, `kod`, ```blok```, [metin](url), satır başı "- " → "• ", "# Başlık" → kalın.
Diğer her özel karakter (. - ( ) ! # + = | { } > ~) otomatik kaçırılır; böylece reply format=markdownv2 güvenle kullanılır."""
import re, sys

OZEL = r"([_*\[\]()~`>#+\-=|{}.!\\])"
def kacir(t): return re.sub(OZEL, r"\\\1", t)
def kacir_kod(t): return t.replace("\\", "\\\\").replace("`", "\\`")

TOKEN = re.compile(r"```(.*?)```|`([^`\n]+)`|\*\*(.+?)\*\*|(?<!\w)_(.+?)_(?!\w)|\[([^\]]+)\]\(([^)\s]+)\)", re.DOTALL)

def satir_on(t):
    out = []
    for l in t.split("\n"):
        m = re.match(r"^(\s*)[-*] (.*)$", l)
        if m: l = f"{m.group(1)}• {m.group(2)}"
        m = re.match(r"^#{1,6} (.*)$", l)
        if m: l = f"**{m.group(1)}**"
        out.append(l)
    return "\n".join(out)

def donustur(t):
    t = satir_on(t)
    out, son = [], 0
    for m in TOKEN.finditer(t):
        out.append(kacir(t[son:m.start()]))
        blok, kod, kalin, italik, ltext, url = m.groups()
        if blok is not None: out.append("```" + kacir_kod(blok) + "```")
        elif kod is not None: out.append("`" + kacir_kod(kod) + "`")
        elif kalin is not None: out.append("*" + kacir(kalin) + "*")
        elif italik is not None: out.append("_" + kacir(italik) + "_")
        else: out.append("[" + kacir(ltext) + "](" + url.replace("\\", "\\\\").replace(")", "\\)") + ")")
        son = m.end()
    out.append(kacir(t[son:]))
    return "".join(out)

if __name__ == "__main__":
    sys.stdout.write(donustur(sys.stdin.read()))
