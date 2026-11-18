#!/home/rei/whisper/bin/python
"""Rei: sesli mesajı metne çevirir (faster-whisper, yerel, Türkçe).

Kullanım: rei-ses <ses-dosyası> [--model base|small] [--keep]
Çıktı: stdout'a metin (ad düzeltmeleri uygulanmış). Dosya Telegram inbox'ındaysa
çözümlemeden sonra silinir (--keep verilmezse): sesli mesaj sunucuda kalmaz.
"""
import argparse, os, re, subprocess, sys, time

# "Rei" Türkçe ses tanımada reis/rey/ray/Rei's diye çıkar; hepsi Rei'dir (karar 2026-10-31).
AD_DUZELTME = re.compile(r"\b(reis|rey|ray|reyis|reys|rei)(['’]?s)?\b", re.IGNORECASE)
INITIAL_PROMPT = "Rei, Yusuf'un asistanı. Rei, ekstre, vault, deploy, Tussle, Diskort, Ziroobank."

def main():
    p = argparse.ArgumentParser()
    p.add_argument("dosya")
    p.add_argument("--model", default=os.environ.get("REI_SES_MODEL", "small"))
    p.add_argument("--keep", action="store_true")
    p.add_argument("--stat", action="store_true", help="stderr'e süre ve olasılık yaz")
    a = p.parse_args()
    if not os.path.isfile(a.dosya):
        print(f"dosya yok: {a.dosya}", file=sys.stderr); return 2
    t0 = time.time()
    # Sesi ffmpeg ile 16 kHz mono float32'ye çevir (PyAV sürüm uyumsuzluğundan kaçınır).
    import numpy as np
    ff = subprocess.run(["ffmpeg", "-nostdin", "-loglevel", "error", "-protocol_whitelist", "file", "-i", a.dosya,
                         "-f", "f32le", "-ac", "1", "-ar", "16000", "-"], capture_output=True)
    if ff.returncode != 0:
        print(f"ffmpeg hata: {ff.stderr.decode(errors='replace')[:300]}", file=sys.stderr); return 3
    ses = np.frombuffer(ff.stdout, dtype=np.float32)
    from faster_whisper import WhisperModel
    model = WhisperModel(a.model, device="cpu", compute_type="int8", cpu_threads=1)
    t1 = time.time()
    segments, info = model.transcribe(
        ses, language="tr", beam_size=1, vad_filter=True,
        initial_prompt=INITIAL_PROMPT, condition_on_previous_text=False)
    parcalar = []
    for s in segments:
        parcalar.append(s.text.strip())
    t2 = time.time()
    metin = " ".join(x for x in parcalar if x)
    metin = AD_DUZELTME.sub("Rei", metin)
    print(metin)
    if a.stat:
        print(f"[rei-ses] model={a.model} yükleme={t1-t0:.1f}s çözümleme={t2-t1:.1f}s "
              f"ses={info.duration:.1f}s dil_olasılık={info.language_probability:.2f}", file=sys.stderr)
    inbox = os.path.realpath(os.path.expanduser("~/.claude/channels/telegram/inbox"))
    gercek = os.path.realpath(a.dosya)
    if not a.keep and gercek.startswith(inbox + os.sep):   # yalnız Telegram inbox'ındaki dosya silinir
        try: os.remove(gercek)
        except OSError as e: print(f"[rei-ses] silinemedi: {e}", file=sys.stderr)
    return 0

if __name__ == "__main__":
    sys.exit(main())
