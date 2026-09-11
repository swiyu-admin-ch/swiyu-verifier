DC API Same-Device Verification Flow
Diese Dokumentation beschreibt den vollständigen Same-Device Verifikationsprozess mittels der W3C Digital Credentials (DC) API unter Verwendung von OpenID4VP (Profile openid4vp-v1-signed) und DCQL.

Der Flow ersetzt den klassischen Cross-Device-Ansatz (QR-Code-Scannen / Custom URL Schemes) durch einen sicheren, browser- und betriebssystemintegrierten Ablauf.

Architektur-Übersicht & Rollenverteilung
Auf dem Smartphone des Bürgers (Same-Device) wirken drei Komponenten zusammen:

Business Verifier Web-App: Die geladene Webseite/Frontend der verifizierenden Organisation (z. B. https://behoerde.ch), die in der Browser-Tab-Laufzeit ausgeführt wird.
Browser Engine & DC API: Die neutrale Browser-Laufzeitumgebung des Betriebssystems (z. B. Chrome/Safari / OS Credential Manager), die die Origin fälschungssicher ausliest.
swiyu-Wallet: Die native Wallet-App auf dem Betriebssystem des Endgeräts.
+-----------------------------------------------------------------------+
|                    Holder Smartphone (Same-Device)                    |
|                                                                       |
|  [ Business Verifier Web-App ] <---> [ Browser Engine & DC API ]      |
|     (Frontend / JS in Browser)          (User Agent / OS API)         |
|                 |                                |                    |
|                 |                                v (In-Band OS IPC)   |
|                 |                        [ swiyu-Wallet ]             |
+-----------------|-----------------------------------------------------+
| (HTTPS POST)
v
[ Generic Verifier Backend ] <---> [ Base / Status Registry ]
(Verifier Service & DB)
Schritt 1: Verification Request Creation & JAR-Erstellung
Request-Initiierung (API IF-100):

Der Business Verifier ruft POST /management/api/verifications auf.
DTO-Erweiterung (CreateVerificationManagementDto): Übergabe von dcql_query, response_mode, accepted_issuers sowie dem neuen Feld expected_origins (Array von erlaubten Web-Domains, z. B. ["https://behoerde.ch"]).
Nonce-Generierung & DB-Persistierung:

Der Verifier Service erzeugt eine kryptografisch sichere request_nonce (Replay Protection) und speichert den Sitzungsdatensatz in der Verifier DB (UUID, request_nonce, dcql_query, expected_origins, Status: PENDING).
Request Object Konstruktion & Signierung (JAR nach RFC 9101):

Zusammenstellung des JWT-Payloads mit nonce, dcql_query, client_id (Verifier-DID) und expected_origins.
Sicherheitsrelevanz von expected_origins (OpenID4VP App. A.2): Durch das Einbetten im signierten JAR legt der Verifier kryptografisch fest, welche HTTPS-Domains zur Ausführung berechtigt sind.
Das Request Object wird via HSM oder internem Schlüssel signiert (openid4vp-v1-signed).
Antwort an Business Verifier:

Rückgabe des ManagementResponseDto mit Transaktions-ID, request_nonce und dem signierten JAR-JWT.
Schritt 2: In-Browser Handover via DC API (navigator.credentials.get())
Feature Detection (Support-Prüfung):

Die Verifier-Webseite prüft per Standard-JavaScript (ohne externe Bibliotheken):
if (typeof DigitalCredential !== "undefined" &&
DigitalCredential.userAgentAllowsProtocol("openid4vp-v1-signed")) {
// DC API wird unterstützt
}
Standard-JavaScript API-Aufruf:

Die Web-App führt nach Benutzerinteraktion (transient activation) den nativen Web-API-Call aus:
const credential = await navigator.credentials.get({
digital: {
requests: [{
protocol: "openid4vp-v1-signed",
data: { request: jarString }
}]
}
});
Top-Level Origin Extraction:

Die Browser Engine erfasst fälschungssicher die geladene Web-Domain der Hauptseite (z. B. https://behoerde.ch) als Verifier Origin. Dies bietet Schutz vor Phishing, Man-in-the-Middle-Relay und QR-Code-Stealing.
In-Band OS Transport & Credential Chooser:

Der Browser reicht die Anfrage an die Betriebssystem-Schnittstelle weiter (z. B. Android Credential Manager / iOS Digital Credentials Framework).
Das OS zeigt den nativen Digital Credential Chooser an und übergibt das JAR sowie die verifizierte Browser-Origin über einen gesicherten IPC-Kanal direkt an die registrierte swiyu-Wallet (ohne Deeplinks/Custom-URL-Schemes).
Schritt 3: Holder Processing, Origin Verification & Consent (Wallet)
Signature & Integrity Check:

Die Wallet entpackt das JAR und verifiziert die kryptografische Signatur des Verifiers über dessen Public Key / DID.
Strikte Origin-Prüfung (Wallet Enforcement):

Die Wallet gleicht die vom Betriebssystem übermittelte Browser-Origin gegen die im signierten JAR enthaltenen expected_origins ab: Browser-Origin ∈ expected_origins
Bei Mismatch oder ungültiger Signatur: Die Wallet bricht die Transaktion sofort ab (invalid_request).
DCQL-Auswertung & User Consent:

Die Wallet wertet die DCQL-Abfrage aus, wählt die passenden Verifiable Credentials (SD-JWT) aus und zeigt dem Bürger den Zustimmungsdialog (Consent-Prompt).
Erstellung der VP & Origin Binding im Key Binding JWT:

Nach Bestätigung erstellt und signiert die Wallet die Verifiable Presentation (VP).
Origin-Binding: Im Key Binding JWT (KB-JWT) setzt die Wallet als Audience zwingend die Web-Origin mit dem Präfix origin:: aud = "origin:https://behoerde.ch"
Promise-Resolution:

Die Wallet übergibt das Ergebnis als aufgelöstes Promise (DigitalCredential) über das Betriebssystem an das JavaScript der Web-App zurück.
Schritt 4: Submission & Synchronous Verification (Generic Verifier)
Browser-Submit an dedizierte Route:

Die Web-App sendet das empfangene DigitalCredential per HTTP POST an die neue, dedizierte Schnittstelle des Verifier Service: POST /oid4vp/api/dc-api/v1/request-object/{request_id}/response-data
Session-Fetch & Context-Correlation:

Der Verifier Service lädt den Transaktions-Datensatz aus der Verifier DB (Abruf von request_nonce und verifier_web_origin).
Strikte Origin- & KB-JWT-Validierung:

Nonce-Prüfung: nonce im KB-JWT == request_nonce aus der DB.
Verifier Origin Enforcement: Prüfe, ob im KB-JWT der Audience-Claim korrekt auf die eigene Web-Domain gesetzt ist: aud == "origin:" + VerifierWebOrigin
Kryptografische VC-Validierung:

Matching der gelieferten Claims gegen die ursprüngliche DCQL-Query.
Abruf des Issuer Public Keys aus der Base Registry zur Signaturprüfung.
Abruf des Widerrufsstatus aus der Status Registry (Token Status List / TSL).
Synchrone Antwort (Kein Polling erforderlich):

Der Verifier Service aktualisiert den Status in der Verifier DB auf SUCCESS (oder FAILED) und antwortet direkt synchron mit HTTP 200 OK (inkl. verifizierter Claims).
Der aus dem Cross-Device-Flow bekannte Polling-Loop entfällt beim Same-Device DC API Flow vollständig.