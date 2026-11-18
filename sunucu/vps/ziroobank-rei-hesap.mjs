// Bir kez, ana VPS'te root olarak: Ziroobank'ta `rei` operatör hesabını açar (ödeme yapan değil),
// şifreyi yalnız stdout'a basar; çağıran kabuk onu /etc/rei/ziroobank.env'e yazar, ekrana düşmez.
// Çalıştırma: docker exec -i ziroobank node --input-type=module - < bu-dosya
import { openDb, audit, nextColor } from '/app/src/db.js';
import { hashPassword, generatePassword } from '/app/src/auth.js';
const db = openDb();
if (db.prepare('SELECT 1 FROM users WHERE username = ?').get('rei')) { console.error('rei zaten var'); process.exit(3); }
const pw = generatePassword();
const hash = hashPassword(pw);
db.prepare('INSERT INTO users (username, display_name, password_hash, role, is_payer, active, color) VALUES (?,?,?,?,?,1,?)')
  .run('rei', 'Rei', hash, 'operator', 0, nextColor(db));
audit(db, null, 'user.create', 'user:rei', null, { username: 'rei', display_name: 'Rei', role: 'operator', is_payer: false, by: 'ziroobank-rei-hesap.mjs' });
db.close();
process.stdout.write(pw);
