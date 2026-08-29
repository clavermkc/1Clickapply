# Voraussetzungen (Requirements)

*[Version française](REQUIREMENTS.md)*

Dieses Projekt ist eine **Java-/Gradle-Anwendung** (keine Python-`requirements.txt`). Dieses Dokument listet auf, was zum Ausführen der aktuellen Projektversion notwendig ist, sowie was für die noch nicht implementierten Module benötigt werden wird.

## Notwendig für die aktuelle Version (Sourcing + Scraping)

| Voraussetzung | Details |
|---|---|
| **JDK 21** | Von `build.gradle` gefordert (`JavaLanguageVersion.of(21)`). Der Gradle-Wrapper kann es über ein Toolchain herunterladen, falls nicht lokal installiert. |
| **Gradle** | Wird über den Wrapper (`./gradlew`) bereitgestellt, keine manuelle Installation nötig. |
| **PostgreSQL-Datenbank** | Zwei Optionen: (1) ein Cloud-Anbieter wie [Neon](https://neon.com/) oder [Supabase](https://supabase.com/) — Standardkonfiguration des Projekts, oder (2) lokales PostgreSQL via Docker (`compose.yaml`, Service standardmäßig auskommentiert, muss einkommentiert werden). Siehe Abschnitt *Konfiguration* in `README.de.md`. |
| **`.env`-Datei** | Nur für die Cloud-Option erforderlich. Muss die Variablen `PGHOST`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`, `PGSSLMODE`, `PGCHANNELBINDING`, `NEON_DB_PORT`, `NEON_DB_URL` enthalten (siehe `README.de.md`). Wird über `spring-dotenv` geladen. |
| **Docker** *(optional)* | Nur erforderlich, wenn Sie die lokale Datenbank über `compose.yaml` nutzen. |
| **Ausgehender Netzwerkzugriff** | Zu `overpass-api.de` (OSM-Sourcing) und zu den Websites der Zielunternehmen (Scraping). |

Wichtigste Gradle-Abhängigkeiten (siehe `build.gradle`):
- `org.springframework.boot:spring-boot-starter-webmvc`
- `org.springframework.boot:spring-boot-starter-data-jpa`
- `org.springframework.ai:spring-ai-jsoup-document-reader`
- `org.postgresql:postgresql`
- `org.projectlombok:lombok`
- `me.paulschwarz:spring-dotenv-bom`

## Aktuell nicht notwendig

Diese Elemente werden in der Zielspezifikation des Projekts (`HELP.md`) erwähnt, sind aber **derzeit nicht erforderlich**, da die entsprechenden Module nicht implementiert sind:

- ~~Ollama (lokales LLM, z. B. `llama3.2`/`mistral`)~~ — KI-Modul nicht implementiert.
- ~~LaTeX-Distribution / `pdflatex`~~ — PDF-Modul nicht implementiert.
- ~~SMTP-Server~~ — Versandmodul nicht implementiert.
- ~~Node.js / Next.js~~ — kein Frontend in diesem Repository.
- ~~Playwright~~ — in der Spezifikation als JS-Fallback erwähnt, aber nicht implementiert (der aktuelle Scraper nutzt nur Jsoup, daher werden SPA-Websites nicht korrekt gescrapt — siehe Einschränkungen in `README.de.md`).

Sie werden nach und nach hinzugefügt, sobald die KI-, PDF- und Delivery-Module implementiert werden.

## Tests ausführen

```bash
./gradlew test
```

## Build

```bash
./gradlew build
```
