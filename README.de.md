# 1ClickApply
*[Version française](README.md)*

Kommandozeilen-Pipeline für Initiativbewerbungen (Werkstudent Softwareentwicklung) bei Tech-Unternehmen in Niedersachsen und Bremen.

Die Pipeline:
1. findet Unternehmen über OpenStreetMap (Overpass) und über die Jobsuche-API der Bundesagentur für Arbeit;
2. besucht deren öffentliche Website, um eine Kontakt-E-Mail zu extrahieren, den Tech-Stack zu erkennen und einen Score zu berechnen;
3. erzeugt aus einer Vorlage eine personalisierte Mail;
4. exportiert die Entwürfe in eine CSV-Datei zur **manuellen Prüfung**;
5. versendet nur die freigegebenen Mails über Gmail, mit angehängtem Lebenslauf, einem Tageslimit und einer Pause zwischen zwei Mails.

Die Anleitung für den täglichen Ablauf steht in [`PIPELINE.md`](PIPELINE.md) (auf Französisch).

## Stack

| | |
|---|---|
| Sprache | Java 21 (Gradle-Toolchain) |
| Framework | Spring Boot 4.1.1: Data JPA, Mail, Actuator, Web MVC (Webserver deaktiviert: `spring.main.web-application-type=none`) |
| Scraping | Jsoup (über `spring-ai-jsoup-document-reader`, Spring AI BOM 2.0.0). Es wird kein LLM verwendet. |
| Weitere Bibliotheken | Apache Commons CSV, Lombok, `spring-dotenv` |
| Datenbank | PostgreSQL (standardmäßig [Neon](https://neon.com/)), Schema wird von Hibernate verwaltet (`ddl-auto=update`) |
| Build | Gradle 9.7 (Wrapper `./gradlew`) |
| Container | `Dockerfile` (nur Ausführung, das Jar wird lokal gebaut) + `compose.yaml` (ein Service pro Schritt) |

## Voraussetzungen

- JDK 21 (die Gradle-Toolchain kann es herunterladen)
- Eine erreichbare PostgreSQL-Datenbank (Neon, Supabase oder lokal)
- Ein Gmail-Konto mit Bestätigung in zwei Schritten und einem [App-Passwort](https://myaccount.google.com/apppasswords), nur für `--mode=send` nötig
- Ein Lebenslauf als PDF, der jeder Mail angehängt wird
- Ausgehender Netzwerkzugriff auf `overpass-api.de`, `rest.arbeitsagentur.de`, die Websites der Unternehmen und `smtp.gmail.com:587`
- Docker *(optional)*

Siehe auch [`REQUIREMENTS.de.md`](REQUIREMENTS.de.md).

## Umgebungsvariablen

Sie werden aus einer `.env`-Datei im Projektverzeichnis gelesen (`spring.config.import=optional:file:.env[.properties]` + `spring-dotenv`). Diese Datei wird von git ignoriert und ist vom Docker-Image ausgeschlossen.

| Variable | Erforderlich | Verwendung |
|---|---|---|
| `NEON_DB_URL` | ja | JDBC-URL, z. B. `jdbc:postgresql://<host>:5432/<db>?sslmode=require&channel_binding=require` |
| `PGUSER` | ja | Datenbankbenutzer |
| `PGPASSWORD` | ja | Datenbankpasswort |
| `GMAIL_USER` | ja | Absenderadresse (`spring.mail.username`, `outreach.sender-email`) |
| `GMAIL_APP_PASSWORD` | für `send` | Gmail-App-Passwort |
| `CV_PATH` | für `send` | Pfad zum Lebenslauf-PDF (Standard `./CV_Claver.pdf`). Mit Docker: `CV/<datei>.pdf` |
| `PGHOST`, `PGDATABASE`, `PGSSLMODE`, `PGCHANNELBINDING`, `NEON_DB_PORT` | nein | Dienen nur zum Zusammensetzen von `NEON_DB_URL` |

Jede `outreach.*`-Eigenschaft kann auch per Umgebungsvariable überschrieben werden, z. B. `OUTREACH_REVIEW_CSV_PATH` oder `OUTREACH_FOLLOW_UP_AFTER_DAYS`, wie in `compose.yaml`.

### Kampagnenparameter (`application.properties`, Präfix `outreach.`)

| Eigenschaft | Aktueller Wert | Bedeutung |
|---|---|---|
| `cities` | Bremen, Hannover, Oldenburg, Osnabrück, … | Städte, die `--mode=source` abfragt |
| `ba-queries` | Werkstudent Java, Spring Boot, Kotlin, … | Suchanfragen an die Bundesagentur |
| `min-score` | `1` | Mindestscore, um einen Entwurf zu erzeugen |
| `daily-limit` | `40` | Maximale Anzahl Mails pro Tag, Nachfassmails eingeschlossen |
| `min-delay-seconds` / `max-delay-seconds` | `300` / `600` | Zufällige Pause zwischen zwei Mails |
| `follow-up-after-days` | `9999` | Wartezeit vor einer Nachfassmail (`9999` = Nachfassen deaktiviert) |
| `scrape-delay-millis` | `1000` | Pause zwischen zwei Scraping-Anfragen |
| `cv-path`, `review-csv-path` | `${CV_PATH}`, `./data/review.csv` | Verwendete Dateien |
| `sender-name`, `phone`, `portfolio-url`, `github-url`, `linkedin-url` | — | Signatur der Mails |

## Installation

```bash
git clone <repo> && cd 1Clickapply
# .env-Datei anlegen (siehe oben)
mkdir -p CV data && cp /pfad/zu/meinem_lebenslauf.pdf CV/
./gradlew build
```

### Lokale Datenbank (optional)

Die aktuelle `compose.yaml` enthält keinen PostgreSQL-Service mehr. Für eine lokale Datenbank starten Sie selbst einen Postgres-Container, zum Beispiel:

```bash
docker run -d --name oneclickapply-db -p 5432:5432 \
  -e POSTGRES_DB=oneclickapply_db -e POSTGRES_USER=oneclick_user -e POSTGRES_PASSWORD=oneclick_pass postgres
```

Aktivieren Sie anschließend in `application.properties` die (derzeit auskommentierten) `localhost`-Zeilen anstelle der `${NEON_DB_URL}`-Zeilen.

## Verwendung

Der Einstiegspunkt ist `Application.java`. Die gesamte Pipeline läuft über `runner/PipelineRunner`, der den Schritt anhand des Arguments `--mode` auswählt:

```bash
./gradlew bootRun --args="--mode=<modus> [optionen]"
# oder, nach ./gradlew bootJar:
java -jar build/libs/1clickapply-0.0.1-SNAPSHOT.jar --mode=<modus> [optionen]
```

| Modus | Wirkung |
|---|---|
| `source` | Holt OSM-Unternehmen (`office=it`, `office=company`, …) für die Städte aus `outreach.cities`, mit 5 s Pause zwischen zwei Städten |
| `source-ba` | Fügt Arbeitgeber hinzu, die Tech-Stellen bei der Bundesagentur für Arbeit ausschreiben |
| `scrape` | Für noch nicht gescrapte Unternehmen: extrahiert eine E-Mail, erkennt den Stack, berechnet den Score, sucht eine Werkstudenten-Seite |
| `test-scrape --url=<url>` | Startet den Scraper im Debug-Modus für eine einzelne URL |
| `stats` | Zeigt Zähler an: Unternehmen, ohne Website, gescrapt, mit E-Mail, über dem Score |
| `draft` | Erzeugt einen Entwurf (`DRAFT`) pro Unternehmen mit E-Mail und Score ≥ `min-score` |
| `export` | Schreibt `data/review.csv` (Trennzeichen `;`, UTF-8) |
| `import` | Liest `review.csv` wieder ein: `x` → `APPROVED`, `n` → `SKIPPED` |
| `send [--dry-run]` | Versendet die `APPROVED`-Mails im Rahmen des Tageslimits. `--dry-run` zeigt nur an, was versendet würde |
| `optout --email=… \| --domain=…` | Markiert das Unternehmen als Opt-out und löscht dessen E-Mail und Kontakt |
| *(keiner)* / `help` | Zeigt die Hilfe an |

Optionen: `--city=Bremen` (beschränkt `source`, `source-ba` und `scrape` auf eine Stadt), `--limit=50` (maximale Anzahl zu scrapender Unternehmen, Standard 200).

Spalten von `review.csv`: `approve; id; company; city; website; score; stack; werkstudent_page; recipient; subject; body`. Die Spalten `recipient`, `subject` und `body` können vor dem Import korrigiert werden.

### Mit Docker

```bash
./gradlew bootJar -x test          # das Dockerfile kopiert das bereits gebaute Jar
docker compose build
docker compose run --rm source --mode=source --city=Hamburg
docker compose run --rm scrape
docker compose run --rm draft
docker compose run --rm export     # -> ./data/review.csv
docker compose run --rm import
docker compose run --rm send --mode=send --dry-run
docker compose run --rm send
```

Die Services liegen im Profil `pipeline`, ein einfaches `docker compose up` startet also nichts. `./CV` wird schreibgeschützt eingebunden, `./data` wird mit dem Host geteilt.

## Gradle-Tasks

| Befehl | Wirkung |
|---|---|
| `./gradlew bootRun --args="…"` | Startet die Anwendung |
| `./gradlew build` | Kompiliert und führt die Tests aus |
| `./gradlew bootJar` | Erzeugt `build/libs/1clickapply-0.0.1-SNAPSHOT.jar` |
| `./gradlew test` | Führt die Tests aus (JUnit 5) |

## Tests

```bash
./gradlew test
./gradlew test --tests '*ScraperServiceTest'
```

- `service/ScraperServiceTest`: Offline-Unit-Tests des Scrapers (verschleierte oder durch Cloudflare geschützte E-Mails, Priorität von HR-Adressen, Java/JavaScript-Scoring, Parsing von `robots.txt`).
- `ApplicationTests`: Kontext-Ladetest, **deaktiviert** (`@Disabled`), da er eine echte Datenbank und die `.env` benötigt.

## Projektstruktur

```
├── build.gradle, settings.gradle, gradlew   # Gradle-Build
├── Dockerfile, .dockerignore, compose.yaml  # Laufzeit-Image + ein Service pro Schritt
├── PIPELINE.md                              # täglicher Ablauf und rechtliche Schutzmaßnahmen
├── REQUIREMENTS.md / *.de.md                # Voraussetzungen, deutsche Übersetzungen
├── Terms.md                                 # Nutzungsbedingungen
├── CV/, data/, .env                         # nur lokal (von git ignoriert)
└── src/
    ├── main/java/com/claveri/Oneclickapply/
    │   ├── Application.java                 # Spring-Boot-Einstiegspunkt
    │   ├── runner/PipelineRunner.java       # Verteilung nach --mode
    │   ├── config/OutreachProperties.java   # Eigenschaften outreach.*
    │   ├── domain/                          # JPA-Entitäten Company, CoverLetter, Enum ApplicationStatus
    │   ├── dto/                             # Antworten von Overpass und Bundesagentur
    │   ├── repository/                      # Spring-Data-Repositories
    │   └── service/
    │       ├── SourcingService.java         # Overpass / OSM
    │       ├── BundesagenturService.java    # Jobsuche-API der Bundesagentur für Arbeit
    │       ├── ScraperService.java          # Jsoup, robots.txt, E-Mail, Stack, Score
    │       ├── DraftService.java            # Vorlagen für Mail und Nachfassmail
    │       ├── ReviewCsvService.java        # Export/Import von review.csv
    │       └── MailService.java             # Gmail-SMTP-Versand, Kontingent, Pausen
    ├── main/resources/application.properties
    └── test/java/…                          # ApplicationTests, ScraperServiceTest
```

## Schutzmaßnahmen

Zusammenfassung; Details in [`PIPELINE.md`](PIPELINE.md):

- Beachtung von `robots.txt`, User-Agent, der den Bot mit einer Kontaktadresse ausweist, höchstens 7 öffentliche Seiten pro Website, 1 s zwischen zwei Anfragen;
- keine Mail ohne `x` in `review.csv`, und nur eine Bewerbung pro Unternehmen (eindeutige Domain);
- Adressen wie `noreply@`, `datenschutz@`, `support@` usw. werden ausgeschlossen;
- Opt-out über `--mode=optout`, das die Kontaktadresse löscht.

## Bekannte Einschränkungen

- Der Scraper liest nur statisches HTML: Websites, die ihren Inhalt per JavaScript erzeugen (SPA), werden schlecht erfasst, und viele Websites veröffentlichen keine E-Mail im Klartext.
- Der Mailtext stammt aus fest in `DraftService` hinterlegten Vorlagen. Es gibt weder KI-Generierung noch ein Anschreiben als PDF; der Lebenslauf wird unverändert angehängt.
- Die Absenderdaten (Name, Telefon, Links) und die Kontaktadresse des User-Agents sind fest in `application.properties` sowie in `SourcingService` / `ScraperService` hinterlegt. Ein Fork muss sie ersetzen.
- Die Javadoc von `PipelineRunner` und `PIPELINE.md` sprechen von „30 Mails/Tag“ und Nachfassen nach 7 Tagen, die aktuelle Konfiguration ist jedoch `daily-limit=40` mit deaktiviertem Nachfassen.
- Keine REST-API und kein Frontend.

## Roadmap

- [ ] TODO: E-Mail-Extraktionsrate verbessern (JavaScript-Rendering, Verschleierung)
- [ ] TODO: persönliche Absenderdaten aus dem Code und der versionierten Konfiguration entfernen

## Lizenz

TODO: Das Repository enthält keine `LICENSE`-Datei, daher wird derzeit keine Open-Source-Lizenz gewährt. Die Nutzungsbedingungen stehen in [`Terms.md`](Terms.md).
