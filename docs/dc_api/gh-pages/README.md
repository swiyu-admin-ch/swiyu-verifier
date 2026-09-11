# DC API Testseite – Veröffentlichung via GitHub Pages

Dieser Ordner ist publikationsfertig. Inhalt einfach in ein neues, **öffentliches**
Repository kopieren und GitHub Pages aktivieren.

| Datei | Zweck |
|---|---|
| `index.html` | Die Diagnose-Seite (Kopie von `../dc-api-test.html`) |
| `.nojekyll` | Verhindert die Jekyll-Verarbeitung durch GitHub Pages |

## Schritte

1. Neues öffentliches Repo anlegen, z. B. `dc-api-test`.
2. Inhalt dieses Ordners hineinkopieren und pushen:

   ```bash
   cd /pfad/zum/neuen/repo
   cp /home/gapa/development/swiyu-verifier/docs/dc_api/gh-pages/. . -r
   git add -A && git commit -m "add DC API diagnostic page" && git push
   ```

3. Repo → **Settings → Pages** → Source: *Deploy from a branch*,
   Branch `main`, Ordner `/ (root)` → Save.
4. Nach etwa einer Minute erreichbar unter:

   ```
   https://<github-user>.github.io/dc-api-test/
   ```

## Origin für expected_origins

Die Origin besteht nur aus Schema und Host – **ohne Pfad und ohne Trailing Slash**:

```json
"expected_origins": ["https://<github-user>.github.io"]
```

Nicht `https://<github-user>.github.io/dc-api-test/`.

Zu beachten: Bei `github.io` teilen sich alle Nutzer dieselbe Origin. Für einen POC
ist das unkritisch, für produktionsnahe Tests sollte eine eigene Domain verwendet werden.

## Voraussetzungen auf Verifier-Seite

- Der Verifier Service muss **vom Handy aus über das Internet erreichbar** sein.
  Eine rein intern erreichbare Instanz funktioniert nicht, da die Seite im Browser
  des Telefons läuft.
- `https://<github-user>.github.io` muss in `WebConfig.addCorsMappings()` freigegeben
  sein. Aktuell ist dort nur `https://confluence.bit.admin.ch` hinterlegt – ohne
  Anpassung schlägt bereits der JAR-Abruf mit einem CORS-Fehler fehl.

## Aktualisieren

Nach Änderungen an `../dc-api-test.html`:

```bash
cp ../dc-api-test.html index.html
```

Danach erneut ins Pages-Repo kopieren und pushen.
