# Pipeline Initiativbewerbung (30 mails/jour)

## Configuration (une fois)
1. Dans `.env` (à côté de build.gradle) :
   ```
   NEON_DB_URL=jdbc:postgresql://...
   PGUSER=...
   PGPASSWORD=...
   GMAIL_USER=monthekamenic@gmail.com
   GMAIL_APP_PASSWORD=xxxx xxxx xxxx xxxx   # https://myaccount.google.com/apppasswords (2FA requis)
   CV_PATH=./CV_Claver.pdf
   ```
2. Copier le CV PDF à la racine du projet (ignoré par git).
3. Villes, score minimum, limite par jour : `outreach.*` dans `application.properties`.

## Routine
| Étape | Commande | Fréquence |
|---|---|---|
| 1. Trouver des entreprises (OSM) | `./gradlew bootRun --args="--mode=source"` | 1 fois (puis quand tu ajoutes des villes) |
| 1b. Entreprises qui recrutent en tech (Bundesagentur) | `./gradlew bootRun --args="--mode=source-ba"` | 1 fois/semaine |
| 2. Emails + stack + score | `./gradlew bootRun --args="--mode=scrape --limit=150"` | ~1 s/page, 150 entreprises ≈ 15–20 min |
| 3. Générer les mails | `./gradlew bootRun --args="--mode=draft"` | après chaque scrape |
| 4. Exporter pour validation | `./gradlew bootRun --args="--mode=export"` | chaque soir |
| 5. **Valider** `review.csv` | colonne `approve` : `x` = envoyer, `n` = jamais. Tu peux corriger email/sujet/texte. Ouvrir avec LibreOffice (séparateur `;`, UTF-8) | 5–10 min |
| 6. Importer | `./gradlew bootRun --args="--mode=import"` | |
| 7. Tester | `./gradlew bootRun --args="--mode=send --dry-run"` | |
| 8. Envoyer | `./gradlew bootRun --args="--mode=send"` | 1 fois/jour : max 30 (relances J+7 incluses), 5–10 min entre deux mails (~4 h au total, laisse tourner) |

## Règles intégrées
- Rien ne part sans `x` dans review.csv.
- Une seule candidature par entreprise (domaine unique), une seule relance.
- Adresses `datenschutz@`, `noreply@`, `support@`… ignorées ; `jobs@`/`karriere@`/`bewerbung@` prioritaires.
- Score : java/spring 3, kotlin 2, backend/docker/devops/… 1, page Werkstudent +2. Par défaut min 4.

Tests : `./gradlew test --tests '*ScraperServiceTest'`

## Garde-fous légaux intégrés (DSGVO / UWG / règles des sites)
- **robots.txt respecté** : une page interdite aux robots n'est jamais visitée.
- **User-Agent honnête** : le bot s'identifie (`1ClickApply-Bewerbungsbot`) avec ton e-mail de contact, il ne se fait pas passer pour un navigateur.
- **Uniquement des pages publiques**, max 7 par site, 1 s de pause entre deux requêtes, aucun formulaire, aucun login.
- **Choix de l'adresse par priorité** : `jobs@`/`karriere@` du domaine > adresse publiée sur la page Karriere (souvent le/la recruteur·se, même nominative) > `info@` > autre adresse du domaine > dernier recours. Seules `datenschutz@`, `noreply@`, `presse@`, `ir@`/investor, `support@`… sont exclues (elles n'arrivent jamais chez un recruteur). Tu valides chaque adresse dans review.csv.
- **Validation humaine obligatoire** (`x` dans review.csv) : aucun envoi automatique en masse.
- **30 mails/jour max**, 1 mail + 1 relance par entreprise, jamais plus.
- **Mention dans chaque mail** : d'où vient l'adresse + « antworten Sie mit Nein danke ».
- **Opt-out** : si quelqu'un répond non → `--mode=optout --email=jobs@firma.de` (ou `--domain=firma.de`) : adresse effacée, plus jamais contacté.

Je ne suis pas avocat : une Initiativbewerbung individuelle et personnalisée à une adresse professionnelle est une pratique courante en Allemagne, mais en cas de doute, demande au service juridique étudiant (AStA) de l'Uni Bremen.
