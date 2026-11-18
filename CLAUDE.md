# Rei reposu çalışma kuralları

Bu repo uygulama kodu değil, Rei asistanının altyapısıdır: betikler, kurallar, systemd ve launchd dosyaları. Derleme ve otomatik test yoktur, her dosya kendi makinesine kurulur. Önce [docs/kod-haritasi.md](docs/kod-haritasi.md); işletim için [docs/sunucu-kurulum.md](docs/sunucu-kurulum.md).

## Kurulum
- Kurulum yalnız `sunucu/kur.sh` ile: önce `fark`, sonra `kur <repo-yolu>` ya da `kur tumu`.
- Elle kurulacaksa önce kurulu dosyanın özeti karşılaştırılır; kör üstüne yazılmaz.
- Yeni dosya `sunucu/kurulum.tsv`'ye eklenir.
- Sunucuya kurulan commit push'lu main olmalıdır.

## Güvenlik
- Güvenlik sınırı dosyaları (kapılar, sudoers, `managed-settings.json`, Mac kapısı): inceleme effort high, orta üstü bulguda şüpheci.
- `rei.service`'i, canlı tmux oturumunu yeniden başlatan ya da kanalı kesen her adım Yusuf'a sorulur.
- Aynı bot token'ıyla ikinci kanal oturumu açılmaz (409).
- Root, rei'nin yazabildiği yolda dosya açmaz; böyle bir dosya `runuser` ile rei kimliğiyle okunur.
- `rei-push` bu dosyayı (`CLAUDE.md`) korumalı sayar: Mac işinden gelen değişiklik doğrudan main'e gitmez, düğme ister.
