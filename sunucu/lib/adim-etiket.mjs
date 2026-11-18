// adim-etiket: araç çağrısı → kısa Türkçe adım etiketi ve sır maskesi. Ortak modül (2026-11-01):
// Rei sunucusunda rei-adim (Telegram adım kartı; sunucu/bin/rei-adim.mjs, kurulu /usr/local/lib/rei/rei-adim + yanında
// /usr/local/lib/rei/adim-etiket.mjs) ve Mac'te adim.mjs (iş klasörüne adım günlüğü; sunucu/mac/adim.mjs, kurulu
// /usr/local/lib/rei-mac/adim-etiket.mjs) aynı dosyayı kullanır. Bağımlılık yok (Node 22+).
// Güvenlik: etikete yalnız dosya adı (yol değil), Bash'in Türkçe description'ı ya da komutun ilk 60 karakteri
// (ev dizini ~ olur, token deseni maskelenir), Gmail/Takvim/Drive için yalnız eylem türü girer.
import { basename } from 'node:path'

const PLAN_SATIR = 8 // kartta görünen plan maddesi sayısı

export const str = v => (typeof v === 'string' ? v : '')
export function tekSatir(s) { return s.replace(/[\r\n\t]+/g, ' ').replace(/\s{2,}/g, ' ').trim() }
export function kisalt(s, n) { return s.length > n ? s.slice(0, n - 1) + '…' : s }
// Karta giden metinden sır olabilecek parçaları maskeler (bot token düz ve %3A'lı, KEY=/TOKEN=/
// PASS= atamaları, Bearer, sk-/gh*_/github_pat_ anahtarları, curl -u kullanıcı:parola) ve ev dizinini kısaltır.
export function temizle(s) {
  return tekSatir(s)
    .replace(/[\x00-\x1f\x7f]/g, '')
    .replace(/\d{5,}(?::|%3A)[A-Za-z0-9_-]{20,}/gi, '***')
    .replace(/\b(\w*(?:KEY|TOKEN|SECRET|PASS\w*|PAROLA|SIFRE|ŞİFRE)\w*)=\S+/gi, '$1=***')
    .replace(/\bBearer\s+\S+/gi, 'Bearer ***')
    .replace(/\bsk-[A-Za-z0-9_-]{10,}/g, '***')
    .replace(/\bgithub_pat_[A-Za-z0-9_]{10,}/g, '***')
    .replace(/\bgh[pousr]_[A-Za-z0-9]{10,}/g, '***')
    .replace(/(\s-u\s+)\S+/g, '$1***')
    .replace(/\/(?:home|Users)\/[A-Za-z0-9_-]+\//g, '~/')
}
export function dosyaAdi(yol) {
  const ad = temizle(basename(str(yol)))
  return ad || 'dosya'
}
// rei-mac alt komutları → Yusuf'un anlayacağı etiket (kartta "Mac: is" anlaşılmıyordu, 2026-11-01)
const MAC_ALT = {
  is: 'iş başlat',
  durum: 'durum sor',
  log: 'günlük',
  devam: 'mesaj ilet',
  'devam-ayar': 'ayarlı mesaj ilet',
  cevap: 'cevap ilet',
  'cevap-ayar': 'ayarlı cevap ilet',
  bitir: 'işi bitir',
  iptal: 'işi iptal et',
  yollar: 'değişen yollar',
  birlestir: 'main\'e birleştir',
}
function altKomut(cmd, arac) {
  const m = new RegExp(`(?:^|[\\s;&|])(?:~/bin/|/home/[A-Za-z0-9_-]+/bin/)?${arac}\\s+([A-Za-z0-9_-]+)`).exec(cmd)
  return m ? m[1] : ''
}

// Gmail/Takvim/Drive: yalnız eylem türü (başlık, arama metni, kişi adı karta girmez).
const EYLEM = [
  [/^(search|list|find)/, 'arama'],
  [/^(get|read|download|fetch)/, 'okuma'],
  [/draft/, 'taslak'],
  [/label/, 'etiketleme'],
  [/^(mark|unmark)/, 'işaretleme'],
  [/share/, 'paylaşma'],
  [/trash|delete|remove/, 'silme'],
  [/respond/, 'yanıt'],
  [/suggest/, 'öneri'],
  [/create|insert|add/, 'ekleme'],
  [/update|edit|write|append|replace|batch|copy|move|rename/, 'değişiklik'],
]
const SUNUCU = [
  ['Gmail', '✉️ Gmail'],
  ['Google_Calendar', '📅 Takvim'],
  ['Google_Drive', '📁 Drive'],
  ['Google_Docs', '📄 Docs'],
  ['Google_Sheets', '📊 Sheets'],
  ['Google_Slides', '📽 Slides'],
  ['Claude_Docs', '📄 Claude Docs'],
]
function eylem(arac) {
  const a = arac.toLowerCase()
  return (EYLEM.find(([re]) => re.test(a)) || [null, 'işlem'])[1]
}

// Rei'nin kendi mesajı: bundan sonra kart yukarıda kalır, bir sonraki adımda en alta taşınır.
export function karttanSonraMesaj(ad) {
  return /^mcp__plugin_telegram_[A-Za-z0-9_]*__(reply|ask)$/.test(ad) // edit_message yeni mesaj eklemez
}
// TodoWrite girdisinden plan satırları: [{ad, durum}] (durum: pending|in_progress|completed).
export function planCikar(g) {
  const todos = g && typeof g === 'object' && Array.isArray(g.todos) ? g.todos : null
  if (!todos || !todos.length) return null
  return todos.slice(0, PLAN_SATIR).map(t => ({
    ad: kisalt(temizle(str(t && t.content)) || 'adım', 60),
    durum: str(t && t.status) || 'pending',
  }))
}

// Araç adı + girdiden kısa Türkçe etiket; null = adım sayılmaz.
export function etiketle(ad, g) {
  if (/^mcp__plugin_telegram_/.test(ad)) return null
  const girdi = g && typeof g === 'object' ? g : {}
  switch (ad) {
    case 'Bash': {
      const cmd = str(girdi.command)
      let alt
      if ((alt = altKomut(cmd, 'rei-vps'))) return `🔗 ist-1: ${alt}`
      if ((alt = altKomut(cmd, 'rei-mac'))) return `🖥 Mac: ${MAC_ALT[alt] || alt}`
      if (/\brei-ses\b/.test(cmd)) return '🎤 ses çözümleme'
      if (/\brei-ekstre\b/.test(cmd)) return '🧾 ekstre'
      if (/\brei-mdv2\b/.test(cmd)) return '📝 Telegram biçimi'
      const d = temizle(str(girdi.description))
      return '💻 ' + kisalt(d || temizle(cmd) || 'komut', 60)
    }
    case 'Read':
      return '📖 ' + dosyaAdi(girdi.file_path)
    case 'Grep':
      return '🔍 ' + (girdi.path ? dosyaAdi(girdi.path) : 'arama')
    case 'Glob':
      return '🔍 ' + kisalt(temizle(str(girdi.pattern)) || 'dosya arama', 40)
    case 'Write':
    case 'Edit':
    case 'MultiEdit':
      return '📝 ' + dosyaAdi(girdi.file_path)
    case 'NotebookEdit':
      return '📝 ' + dosyaAdi(girdi.notebook_path)
    case 'Agent':
    case 'Task': {
      const d = temizle(str(girdi.description))
      return '🤖 alt ajan' + (d ? `: ${kisalt(d, 40)}` : '')
    }
    case 'WebFetch':
    case 'WebSearch':
      return '🌐 web'
    case 'Skill':
      return '🧩 ' + kisalt(temizle(str(girdi.skill)) || 'beceri', 30)
    case 'TodoWrite':
      return null // plan satırları ayrıca kartın üstünde gösterilir, adım sayılmaz
    case 'TaskCreate':
    case 'TaskUpdate':
    case 'TaskList':
    case 'TaskGet':
      return '📋 plan'
    case 'AskUserQuestion':
      return '❓ soru'
  }
  const m = /^mcp__(.+?)__(.+)$/.exec(ad)
  if (m) {
    const [, sunucu, arac] = m
    const s = SUNUCU.find(([k]) => sunucu.includes(k))
    if (s) return `${s[1]}: ${eylem(arac)}`
    return '🔧 ' + kisalt(temizle(sunucu.replace(/^claude_ai_/, '').replace(/_/g, ' ')), 30) + ': ' + eylem(arac)
  }
  return '🔧 ' + kisalt(temizle(ad), 40)
}
