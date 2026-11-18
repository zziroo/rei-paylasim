#!/usr/bin/env node
// rei-adim: Rei'nin Telegram'daki adım kartı (Claude Code'daki adım listesinin karşılığı).
// Rei'nin oturumunda PostToolUse ve Stop hook'u olarak çalışır (managed settings, kaynak
// sunucu/etc/managed-settings.json). Kurulu yer: /usr/local/lib/rei/rei-adim (root 755);
// Node 22, bağımlılık yok (uzantısız dosyada ESM sözdizimi Node 22'nin kendi algılamasıyla çalışır).
//
// Akış: turun ilk (Telegram eklentisi dışı) araç çağrısında Bot API sendMessage ile
// "⏳ çalışıyorum" + ilk adım; sonraki çağrılarda editMessageText ile son 5 adım (2 sn'den sık
// düzenlenmez: sık çağrıda bekletmeden atlanır, adım durum dosyasında birikir ve sonraki
// düzenlemede görünür; Telegram düzenlemeleri bildirim üretmez). Stop'ta kart "✅ bitti
// (N adım, X sn)" diye son kez düzenlenir ve durum dosyası silinir (yeni tur yeni kart).
// Yalnız Telegram eklentisi araçlarından (reply/react/edit_message/ask/download_attachment)
// oluşan turda kart hiç açılmaz. Alt ajanların kendi çağrıları (agent_id) karta girmez;
// Agent aracı bitince tek satır "🤖 alt ajan" düşer. Rei araya mesaj atarsa (reply/ask/edit)
// kart yukarıda kalır: bir sonraki adımda eski kart silinip en alta sessizce yeniden gönderilir
// (Yusuf'un isteği, 2026-11-01). Rei TodoWrite ile plan yazarsa plan satırları ☑/⏳/☐ ile
// kartın üstünde görünür (Claude Code'daki adım listesinin karşılığı).
//
// Güvenlik: karta yalnız adım etiketi gider: dosya adı (yol değil), Bash'in Türkçe
// description'ı ya da komutun ilk 60 karakteri (ev dizini ~ olur, token deseni maskelenir),
// Gmail/Takvim/Drive için yalnız eylem türü. Token ~/.claude/channels/telegram/.env'den
// okunur, bellekte kalır, loglanmaz; chat_id yalnız access.json allowFrom[0]; istek yalnız
// https://api.telegram.org'a. Her hata yutulur, çıkış her zaman 0; Rei hiç durmaz.
// Durum: ~/.cache/rei-adim/<session_id>.json (+ .lock: paralel araç çağrılarında iki hook
// aynı anda çalışabilir, kilit çifte kart açılmasını önler).
import { readFileSync, writeFileSync, mkdirSync, openSync, closeSync, unlinkSync, statSync, renameSync } from 'node:fs'
import { homedir } from 'node:os'
import { join } from 'node:path'

const CONFIG_DIR = process.env.CLAUDE_CONFIG_DIR || join(homedir(), '.claude')
const ENV_FILE = join(CONFIG_DIR, 'channels', 'telegram', '.env')
const ACCESS_FILE = join(CONFIG_DIR, 'channels', 'telegram', 'access.json')
const TUR_BITTI_FILE = join(CONFIG_DIR, 'channels', 'telegram', 'tur-bitti')
const STATE_DIR = join(homedir(), '.cache', 'rei-adim')
const API = 'https://api.telegram.org'
const SON_ADIM = 5           // kartta görünen adım sayısı
const DUZENLEME_ARALIK = 2000 // ms; daha sık düzenleme atlanır
const ISTEK_ZAMAN_ASIMI = 2000 // ms; hook'un kendi süresi 5 sn (kilit 0,6 sn + silme 1,2 sn + istek 2 sn + açılış)
const SILME_ZAMAN_ASIMI = 1200
const KILIT_DENEME = 15, KILIT_BEKLE = 40, KILIT_BAYAT = 5000 // kilit en çok hook timeout'u kadar yaşar
const TUR_BAYAT = 15 * 60 * 1000 // son düzenlemeden bu kadar geçtiyse Stop gelmemiştir: yeni tur say

// ---- yardımcılar -----------------------------------------------------------
// Etiketleme, sır maskesi ve plan çıkarma ortak modülde (sunucu/lib/adim-etiket.mjs; kurulu /usr/local/lib/rei/adim-etiket.mjs,
// Mac'teki adım günlüğü hook'u da aynı dosyayı kullanır).
import { str, kisalt, temizle, karttanSonraMesaj, planCikar, etiketle } from './adim-etiket.mjs'

// ---- kimlik ---------------------------------------------------------------
function token() {
  for (const line of readFileSync(ENV_FILE, 'utf8').split('\n')) {
    const m = /^TELEGRAM_BOT_TOKEN=(.*)$/.exec(line.trim())
    if (m && /^\d+:[A-Za-z0-9_-]{20,}$/.test(m[1])) return m[1]
  }
  return null
}
function chatId() {
  const a = JSON.parse(readFileSync(ACCESS_FILE, 'utf8'))
  const id = Array.isArray(a.allowFrom) ? String(a.allowFrom[0] ?? '') : ''
  return /^-?\d{1,20}$/.test(id) ? id : null
}

// ---- Bot API ----------------------------------------------------------------
async function api(tok, yontem, govde, zamanAsimi = ISTEK_ZAMAN_ASIMI) {
  const res = await fetch(`${API}/bot${tok}/${yontem}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(govde),
    signal: AbortSignal.timeout(zamanAsimi),
  })
  const j = await res.json().catch(() => null)
  return j && j.ok ? j.result : null
}

// ---- durum ve kilit ---------------------------------------------------------
function durumYolu(sid) { return join(STATE_DIR, `${sid}.json`) }
function kilitAl(sid) {
  const yol = join(STATE_DIR, `${sid}.lock`)
  for (let i = 0; i < KILIT_DENEME; i++) {
    try {
      closeSync(openSync(yol, 'wx'))
      return yol
    } catch {
      try {
        if (Date.now() - statSync(yol).mtimeMs > KILIT_BAYAT) unlinkSync(yol)
      } catch { /* yarışta silinmiş olabilir */ }
      Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, KILIT_BEKLE) // kısa uyku
    }
  }
  return null
}
function kilitBirak(yol) { try { unlinkSync(yol) } catch { /* yok say */ } }
function durumOku(sid) {
  try { return JSON.parse(readFileSync(durumYolu(sid), 'utf8')) } catch { return null }
}
function durumYaz(sid, d) {
  const yol = durumYolu(sid)
  writeFileSync(yol + '.tmp', JSON.stringify(d))
  renameSync(yol + '.tmp', yol)
}
function durumSil(sid) { try { unlinkSync(durumYolu(sid)) } catch { /* yok */ } }

// ---- kart metni -------------------------------------------------------------
function sure(ms) {
  const s = Math.max(0, Math.round(ms / 1000))
  return s >= 90 ? `${Math.floor(s / 60)} dk ${s % 60} sn` : `${s} sn`
}
function kartMetni(d, bitti) {
  const gecen = Date.now() - d.baslangic
  const baslik = bitti
    ? `✅ bitti (${d.sayi} adım, ${sure(gecen)})`
    : `⏳ çalışıyorum (${d.sayi} adım, ${sure(gecen)})`
  const ilk = d.sayi - d.adimlar.length
  const satirlar = d.adimlar.map((a, i) => `${ilk + i + 1}. ${a}`)
  if (ilk > 0) satirlar.unshift(`… (+${ilk} önceki)`)
  const plan = (d.plan || []).map(t =>
    `${t.durum === 'completed' ? '☑' : t.durum === 'in_progress' ? '⏳' : '☐'} ${t.ad}`)
  const govde = plan.length ? [...plan, ...satirlar.map(l => `· ${l}`)] : satirlar
  return [baslik, ...govde].join('\n').slice(0, 3900)
}

// ---- ana akış ---------------------------------------------------------------
async function aracSonrasi(sid, g) {
  if (g.agent_id) return // alt ajanın kendi çağrıları; Agent aracı bitince tek satır düşer
  const ad = str(g.tool_name)
  const etiket = etiketle(ad, g.tool_input)
  const plan = ad === 'TodoWrite' ? planCikar(g.tool_input) : null
  const mesajAtti = karttanSonraMesaj(ad)
  if (!etiket && !plan && !mesajAtti) return
  const kilit = kilitAl(sid)
  if (!kilit) return
  try {
    const simdi = Date.now()
    let d = durumOku(sid)
    if (mesajAtti) {
      // Rei mesaj attı: kart artık en altta değil. Kart yoksa yapacak bir şey yok.
      if (d && d.message_id) { d.yukarida = true; durumYaz(sid, d) }
      return
    }
    // Stop hiç gelmediyse (API hatasıyla biten tur, öldürülen hook) eski kart düzenlenmesin.
    // Agent aracı 15 dk'dan uzun sürebilir (alt ajanın iç çağrıları karta girmez); onda bayatlık bakılmaz.
    const uzunArac = ad === 'Agent' || ad === 'Task'
    if (d && !uzunArac && simdi - (d.son || d.baslangic || 0) > TUR_BAYAT) d = null
    if (!d) d = { message_id: null, chat_id: null, baslangic: simdi, sayi: 0, adimlar: [], son: 0, plan: [], yukarida: false }
    if (plan) d.plan = plan
    if (!etiket && !d.message_id) { durumYaz(sid, d); return } // yalnız plan: kart ilk gerçek adımda açılır
    if (etiket) {
      d.sayi += 1
      d.adimlar.push(etiket)
      if (d.adimlar.length > SON_ADIM) d.adimlar.splice(0, d.adimlar.length - SON_ADIM)
    }
    if (d.message_id && d.yukarida) {
      // Kartı en alta taşı: eskisini sil (sonucu beklemeden), yenisini sessizce gönder.
      const tok = token()
      if (tok) {
        const silindi = await api(tok, 'deleteMessage', { chat_id: d.chat_id, message_id: d.message_id }, SILME_ZAMAN_ASIMI).catch(() => null)
        // Silinemediyse (zaman aşımı, API hatası) eski kartı düzenlemeye devam et; ikinci kart açma.
        if (silindi === true) d.message_id = null
        d.yukarida = false
      }
    }
    if (!d.message_id) {
      const tok = token(), cid = chatId()
      if (!tok || !cid) return
      if (d.sayi <= 1 && !d.son) d.baslangic = simdi
      const r = await api(tok, 'sendMessage', { chat_id: cid, text: kartMetni(d, false), disable_notification: true })
      if (r && r.message_id) { d.message_id = r.message_id; d.chat_id = cid; d.son = simdi }
    } else if (simdi - d.son >= DUZENLEME_ARALIK) {
      const tok = token()
      if (tok) {
        await api(tok, 'editMessageText', { chat_id: d.chat_id, message_id: d.message_id, text: kartMetni(d, false) })
        d.son = simdi
      }
    }
    durumYaz(sid, d)
  } finally {
    kilitBirak(kilit)
  }
}

async function durunca(sid) {
  const kilit = kilitAl(sid)
  if (!kilit) { durumSil(sid); return } // kilit alınamasa da durum sıfırlanır (silme kilitsiz de güvenli)
  try {
    const d = durumOku(sid)
    durumSil(sid)
    if (!d || !d.message_id) return
    const tok = token()
    if (!tok) return
    await api(tok, 'editMessageText', { chat_id: d.chat_id, message_id: d.message_id, text: kartMetni(d, true) })
  } finally {
    kilitBirak(kilit)
  }
}

// Tur reply/ask olmadan biterse (ör. "tamamdır" mesajına cevap gerekmez) eklentide "yazıyor"
// 10 dk'lık tavana kadar sürerdi (2026-11-01). Eklenti her tikte bu dosyanın zamanına bakar.
function turBittiYaz() {
  try { writeFileSync(TUR_BITTI_FILE, String(Date.now())) } catch { /* yok say */ }
}

try {
  const g = JSON.parse(readFileSync(0, 'utf8') || '{}')
  const sid = str(g.session_id)
  if (g.hook_event_name === 'Stop') turBittiYaz()
  if (/^[A-Za-z0-9_-]{1,80}$/.test(sid)) {
    mkdirSync(STATE_DIR, { recursive: true, mode: 0o700 })
    if (g.hook_event_name === 'PostToolUse') await aracSonrasi(sid, g)
    else if (g.hook_event_name === 'Stop') await durunca(sid)
  }
} catch {
  // her hata yutulur; hook Rei'yi asla durdurmaz
}
process.exit(0)
