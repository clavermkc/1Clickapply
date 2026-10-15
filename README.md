# 1ClickApply
*[Deutsche Version](README.de.md)*

Pipeline en ligne de commande pour des candidatures spontanées (*Initiativbewerbung*, Werkstudent Softwareentwicklung) auprès d'entreprises tech de Basse-Saxe et de Brême.

Le pipeline :
1. trouve des entreprises via OpenStreetMap (Overpass) et via l'API emploi de la Bundesagentur für Arbeit ;
2. visite leur site public pour en extraire un e-mail de contact, détecter leur stack et calculer un score ;
3. génère un mail personnalisé à partir d'un modèle ;
4. exporte les brouillons dans un CSV pour **validation manuelle** ;
5. envoie uniquement les mails validés, via Gmail, en joignant le CV, avec une limite par jour et un délai entre deux envois.

Le guide d'utilisation au quotidien se trouve dans [`PIPELINE.md`](PIPELINE.md).

## 🚀 Résultats récents
Dernière extraction pour la ville de **Berlin** : **885 e-mails exploitables** trouvés pour **1200 entreprises tech** scrappées ! Un taux d'efficacité record pour la prospection.

## Stack

| | |
|---|---|
| Langage | Java 21 (toolchain Gradle) |
| Framework | Spring Boot 4.1.1 : Data JPA, Mail, Actuator, Web MVC (serveur web désactivé : `spring.main.web-application-type=none`) |
| Scraping | Jsoup (via `spring-ai-jsoup-document-reader`, Spring AI BOM 2.0.0). Aucun LLM n'est utilisé. |
| Autres librairies | Apache Commons CSV, Lombok, `spring-dotenv` |
| Base de données | PostgreSQL ([Neon](https://neon.com/) par défaut), schéma géré par Hibernate (`ddl-auto=update`) |
| Build | Gradle 9.7 (wrapper `./gradlew`) |
| Conteneur | `Dockerfile` (exécution seule, le jar est construit localement) + `compose.yaml` (un service par étape) |

## Prérequis

- JDK 21 (le toolchain Gradle peut le télécharger)
- Une base PostgreSQL accessible (Neon, Supabase, ou locale)
- Un compte Gmail avec la validation en 2 étapes et un [mot de passe d'application](https://myaccount.google.com/apppasswords), nécessaire uniquement pour `--mode=send`
- Un CV en PDF, joint à chaque mail
- Un accès réseau sortant vers `overpass-api.de`, `rest.arbeitsagentur.de`, les sites des entreprises et `smtp.gmail.com:587`
- Docker *(facultatif)*

Voir aussi [`REQUIREMENTS.md`](REQUIREMENTS.md).

## Variables d'environnement

Elles sont lues depuis un fichier `.env` à la racine (`spring.config.import=optional:file:.env[.properties]` + `spring-dotenv`). Ce fichier est ignoré par git et exclu de l'image Docker.

| Variable | Requise | Usage |
|---|---|---|
| `NEON_DB_URL` | oui | URL JDBC, ex. `jdbc:postgresql://<host>:5432/<db>?sslmode=require&channel_binding=require` |
| `PGUSER` | oui | Utilisateur de la base |
| `PGPASSWORD` | oui | Mot de passe de la base |
| `GMAIL_USER` | oui | Adresse d'envoi (`spring.mail.username`, `outreach.sender-email`) |
| `GMAIL_APP_PASSWORD` | pour `send` | Mot de passe d'application Gmail |
| `CV_PATH` | pour `send` | Chemin du CV PDF (défaut `./CV_Claver.pdf`). Avec Docker : `CV/<fichier>.pdf` |
| `PGHOST`, `PGDATABASE`, `PGSSLMODE`, `PGCHANNELBINDING`, `NEON_DB_PORT` | non | Servent seulement à composer `NEON_DB_URL` |

Toute propriété `outreach.*` peut aussi être surchargée par variable d'environnement, par exemple `OUTREACH_REVIEW_CSV_PATH` ou `OUTREACH_FOLLOW_UP_AFTER_DAYS`, comme dans `compose.yaml`.

### Paramètres de campagne (`application.properties`, préfixe `outreach.`)

| Propriété | Valeur actuelle | Rôle |
|---|---|---|
| `cities` | Bremen, Hannover, Oldenburg, Osnabrück, … | Villes interrogées par `--mode=source` |
| `ba-queries` | Werkstudent Java, Spring Boot, Kotlin, … | Recherches envoyées à la Bundesagentur |
| `min-score` | `1` | Score minimal pour générer un brouillon |
| `daily-limit` | `40` | Nombre maximal de mails par jour, relances comprises |
| `min-delay-seconds` / `max-delay-seconds` | `300` / `600` | Délai aléatoire entre deux envois |
| `follow-up-after-days` | `9999` | Délai avant relance (`9999` = relances désactivées) |
| `scrape-delay-millis` | `1000` | Pause entre deux requêtes de scraping |
| `cv-path`, `review-csv-path` | `${CV_PATH}`, `./data/review.csv` | Fichiers utilisés |
| `sender-name`, `phone`, `portfolio-url`, `github-url`, `linkedin-url` | — | Signature des mails |

## Installation

```bash
git clone <repo> && cd 1Clickapply
# créer le fichier .env (voir ci-dessus)
mkdir -p CV data && cp /chemin/vers/mon_cv.pdf CV/
./gradlew build
```

### Base locale (facultatif)

Le `compose.yaml` actuel ne contient plus de service PostgreSQL. Pour une base locale, lancez un conteneur Postgres vous-même, par exemple :

```bash
docker run -d --name oneclickapply-db -p 5432:5432 \
  -e POSTGRES_DB=oneclickapply_db -e POSTGRES_USER=oneclick_user -e POSTGRES_PASSWORD=oneclick_pass postgres
```

Puis, dans `application.properties`, activez les lignes `localhost` (actuellement commentées) à la place des lignes `${NEON_DB_URL}`.

## Utilisation

Le point d'entrée est `Application.java`. Tout le pipeline passe par `runner/PipelineRunner`, qui choisit l'étape selon l'argument `--mode` :

```bash
./gradlew bootRun --args="--mode=<mode> [options]"
# ou, après ./gradlew bootJar :
java -jar build/libs/1clickapply-0.0.1-SNAPSHOT.jar --mode=<mode> [options]
```

| Mode | Effet |
|---|---|
| `source` | Récupère les entreprises OSM (`office=it`, `office=company`, …) des villes de `outreach.cities`, avec une pause de 5 s entre deux villes |
| `source-ba` | Ajoute les employeurs qui publient des offres tech sur la Bundesagentur für Arbeit |
| `scrape` | Pour les entreprises pas encore scrapées : extrait un e-mail, détecte la stack, calcule le score, cherche une page Werkstudent |
| `test-scrape --url=<url>` | Lance le scraper en mode debug sur une seule URL |
| `stats` | Affiche des compteurs : entreprises, sans site, scrapées, avec e-mail, au-dessus du score |
| `draft` | Génère un brouillon (`DRAFT`) par entreprise avec e-mail et score ≥ `min-score` |
| `export` | Écrit `data/review.csv` (séparateur `;`, UTF-8) |
| `import` | Relit `review.csv` : `x` → `APPROVED`, `n` → `SKIPPED` |
| `send [--dry-run]` | Envoie les mails `APPROVED` dans la limite du jour. `--dry-run` ne fait qu'afficher ce qui serait envoyé |
| `optout --email=… \| --domain=…` | Marque l'entreprise comme opt-out et efface son e-mail et son contact |
| *(aucun)* / `help` | Affiche l'aide |

Options : `--city=Bremen` (limite `source`, `source-ba` et `scrape` à une ville), `--limit=50` (nombre maximal d'entreprises à scraper, 200 par défaut).

Colonnes de `review.csv` : `approve; id; company; city; website; score; stack; werkstudent_page; recipient; subject; body`. Les colonnes `recipient`, `subject` et `body` peuvent être corrigées avant l'import.

### Avec Docker

```bash
./gradlew bootJar -x test          # le Dockerfile copie le jar déjà construit
docker compose build
docker compose run --rm source --mode=source --city=Hamburg
docker compose run --rm scrape
docker compose run --rm draft
docker compose run --rm export     # -> ./data/review.csv
docker compose run --rm import
docker compose run --rm send --mode=send --dry-run
docker compose run --rm send
```

Les services sont dans le profil `pipeline`, donc un simple `docker compose up` ne lance rien. `./CV` est monté en lecture seule et `./data` est partagé avec la machine hôte.

## Tâches Gradle

| Commande | Effet |
|---|---|
| `./gradlew bootRun --args="…"` | Lance l'application |
| `./gradlew build` | Compile et lance les tests |
| `./gradlew bootJar` | Produit `build/libs/1clickapply-0.0.1-SNAPSHOT.jar` |
| `./gradlew test` | Lance les tests (JUnit 5) |

## Tests

```bash
./gradlew test
./gradlew test --tests '*ScraperServiceTest'
```

- `service/ScraperServiceTest` : tests unitaires hors ligne du scraper (e-mails obfusqués ou protégés par Cloudflare, priorité des adresses RH, scoring Java/JavaScript, parsing de `robots.txt`).
- `ApplicationTests` : test de chargement du contexte, **désactivé** (`@Disabled`) parce qu'il a besoin d'une vraie base et du `.env`.

## Structure du projet

```
├── build.gradle, settings.gradle, gradlew   # build Gradle
├── Dockerfile, .dockerignore, compose.yaml  # image d'exécution + un service par étape
├── PIPELINE.md                              # routine quotidienne et garde-fous légaux
├── REQUIREMENTS.md / *.de.md                # prérequis, traductions allemandes
├── Terms.md                                 # conditions d'utilisation
├── CV/, data/, .env                         # local uniquement (ignorés par git)
└── src/
    ├── main/java/com/claveri/Oneclickapply/
    │   ├── Application.java                 # point d'entrée Spring Boot
    │   ├── runner/PipelineRunner.java       # aiguillage selon --mode
    │   ├── config/OutreachProperties.java   # propriétés outreach.*
    │   ├── domain/                          # entités JPA Company, CoverLetter, enum ApplicationStatus
    │   ├── dto/                             # réponses Overpass et Bundesagentur
    │   ├── repository/                      # repositories Spring Data
    │   └── service/
    │       ├── SourcingService.java         # Overpass / OSM
    │       ├── BundesagenturService.java    # API Jobsuche de la Bundesagentur für Arbeit
    │       ├── ScraperService.java          # Jsoup, robots.txt, e-mail, stack, score
    │       ├── DraftService.java            # modèles de mail et de relance
    │       ├── ReviewCsvService.java        # export/import de review.csv
    │       └── MailService.java             # envoi Gmail SMTP, quota, délais
    ├── main/resources/application.properties
    └── test/java/…                          # ApplicationTests, ScraperServiceTest
```

## Garde-fous

Résumé ; le détail est dans [`PIPELINE.md`](PIPELINE.md) :

- respect de `robots.txt`, User-Agent qui identifie le bot avec une adresse de contact, au plus 7 pages publiques par site, 1 s entre deux requêtes ;
- aucun mail ne part sans `x` dans `review.csv`, et une seule candidature par entreprise (domaine unique) ;
- adresses `noreply@`, `datenschutz@`, `support@`, etc. exclues ;
- opt-out via `--mode=optout`, qui efface l'adresse de contact.

## Limitations connues

- Le scraper ne lit que le HTML statique : les sites qui génèrent leur contenu en JavaScript (SPA) sont mal couverts, et beaucoup de sites ne publient aucun e-mail en clair.
- Le texte des mails vient de modèles écrits en dur dans `DraftService`. Il n'y a ni génération par IA ni lettre de motivation en PDF ; le CV est joint tel quel.
- Les coordonnées de l'expéditeur (nom, téléphone, liens) et l'adresse de contact du User-Agent sont écrites en dur dans `application.properties` et dans `SourcingService` / `ScraperService`. Un fork doit les remplacer.
- La Javadoc de `PipelineRunner` et `PIPELINE.md` parlent de « 30 mails/jour » et de relances à J+7, alors que la configuration actuelle est `daily-limit=40` avec les relances désactivées.
- Pas d'API REST ni de frontend.

## Roadmap

- [ ] TODO : améliorer le taux d'extraction d'e-mails (rendu JavaScript, obfuscation)
- [ ] TODO : sortir les données personnelles de l'expéditeur du code et de la config versionnée

## Licence

TODO : aucun fichier `LICENSE` dans le dépôt, donc aucune licence open source n'est accordée pour l'instant. Les conditions d'utilisation sont dans [`Terms.md`](Terms.md).
