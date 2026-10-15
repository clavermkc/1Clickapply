# Voraussetzungen (Requirements)

*[Version française](REQUIREMENTS.md)*

Dieses Projekt ist eine **Java-21-/Spring-Boot**-Kommandozeilenanwendung. Dieses Dokument listet auf, was zum Ausführen der gesamten Pipeline (Sourcing, Scraping, Entwürfe, CSV-Prüfung, Versand) notwendig ist.

## Notwendig

| Voraussetzung | Details |
|---|---|
| **JDK 21** | Von `build.gradle` gefordert (`JavaLanguageVersion.of(21)`). Die Gradle-Toolchain kann es herunterladen, falls es nicht lokal installiert ist. |
| **Gradle** | Wird über den Wrapper (`./gradlew`, Gradle 9.7) bereitgestellt, keine manuelle Installation nötig. |
| **PostgreSQL-Datenbank** | Ein Cloud-Anbieter wie [Neon](https://neon.com/) (Standardkonfiguration) oder [Supabase](https://supabase.com/), oder ein lokales PostgreSQL (siehe *Lokale Datenbank* in `README.de.md`). Das Schema wird von Hibernate angelegt (`ddl-auto=update`). |
| **`.env`-Datei** | Im Projektverzeichnis, von git ignoriert. Muss mindestens `NEON_DB_URL`, `PGUSER`, `PGPASSWORD`, `GMAIL_USER` enthalten. Wird über `spring-dotenv` geladen. Siehe die Variablentabelle in `README.de.md`. |
| **Gmail-Konto + App-Passwort** | Nur für `--mode=send` erforderlich (SMTP `smtp.gmail.com:587`). Für das Konto muss die Bestätigung in zwei Schritten aktiviert sein, um ein [App-Passwort](https://myaccount.google.com/apppasswords) (`GMAIL_APP_PASSWORD`) zu erzeugen. |
| **Lebenslauf als PDF** | Wird jeder versendeten Mail angehängt. Pfad über `CV_PATH` (mit Docker: in `./CV/` ablegen). |
| **Ausgehender Netzwerkzugriff** | Zu `overpass-api.de` (OSM-Sourcing), `rest.arbeitsagentur.de` (Bundesagentur für Arbeit), den Websites der Unternehmen (Scraping) und `smtp.gmail.com:587` (Versand). |
| **Docker** *(optional)* | Um die Schritte über `compose.yaml` zu starten (ein Service pro Schritt, Profil `pipeline`). Das Jar muss vorher mit `./gradlew bootJar -x test` gebaut werden. |

Wichtigste Gradle-Abhängigkeiten (siehe `build.gradle`):
- `org.springframework.boot:spring-boot-starter-webmvc`
- `org.springframework.boot:spring-boot-starter-data-jpa`
- `org.springframework.boot:spring-boot-starter-mail`
- `org.springframework.boot:spring-boot-starter-actuator`
- `org.springframework.ai:spring-ai-jsoup-document-reader` (Jsoup, Spring AI BOM 2.0.0; es wird kein LLM verwendet)
- `org.apache.commons:commons-csv`
- `org.postgresql:postgresql`
- `org.projectlombok:lombok`
- `me.paulschwarz:spring-dotenv` (BOM 5.1.0)

## Nicht notwendig

Diese Werkzeuge tauchten in früheren Versionen der Spezifikation auf, werden vom aktuellen Code aber **nicht verwendet**:

- ~~Ollama / LLM~~: Die Mails werden aus Vorlagen in `DraftService` erzeugt.
- ~~LaTeX-Distribution / `pdflatex`~~: Kein Anschreiben als PDF, der Lebenslauf wird unverändert angehängt.
- ~~Node.js / Next.js~~: Kein Frontend in diesem Repository.
- ~~Playwright~~: Der Scraper nutzt nur Jsoup (statisches HTML), SPA-Websites werden daher schlecht erfasst.

## Tests ausführen

```bash
./gradlew test
```

`ApplicationTests` ist deaktiviert (`@Disabled`), da er eine echte Datenbank und die `.env` benötigt; `ScraperServiceTest` läuft offline.

## Build

```bash
./gradlew build      # Kompilieren + Tests
./gradlew bootJar    # -> build/libs/1clickapply-0.0.1-SNAPSHOT.jar
```
