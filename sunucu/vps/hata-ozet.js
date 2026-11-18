// Diskort gözcüsü için salt okunur hata özeti. Sunucudaki api kabında stdin'den çalıştırılır:
//   ssh ... "cd /opt/diskort/infra && docker compose exec -T api node - <sinceMs>" < hata-ozet.js
// Çıktı: tek satır JSON { until, groups: [...], incidents: [...] }
const fs = require('fs');
const since = Number(process.argv[2] || process.argv[1] || 0) || 0;
const read = (f) => {
  try {
    return fs.readFileSync(f, 'utf8').split('\n').filter(Boolean).map((l) => { try { return JSON.parse(l); } catch { return null; } }).filter(Boolean);
  } catch { return []; }
};
const norm = (s) => String(s || '').replace(/\{.*$/s, '').replace(/[0-9a-f]{8,}|\d+([.,]\d+)?/gi, '#').slice(0, 80).trim();
let until = since;
const groups = new Map();
for (const [file, kind] of [['/data/client-errors.jsonl', 'istemci'], ['/data/server-errors.jsonl', 'sunucu']]) {
  for (const e of read(file)) {
    if (!(e.at > since)) continue;
    until = Math.max(until, e.at);
    const key = kind + '|' + (e.where || '') + '|' + norm(e.message);
    const g = groups.get(key) || { kind, where: e.where || '', message: norm(e.message), count: 0, users: new Set(), versions: new Set(), first: e.at, last: e.at, sample: String(e.message || '').slice(0, 300) };
    g.count++;
    if (e.user) g.users.add(e.user);
    g.versions.add((e.platform || '?') + '@' + (e.version || '?'));
    g.first = Math.min(g.first, e.at);
    g.last = Math.max(g.last, e.at);
    groups.set(key, g);
  }
}
const incidents = new Map();
for (const e of read('/data/telemetry/incidents.jsonl')) {
  const t = e.end || e.start || 0;
  if (!(t > since)) continue;
  until = Math.max(until, t);
  const i = incidents.get(e.userId) || { userId: e.userId, count: 0, causes: new Set(), platform: (e.platform || '?') + '@' + (e.version || '?') };
  i.count++;
  for (const c of e.causes || []) i.causes.add(c.cause);
  incidents.set(e.userId, i);
}
const freezes = [];
for (const e of read('/data/telemetry/freeze-events.jsonl')) {
  if (!(e.end > since)) continue;
  until = Math.max(until, e.end);
  freezes.push({ start: e.start, end: e.end, label: e.label, affected: e.affected, probe: e.probe, evidence: e.evidence });
}
const iso = (t) => new Date(t).toISOString();
let logBytes = null;
try { logBytes = fs.statSync('/data/client-errors.jsonl').size; } catch {}
process.stdout.write(JSON.stringify({
  until,
  logBytes,
  freezes,
  groups: [...groups.values()].map((g) => ({ ...g, users: [...g.users], versions: [...g.versions], first: iso(g.first), last: iso(g.last) })),
  incidents: [...incidents.values()].map((i) => ({ ...i, causes: [...i.causes] })),
}) + '\n');
