// adim.mjs: Mac'teki `claude -p` işinin adım günlüğü (2026-11-01). Mac'teki rei kullanıcısının Claude ayarında
// (root sahipli ~/.claude/settings.json, kaynak sunucu/mac/rei-settings.json) PostToolUse + Stop hook'u:
//   /opt/homebrew/bin/node /usr/local/lib/rei-mac/adim.mjs
// Kurulu yer /usr/local/lib/rei-mac/ (root 644, yanında adim-etiket.mjs; rei yazamaz). İş klasörünü kapının verdiği
// REI_IS_DIZINI ortam değişkeninden bulur (cwd/CLAUDE_PROJECT_DIR değil); yoksa ya da biçimi tutmuyorsa sessiz çıkar.
// Her araç çağrısında <iş klasörü>/adimlar.log'a bir satır ekler: epoch<TAB>etiket (etiket adim-etiket.mjs'ten:
// dosya adı, Bash description'ı, alt ajan…; sır maskeli). Stop'ta "✔ tur bitti". Alt ajanların iç çağrıları girmez.
// `rei-mac durum <iş-id>` son 3 adımı ADIM: satırı olarak basar, `rei-mac log` ham günlüğü verir.
// Her hata yutulur, çıkış her zaman 0 (iş hiç durmaz). Girdi bir kez, en çok 1 MB okunur.
import { appendFileSync, readFileSync, statSync } from 'node:fs'
import { etiketle, str, temizle, kisalt } from './adim-etiket.mjs'

const DIZIN_DESENI = /^\/Users\/rei\/isler\/[0-9]{8}-[0-9]{6}-[a-z0-9-]{1,20}$/
const EN_COK_GIRDI = 1024 * 1024
const EN_COK_SATIR = 2000 // günlük bu kadar satırı geçince yeni satır yazılmaz (döngüye giren iş diski doldurmasın)

try {
  const dizin = str(process.env.REI_IS_DIZINI)
  if (DIZIN_DESENI.test(dizin) && statSync(dizin).isDirectory()) {
    const ham = readFileSync(0, { encoding: 'utf8' })
    const g = JSON.parse((ham.length > EN_COK_GIRDI ? '' : ham) || '{}')
    let etiket = null
    if (g.hook_event_name === 'PostToolUse') {
      if (!g.agent_id) etiket = etiketle(str(g.tool_name), g.tool_input)
    } else if (g.hook_event_name === 'Stop') {
      etiket = '✔ tur bitti'
    }
    if (etiket) {
      const yol = `${dizin}/adimlar.log`
      let satir = 0
      try { satir = readFileSync(yol, 'utf8').split('\n').length } catch { /* yok */ }
      if (satir <= EN_COK_SATIR) appendFileSync(yol, `${Math.floor(Date.now() / 1000)}\t${kisalt(temizle(etiket), 80)}\n`, { mode: 0o600 })
    }
  }
} catch {
  // yutulur
}
process.exit(0)
