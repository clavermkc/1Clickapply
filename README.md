# 1ClickApply

Plateforme de sourcing automatisé et de candidature spontanée (*Initiativbewerbung*) ciblant les entreprises tech/IT de Basse-Saxe (Niedersachsen) et des régions voisines (Bremen, Hamburg).

L'objectif à terme : extraire des entreprises via OpenStreetMap, récupérer leurs coordonnées et emails RH, générer des lettres de motivation personnalisées via un LLM, les compiler en PDF, puis les envoyer par lots contrôlés.

> ⚠️ **Ce dépôt est un travail en cours.** Seul le module de sourcing/scraping est implémenté à ce jour. Voir la section [Limitations de la version actuelle](#limitations-de-la-version-actuelle) avant de juger le comportement de l'appli.

## État actuel du projet

### Implémenté

| Module | Description | État |
|---|---|---|
| **Sourcing** (`service/SourcingService`) | Interroge l'API Overpass (OpenStreetMap) pour trouver des entreprises (`office=it`, `office=company`, `office=coworking`, `shop=computer`) dans une ville donnée, et les insère en base. | ✅ |
| **Scraping** (`service/ScraperService`) | Visite le site web de chaque entreprise (page d'accueil + jusqu'à 3 pages type `karriere`, `impressum`, `kontakt`...) via Jsoup et tente d'en extraire un email RH et un contact. | ✅ (résultats limités, voir plus bas) |
| **Persistance** (`domain`, `repository`) | Entités JPA `Company` et `CoverLetter` avec statut de cycle de vie (`ApplicationStatus`), base PostgreSQL (Neon). | ✅ |

### Pas encore implémenté

- **Module IA / Ollama (Spring AI)** — pas de connecteur `OllamaChatModel`, pas de service de réécriture de lettre. Seule la dépendance `spring-ai-jsoup-document-reader` est présente dans `build.gradle`, à titre d'outil de scraping/parsing, pas pour l'IA.
- **Module PDF / LaTeX** — pas de `LatexTemplateEngine`, `LatexSanitizerUtils` ni `PdfCompilerService`. Aucune compilation `pdflatex` n'a lieu.
- **Module Delivery / Anti-spam** — pas d'envoi SMTP, pas de rate limiting.
- **API REST** — aucun contrôleur (`@RestController`) n'existe encore ; le projet ne s'utilise pour l'instant qu'en local via `TestSourcingRunner` (un `CommandLineRunner` de test lancé au démarrage de l'application).
- **Frontend** — pas de dossier Next.js dans ce dépôt.

L'architecture cible (avec IA, PDF et delivery) reste documentée dans `HELP.md` (non versionné) à titre de spécification, mais ne reflète pas encore le code.

## Limitations de la version actuelle

- **Taux d'extraction d'emails très faible** : sur un lot d'environ 400 entreprises scrapées, le service n'a réussi à extraire un email exploitable que pour **3 à 5 entreprises**. Causes probables : beaucoup de sites n'exposent pas d'email en clair dans le HTML (formulaires de contact, images, obfuscation avancée, protections anti-bot), le crawl est limité à la page d'accueil + 3 sous-pages, et il n'y a pas de fallback JavaScript (pas de Playwright malgré ce qui est mentionné dans `HELP.md`).
- **Pas de rendu JS** : le scraper utilise uniquement Jsoup (HTML statique) ; les sites en SPA (React/Vue/Angular côté client) ne sont pas correctement scrapés.
- **Pas de déduplication avancée** : la déduplication se fait uniquement par nom de domaine.
- **`TestSourcingRunner` s'exécute à chaque démarrage** de l'application (`CommandLineRunner`), ce qui déclenche automatiquement un cycle sourcing + scraping sur `Hannover` — pratique pour tester, mais à retirer avant tout usage en production.

## Stack technique

- **Backend** : Java 21, Spring Boot 4.1.1, Spring Data JPA, Spring AI BOM 2.0.0 (utilisé uniquement pour `spring-ai-jsoup-document-reader` pour l'instant).
- **Scraping** : Jsoup.
- **Sourcing de données** : Overpass API (OpenStreetMap).
- **Base de données** : PostgreSQL, hébergée sur [Neon](https://neon.com/).
- **Build** : Gradle (wrapper fourni).

## Configuration

Le projet lit sa configuration de base de données depuis des variables d'environnement (via `spring-dotenv`), chargées depuis un fichier `.env` à la racine (non versionné). Deux options sont possibles selon vos préférences.

### Option A — Base de données cloud (Neon, Supabase, etc.)

C'est la configuration par défaut du projet. Créez un fichier `.env` sur ce modèle :

```dotenv
PGHOST=<host-fourni-par-le-provider>
PGDATABASE=<nom-de-la-base>
PGUSER=<utilisateur>
PGPASSWORD=<mot-de-passe>
PGSSLMODE=require
PGCHANNELBINDING=require
NEON_DB_PORT=5432
NEON_DB_URL="jdbc:postgresql://${PGHOST}:${NEON_DB_PORT}/${PGDATABASE}?sslmode=require&channel_binding=require"
```

Ces variables sont consommées dans `src/main/resources/application.properties` :

```properties
spring.datasource.url=${NEON_DB_URL}
spring.datasource.username=${PGUSER}
spring.datasource.password=${PGPASSWORD}
```

Ça fonctionne aussi bien avec [Neon](https://neon.com/) qu'avec [Supabase](https://supabase.com/) ou tout autre PostgreSQL managé : il suffit de renseigner l'URL JDBC, l'utilisateur et le mot de passe fournis par le provider.

### Option B — Base de données locale via Docker

Un `compose.yaml` est fourni mais **commenté par défaut** (le projet utilise la BDD cloud par défaut). Pour l'utiliser :

1. Décommentez le service `postgres` dans `compose.yaml` :

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

2. Lancez le conteneur : `docker compose up -d`.
3. Dans `src/main/resources/application.properties`, décommentez les lignes de connexion locale et commentez (ou supprimez) les lignes `${NEON_DB_URL}` / `${PGUSER}` / `${PGPASSWORD}` :

   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/oneclickapply_db
   spring.datasource.username=oneclick_user
   spring.datasource.password=oneclick_pass
   ```

   (Le projet dépend aussi de `spring-boot-docker-compose`, qui peut démarrer automatiquement les services définis dans `compose.yaml` au lancement de l'application si celui-ci est actif.)

Voir `REQUIREMENTS.md` pour la liste complète des prérequis.

## Lancer le projet

```bash
./gradlew bootRun
```

Au démarrage, `TestSourcingRunner` lance automatiquement :
1. Une collecte Overpass sur la ville `Hannover`.
2. Un scraping des 3 premières entreprises trouvées en base pour cette ville.
3. Un affichage en logs du résultat (email et contact trouvés ou non).

## Roadmap

- [ ] Améliorer le taux d'extraction d'emails (fallback JavaScript/Playwright, gestion de l'obfuscation, formulaires de contact).
- [ ] Implémenter le module IA (Ollama + Spring AI) pour la réécriture des lettres de motivation.
- [ ] Implémenter le module PDF/LaTeX.
- [ ] Implémenter le module d'envoi (SMTP + anti-spam/rate limiting).
- [ ] Exposer une API REST.
- [ ] Frontend Next.js.
