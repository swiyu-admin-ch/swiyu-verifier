# DC API Diagnose-Testseite

`dc-api-test.html` prüft schrittweise, ob der Same-Device-Flow über die
W3C Digital Credentials API auf einem Gerät funktioniert.

## Was die Seite prüft

**Abschnitt 1 – Umgebung** (läuft automatisch beim Laden)

| Prüfung | Bedeutung bei Fehlschlag |
|---|---|
| Secure Context | Seite läuft nicht über HTTPS/localhost → DC API grundsätzlich nicht verfügbar |
| `navigator.credentials` | Credential Management API fehlt komplett |
| `DigitalCredential` | DC API nicht unterstützt oder hinter einem Browser-Flag deaktiviert |
| `userAgentAllowsProtocol()` | Protokoll `openid4vp-v1-signed` wird abgelehnt |
| Plattform | Warnung, wenn nicht Android + Chromium |

**Abschnitt 3 – Ablauf** (nach Klick)

- JAR-Abruf über die `verification_url` inkl. CORS-Diagnose
- Dekodierung des JAR (ohne Signaturprüfung, nur zur Inspektion)
- `response_mode` ist `dc_api` bzw. `dc_api.jwt`
- `expected_origins` enthält die Origin der Testseite
- `response_uri` ist weggelassen
- `exp` noch gültig, `nonce` vorhanden
- Aufruf von `navigator.credentials.get()` mit Fehlerklassifikation

## Verwendung

1. Verification mit `response_mode: "dc_api"` anlegen und `expected_origins` auf die
   Origin setzen, unter der die Testseite ausgeliefert wird.
2. Seite über HTTPS bereitstellen (Tunnel oder Webserver):

   ```bash
   # lokal ausliefern
   python3 -m http.server 8000 --directory docs/dc_api

   # HTTPS-Origin bereitstellen
   cloudflared tunnel --url http://localhost:8000
   # oder
   ngrok http 8000
   ```

3. Die vom Tunnel vergebene HTTPS-URL auf dem Android-Gerät öffnen.
4. `verification_url` aus dem Management-Response eintragen.
5. **Button A** prüft nur Backend und CORS – dafür ist keine Wallet nötig.
   **Button B** startet zusätzlich den DC-API-Aufruf.

## Debugging auf dem Gerät

Gerät per USB anschliessen, im Desktop-Chrome `chrome://inspect` öffnen und die
Remote-DevTools auf den Tab richten. Nur dort ist die tatsächliche Exception sichtbar.

## Bekannte Stolpersteine

- **Tunnel-Domain wechselt** bei jedem Neustart (v. a. bei ngrok) – `expected_origins`
  muss danach für neue Verifications angepasst werden.
- **CORS**: `WebConfig.addCorsMappings()` erlaubt derzeit nur eine einzige fest
  hinterlegte Origin. Ohne Erweiterung schlägt bereits Button A fehl.
- **Keine registrierte Wallet**: Ohne eine App, die sich unter Android als
  Digital-Credential-Provider registriert hat, endet Button B mit `NotAllowedError`.
- **Transient Activation**: Der Aufruf muss aus einem Klick-Handler erfolgen.

## Abgrenzung

Die Seite deckt die Schritte 1–3 des Flows aus `dc_api_same_device_19.puml` ab.
Das Absenden der Response (Schritt 4) ist nicht implementiert, da der dafür
vorgesehene Endpoint im Verifier Service noch nicht existiert.
