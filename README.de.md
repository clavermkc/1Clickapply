# 1ClickApply

*[Version française](README.md)*

Plattform für automatisiertes Sourcing und Initiativbewerbungen, die auf Tech-/IT-Unternehmen in Niedersachsen und angrenzenden Regionen (Bremen, Hamburg) abzielt.

Langfristiges Ziel: Unternehmen über OpenStreetMap extrahieren, deren Adressen und HR-E-Mails ermitteln, individuelle Bewerbungsschreiben per LLM generieren, als PDF zusammenstellen und in kontrollierten Batches versenden.

> ⚠️ **Dieses Repository befindet sich in Arbeit.** Bisher ist ausschließlich das Sourcing-/Scraping-Modul implementiert. Siehe Abschnitt [Einschränkungen der aktuellen Version](#einschränkungen-der-aktuellen-version), bevor Sie das Verhalten der App bewerten.

## Aktueller Projektstand

### Implementiert

| Modul | Beschreibung | Status |
|---|---|---|
| **Sourcing** (`service/SourcingService`) | Fragt die Overpass-API (OpenStreetMap) ab, um Unternehmen (`office=it`, `office=company`, `office=coworking`, `shop=computer`) in einer angegebenen Stadt zu finden, und speichert sie in der Datenbank. | ✅ |
| **Scraping** (`service/ScraperService`) | Besucht die Website jedes Unternehmens (Startseite + bis zu 3 Unterseiten wie `karriere`, `impressum`, `kontakt` ...) über Jsoup und versucht, eine HR-E-Mail sowie einen Ansprechpartner zu extrahieren. | ✅ (eingeschränkte Ergebnisse, siehe unten) |
| **Persistenz** (`domain`, `repository`) | JPA-Entitäten `Company` und `CoverLetter` mit Lebenszyklus-Status (`ApplicationStatus`), PostgreSQL-Datenbank (Neon). | ✅ |

### Noch nicht implementiert

- **KI-/Ollama-Modul (Spring AI)** — kein `OllamaChatModel`-Connector, kein Service zur Umformulierung von Bewerbungsschreiben. In `build.gradle` ist lediglich die Abhängigkeit `spring-ai-jsoup-document-reader` vorhanden, als Scraping-/Parsing-Werkzeug, nicht für KI.
- **PDF-/LaTeX-Modul** — kein `LatexTemplateEngine`, `LatexSanitizerUtils` oder `PdfCompilerService`. Es findet keine `pdflatex`-Kompilierung statt.
- **Delivery-/Anti-Spam-Modul** — kein SMTP-Versand, kein Rate Limiting.
- **REST-API** — es existiert noch kein Controller (`@RestController`); das Projekt wird derzeit nur lokal über `TestSourcingRunner` genutzt (ein `CommandLineRunner`, der beim Anwendungsstart ausgeführt wird).
- **Frontend** — kein Next.js-Ordner in diesem Repository.

Die Zielarchitektur (mit KI, PDF und Delivery) ist weiterhin in `HELP.md` (nicht versioniert) als Spezifikation dokumentiert, spiegelt aber noch nicht den tatsächlichen Code wider.

## Einschränkungen der aktuellen Version

- **Sehr niedrige E-Mail-Extraktionsrate**: Bei einem Durchlauf von rund 400 gescrapten Unternehmen konnte der Service nur bei **3 bis 5 Unternehmen** eine verwertbare E-Mail-Adresse extrahieren. Wahrscheinliche Ursachen: Viele Websites zeigen keine E-Mail im Klartext im HTML (Kontaktformulare, Bilder, fortgeschrittene Verschleierung, Anti-Bot-Schutz); das Crawling ist auf die Startseite + 3 Unterseiten begrenzt; es gibt keinen JavaScript-Fallback (kein Playwright, trotz entsprechender Erwähnung in `HELP.md`).
- **Kein JS-Rendering**: Der Scraper verwendet ausschließlich Jsoup (statisches HTML); Single-Page-Applications (React/Vue/Angular clientseitig) werden nicht korrekt gescrapt.
- **Keine erweiterte Deduplizierung**: Die Deduplizierung erfolgt ausschließlich anhand des Domainnamens.
- **`TestSourcingRunner` läuft bei jedem Start** der Anwendung (`CommandLineRunner`), wodurch automatisch ein Sourcing- + Scraping-Zyklus für `Hannover` ausgelöst wird — praktisch zum Testen, sollte aber vor jedem Produktiveinsatz entfernt werden.

## Technologie-Stack

- **Backend**: Java 21, Spring Boot 4.1.1, Spring Data JPA, Spring AI BOM 2.0.0 (aktuell nur für `spring-ai-jsoup-document-reader` genutzt).
- **Scraping**: Jsoup.
- **Daten-Sourcing**: Overpass API (OpenStreetMap).
- **Datenbank**: PostgreSQL, gehostet auf [Neon](https://neon.com/).
- **Build**: Gradle (Wrapper enthalten).

## Konfiguration

Das Projekt liest seine Datenbankkonfiguration aus Umgebungsvariablen (über `spring-dotenv`), die aus einer `.env`-Datei im Root-Verzeichnis geladen werden (nicht versioniert). Je nach Präferenz stehen zwei Optionen zur Verfügung.

### Option A — Cloud-Datenbank (Neon, Supabase usw.)

Dies ist die Standardkonfiguration des Projekts. Erstellen Sie eine `.env`-Datei nach folgendem Muster:

```dotenv
PGHOST=<vom-provider-bereitgestellter-host>
PGDATABASE=<datenbankname>
PGUSER=<benutzername>
PGPASSWORD=<passwort>
PGSSLMODE=require
PGCHANNELBINDING=require
NEON_DB_PORT=5432
NEON_DB_URL="jdbc:postgresql://${PGHOST}:${NEON_DB_PORT}/${PGDATABASE}?sslmode=require&channel_binding=require"
```

Diese Variablen werden in `src/main/resources/application.properties` verwendet:

```properties
spring.datasource.url=${NEON_DB_URL}
spring.datasource.username=${PGUSER}
spring.datasource.password=${PGPASSWORD}
```

Das funktioniert sowohl mit [Neon](https://neon.com/) als auch mit [Supabase](https://supabase.com/) oder jeder anderen verwalteten PostgreSQL-Instanz: Es genügt, JDBC-URL, Benutzername und Passwort des jeweiligen Anbieters einzutragen.

### Option B — Lokale Datenbank via Docker

Eine `compose.yaml` liegt bei, ist aber **standardmäßig auskommentiert** (das Projekt nutzt standardmäßig die Cloud-Datenbank). So aktivieren Sie sie:

1. Kommentieren Sie den `postgres`-Service in `compose.yaml` ein:

   ```yaml
   services:
     postgres:
       image: postgres:latest
       container_name: oneclickapply-db
       environment:
         POSTGRES_DB: oneclickapply_db
         POSTGRES_USER: oneclick_user
         POSTGRES_PASSWORD: oneclick_pass
       ports:
         - "5432:5432"
       volumes:
         - postgres_data:/var/lib/postgresql/data
   ```

2. Starten Sie den Container: `docker compose up -d`.
3. Kommentieren Sie in `src/main/resources/application.properties` die Zeilen für die lokale Verbindung ein und kommentieren Sie die Zeilen `${NEON_DB_URL}` / `${PGUSER}` / `${PGPASSWORD}` aus (oder entfernen Sie sie):

   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/oneclickapply_db
   spring.datasource.username=oneclick_user
   spring.datasource.password=oneclick_pass
   ```

   (Das Projekt hängt außerdem von `spring-boot-docker-compose` ab, das die in `compose.yaml` definierten Services beim Anwendungsstart automatisch starten kann, sofern diese aktiv ist.)

Die vollständige Liste der Voraussetzungen finden Sie in `REQUIREMENTS.de.md`.

## Projekt starten

```bash
./gradlew bootRun
```

Beim Start führt `TestSourcingRunner` automatisch Folgendes aus:
1. Eine Overpass-Erfassung für die Stadt `Hannover`.
2. Ein Scraping der ersten 3 in der Datenbank gefundenen Unternehmen für diese Stadt.
3. Eine Log-Ausgabe des Ergebnisses (gefundene oder nicht gefundene E-Mail und Kontaktperson).

## Roadmap

- [ ] Verbesserung der E-Mail-Extraktionsrate (JavaScript-/Playwright-Fallback, Umgang mit Verschleierung, Kontaktformulare).
- [ ] Implementierung des KI-Moduls (Ollama + Spring AI) zur Umformulierung von Bewerbungsschreiben.
- [ ] Implementierung des PDF-/LaTeX-Moduls.
- [ ] Implementierung des Versandmoduls (SMTP + Anti-Spam/Rate Limiting).
- [ ] Bereitstellung einer REST-API.
- [ ] Next.js-Frontend.
