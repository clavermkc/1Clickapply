# Prérequis (Requirements)

Ce projet est une application **Java ** . Ce document liste ce qui est nécessaire pour faire tourner la version actuelle du projet, ainsi que ce qui sera nécessaire pour les modules pas encore implémentés.

## Nécessaire pour la version actuelle (sourcing + scraping)

| Prérequis | Détail |
|---|---|
| **JDK 21** | Requis par `build.gradle` (`JavaLanguageVersion.of(21)`). Le wrapper Gradle peut le télécharger via un toolchain si non installé localement. |
| **Gradle** | Fourni via le wrapper (`./gradlew`), aucune installation manuelle nécessaire. |
| **Base de données PostgreSQL** | Deux options : (1) un provider cloud comme [Neon](https://neon.com/) ou [Supabase](https://supabase.com/) — config par défaut du projet, ou (2) PostgreSQL en local via Docker (`compose.yaml`, service commenté par défaut à décommenter). Voir la section *Configuration* du `README.md`. |
| **Fichier `.env`** | Requis uniquement pour l'option cloud. Doit contenir les variables `PGHOST`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`, `PGSSLMODE`, `PGCHANNELBINDING`, `NEON_DB_PORT`, `NEON_DB_URL` (voir `README.md`). Chargé via `spring-dotenv`. |
| **Docker** *(optionnel)* | Uniquement si vous choisissez la BDD locale via `compose.yaml`. |
| **Accès réseau sortant** | Vers `overpass-api.de` (sourcing OSM) et vers les sites web des entreprises ciblées (scraping). |

Dépendances Gradle principales (voir `build.gradle`) :
- `org.springframework.boot:spring-boot-starter-webmvc`
- `org.springframework.boot:spring-boot-starter-data-jpa`
- `org.springframework.ai:spring-ai-jsoup-document-reader`
- `org.postgresql:postgresql`
- `org.projectlombok:lombok`
- `me.paulschwarz:spring-dotenv-bom`

## Pas nécessaire pour l'instant

Ces éléments sont mentionnés dans la spécification cible du projet (`HELP.md`) mais **ne sont pas requis aujourd'hui** car les modules correspondants ne sont pas implémentés :

- ~~Ollama (LLM local, ex. `llama3.2`/`mistral`)~~ — module IA non implémenté.
- ~~Distribution LaTeX / `pdflatex`~~ — module PDF non implémenté.
- ~~Serveur SMTP~~ — module d'envoi non implémenté.
- ~~Node.js / Next.js~~ — pas de frontend dans ce dépôt.
- ~~Playwright~~ — mentionné comme fallback JS dans la spec, non implémenté (le scraper actuel n'utilise que Jsoup, donc les sites en SPA ne sont pas correctement scrapés — voir les limitations dans `README.md`).

Ils seront à ajouter au fur et à mesure de l'implémentation des modules IA, PDF et Delivery.

## Lancer les tests

```bash
./gradlew test
```

## Build

```bash
./gradlew build
```
