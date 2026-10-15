# Prérequis (Requirements)

*[Deutsche Version](REQUIREMENTS.de.md)*

Ce projet est une application **Java 21 / Spring Boot** en ligne de commande. Ce document liste ce qui est nécessaire pour faire tourner tout le pipeline (sourcing, scraping, brouillons, validation CSV, envoi).

## Nécessaire

| Prérequis | Détail |
|---|---|
| **JDK 21** | Requis par `build.gradle` (`JavaLanguageVersion.of(21)`). Le toolchain Gradle peut le télécharger s'il n'est pas installé localement. |
| **Gradle** | Fourni via le wrapper (`./gradlew`, Gradle 9.7), aucune installation manuelle nécessaire. |
| **Base de données PostgreSQL** | Un provider cloud comme [Neon](https://neon.com/) (config par défaut) ou [Supabase](https://supabase.com/), ou un PostgreSQL local (voir *Base locale* dans le `README.md`). Le schéma est créé par Hibernate (`ddl-auto=update`). |
| **Fichier `.env`** | À la racine du projet, ignoré par git. Doit contenir au minimum `NEON_DB_URL`, `PGUSER`, `PGPASSWORD`, `GMAIL_USER`. Chargé via `spring-dotenv`. Voir le tableau des variables dans le `README.md`. |
| **Compte Gmail + mot de passe d'application** | Nécessaire uniquement pour `--mode=send` (SMTP `smtp.gmail.com:587`). Le compte doit avoir la validation en 2 étapes activée pour générer un [mot de passe d'application](https://myaccount.google.com/apppasswords) (`GMAIL_APP_PASSWORD`). |
| **CV en PDF** | Joint à chaque mail envoyé. Chemin donné par `CV_PATH` (avec Docker : à placer dans `./CV/`). |
| **Accès réseau sortant** | Vers `overpass-api.de` (sourcing OSM), `rest.arbeitsagentur.de` (Bundesagentur für Arbeit), les sites web des entreprises (scraping) et `smtp.gmail.com:587` (envoi). |
| **Docker** *(optionnel)* | Pour lancer les étapes via `compose.yaml` (un service par étape, profil `pipeline`). Le jar doit être construit avant avec `./gradlew bootJar -x test`. |

Dépendances Gradle principales (voir `build.gradle`) :
- `org.springframework.boot:spring-boot-starter-webmvc`
- `org.springframework.boot:spring-boot-starter-data-jpa`
- `org.springframework.boot:spring-boot-starter-mail`
- `org.springframework.boot:spring-boot-starter-actuator`
- `org.springframework.ai:spring-ai-jsoup-document-reader` (Jsoup, Spring AI BOM 2.0.0 ; aucun LLM n'est utilisé)
- `org.apache.commons:commons-csv`
- `org.postgresql:postgresql`
- `org.projectlombok:lombok`
- `me.paulschwarz:spring-dotenv` (BOM 5.1.0)

## Pas nécessaire

Ces outils apparaissaient dans d'anciennes versions de la spécification mais **ne sont pas utilisés** par le code actuel :

- ~~Ollama / LLM~~ : les mails sont générés à partir de modèles écrits dans `DraftService`.
- ~~Distribution LaTeX / `pdflatex`~~ : pas de lettre de motivation en PDF, le CV est joint tel quel.
- ~~Node.js / Next.js~~ : pas de frontend dans ce dépôt.
- ~~Playwright~~ : le scraper n'utilise que Jsoup (HTML statique), les sites en SPA sont donc mal couverts.

## Lancer les tests

```bash
./gradlew test
```

`ApplicationTests` est désactivé (`@Disabled`) car il a besoin d'une vraie base et du `.env` ; `ScraperServiceTest` tourne hors ligne.

## Build

```bash
./gradlew build      # compile + tests
./gradlew bootJar    # -> build/libs/1clickapply-0.0.1-SNAPSHOT.jar
```

---

# Adapter le projet à un autre domaine ou à un autre profil

Le code est réglé pour **un** profil : étudiant en informatique qui cherche un poste de Werkstudent en Java / Spring Boot dans des entreprises IT. Pour viser un autre secteur, il faut modifier **les 5 endroits ci-dessous**. Si vous en oubliez un, le pipeline continue de tourner sans erreur mais trie mal les entreprises ou envoie un texte qui parle d'informatique.

L'exemple suivi dans toute cette section : **un·e étudiant·e en Elektrotechnik qui cherche un poste de Werkstudent en automatisation, électronique ou énergie**.

| # | Quoi | Fichier | Emplacement |
|---|---|---|---|
| 1 | Quelles entreprises chercher sur OpenStreetMap | `service/SourcingService.java` | `buildQuery()` (l. 103–121), `isIgnoredDomain()` (l. 124) |
| 2 | Quelles offres chercher à la Bundesagentur | `application.properties` + `service/BundesagenturService.java` | `outreach.ba-queries`, `isRelevant()` (l. 103–106), `matchesName()` (l. 143) |
| 3 | Mots-clés et score | `service/ScraperService.java` | `KEYWORDS` (l. 40–70), `STUDENT` (l. 71), `GUESSED_PATHS` (l. 34), `isTargetLink()` (l. 240) |
| 4 | Texte du mail et de la relance | `service/DraftService.java` | intitulé du poste (l. 43), `subject()` (l. 55), `body()` (l. 63), `followUpBody()` (l. 94), `stackSentence()` (l. 119), `stackOnly()` + `pretty()` (l. 128–156) |
| 5 | Paramètres et identité de l'expéditeur | `application.properties` | `outreach.*` + User-Agent (`SourcingService` l. 32, `ScraperService` l. 29) |

Chemins relatifs à `src/main/java/com/claveri/Oneclickapply/`. Les numéros de ligne correspondent à la version actuelle du dépôt et peuvent bouger.

## 1. Requête Overpass (OpenStreetMap)

`buildQuery(city)` renvoie une requête en [Overpass QL](https://wiki.openstreetmap.org/wiki/Overpass_API/Overpass_QL). Fonctionnement :

- `area["name"="%s"]["admin_level"~"^(4|6|8)$"]` : la zone de recherche. `%s` est remplacé par chaque ville de `outreach.cities`. Les niveaux 4, 6 et 8 correspondent au Land, au Kreis et à la commune.
- `nwr[...]` : cherche les nœuds, ways et relations qui portent ces tags.
- `["website"]` / `["contact:website"]` : garde ce filtre. Le code ignore toute entreprise sans site web (l. 63), donc sans lui Overpass renvoie surtout des entrées inutiles.
- `out center tags;` : à garder, le DTO `OverpassResponseDto` attend ce format.

Les tags valables pour chaque secteur sont sur le wiki OSM : [`office=*`](https://wiki.openstreetmap.org/wiki/Key:office), [`craft=*`](https://wiki.openstreetmap.org/wiki/Key:craft), [`industrial=*`](https://wiki.openstreetmap.org/wiki/Key:industrial). Testez toujours une requête sur [overpass-turbo.eu](https://overpass-turbo.eu/) avant de l'intégrer : collez-la en remplaçant `%s` par une ville, par exemple `Hannover`.

### Exemple prêt à l'emploi : Elektrotechnik

Remplacer **tout le corps** de `buildQuery()` (l. 103–121) par :

```java
    private String buildQuery(String city) {
        return """
            [out:json][timeout:120];
            area["name"="%s"]["admin_level"~"^(4|6|8)$"]->.searchArea;
            (
              // Électriciens, électronique, énergie
              nwr["craft"="electrician"]["website"](area.searchArea);
              nwr["craft"="electrician"]["contact:website"](area.searchArea);
              nwr["craft"="electronics_repair"]["website"](area.searchArea);
              nwr["office"="energy_supplier"]["website"](area.searchArea);
              // Bureaux d'ingénieurs : tous les secteurs, on filtre sur le nom
              nwr["office"="engineer"]["website"]["name"~"elektr|electr|energie|automati|steuerung|mess|regel",i](area.searchArea);
              // Entreprises et usines dont le nom indique l'électrotechnique
              nwr["office"="company"]["website"]["name"~"elektr|electr|elektronik|automation|automatisierung|antrieb|schaltanlag|energietechnik|steuerungstechnik",i](area.searchArea);
              nwr["office"="company"]["contact:website"]["name"~"elektr|electr|elektronik|automation|automatisierung|antrieb|schaltanlag|energietechnik|steuerungstechnik",i](area.searchArea);
              nwr["man_made"="works"]["website"]["name"~"elektr|electr|elektronik|automation|antrieb|energie",i](area.searchArea);
              nwr["industrial"]["website"]["name"~"elektr|electr|elektronik|automation|antrieb|energie",i](area.searchArea);
            );
            out center tags;
            """.formatted(city);
    }
```

Pièges :
- La chaîne passe par `.formatted(city)`. Un `%` littéral dans la requête doit donc s'écrire `%%`, sinon l'application lève `UnknownFormatConversionException`.
- `["name"~"…",i]` est une regex insensible à la casse. Plus elle est large, plus il y a de résultats, et de faux positifs.
- `craft=electrician` ramène surtout de petits artisans (Elektroinstallateur), qui n'ont pas toujours de poste de Werkstudent. Supprimez ces lignes pour ne viser que l'industrie.
- `[timeout:120]` : une requête plus large est plus lente. Overpass est un service gratuit, gardez la pause de 5 s entre deux villes (`PipelineRunner`).
- Les deux lignes `contact:website` ne sont mises que sur les tags les plus fréquents. Dupliquez-les pour les autres si besoin.

`isIgnoredDomain()` (l. 124) écarte les réseaux sociaux et les fournisseurs de mail. Ajoutez-y les annuaires de votre secteur si des entreprises y renvoient (`|gelbeseiten|11880|myhammer`, par exemple).

### Autres secteurs (point de départ)

| Secteur | Tags OSM à essayer |
|---|---|
| Maschinenbau / Mechatronik | `office=engineer`, `man_made=works`, `craft=metal_construction`, `office=company` + filtre sur le nom (`maschinenbau\|mechatron\|anlagenbau`) |
| Bauingenieurwesen / Architektur | `office=engineer`, `office=architect` |
| Marketing / Medien | `office=advertising_agency`, `office=company` + filtre sur le nom (`agentur\|media`) |
| Logistik | `office=logistics`, `office=company` + filtre sur le nom (`logistik\|spedition`) |
| Wirtschaft / Steuern | `office=accountant`, `office=tax_advisor`, `office=consulting` |

## 2. Source Bundesagentur für Arbeit (`--mode=source-ba`)

Trois réglages, **tous les trois à changer** :

**a) Mots-clés de recherche** dans `application.properties` :

```properties
outreach.ba-queries=Werkstudent Elektrotechnik,Werkstudent Automatisierungstechnik,Werkstudent Embedded Systems,Werkstudent Energietechnik,SPS-Programmierer,Elektroingenieur,Hardwareentwicklung,Inbetriebnahme
```

**b) Filtre de pertinence** `isRelevant()` (l. 103–106). Il ne laisse passer que les offres dont le titre ou le métier parle d'informatique. **Si vous ne le changez pas, toutes les offres Elektrotechnik sont rejetées** et `source-ba` n'ajoute rien. Remplacez la regex :

```java
        return t.matches(".*(elektr|electr|automatisier|sps|plc|embedded|hardware|energietechnik|mess.?technik|regelungstechnik|inbetriebnahme|schaltplan|eplan|mechatron).*");
```

**c) Garde-fou nom ↔ domaine** `matchesName()` (l. 143). Les mots trop génériques ne doivent pas suffire à associer un employeur à un domaine. Pour l'électrotechnique, ajoutez-les à la liste exclue, sinon « Elektro Müller GmbH » peut être associé à `elektro-schmidt.de` :

```java
!token.matches("gmbh|mbh|group|gruppe|holding|deutschland|germany|systems|solutions|services|consulting|elektro|elektrotechnik|technik|energie|automation")
```

## 3. Mots-clés et score (`ScraperService`)

Chaque mot-clé trouvé sur le site rapporte son poids, et une page qui parle de Werkstudent ajoute +2. Le total est stocké dans `match_score`, et `draft` ne crée un mail que si `match_score >= outreach.min-score`. Le mot-clé doit être isolé, entouré de caractères autres que des lettres : `java` ne compte pas dans `javascript`.

**Remplacez toute la map `KEYWORDS`** (l. 40–70). Si vous gardez les anciens mots (`software`, `api`, `cloud`…), les entreprises IT continuent de marquer des points. Exemple Elektrotechnik, mots en **minuscules** (le texte est mis en minuscules avant la recherche) :

```java
    private static final Map<String, Integer> KEYWORDS = new LinkedHashMap<>();
    static {
        // Cœur du profil (poids fort)
        KEYWORDS.put("sps", 3);
        KEYWORDS.put("tia portal", 3);
        KEYWORDS.put("simatic", 2);
        KEYWORDS.put("eplan", 3);
        KEYWORDS.put("automatisierungstechnik", 3);
        KEYWORDS.put("elektrotechnik", 2);
        KEYWORDS.put("embedded", 2);
        KEYWORDS.put("leiterplatte", 2);
        KEYWORDS.put("pcb", 2);
        KEYWORDS.put("schaltplan", 2);
        // Compétences complémentaires
        KEYWORDS.put("plc", 1);
        KEYWORDS.put("codesys", 1);
        KEYWORDS.put("matlab", 1);
        KEYWORDS.put("simulink", 1);
        KEYWORDS.put("altium", 1);
        KEYWORDS.put("fpga", 1);
        KEYWORDS.put("vhdl", 1);
        KEYWORDS.put("mikrocontroller", 1);
        KEYWORDS.put("c++", 1);
        KEYWORDS.put("knx", 1);
        KEYWORDS.put("energietechnik", 1);
        KEYWORDS.put("antriebstechnik", 1);
        KEYWORDS.put("messtechnik", 1);
        KEYWORDS.put("regelungstechnik", 1);
        KEYWORDS.put("photovoltaik", 1);
        KEYWORDS.put("inbetriebnahme", 1);
        KEYWORDS.put("schaltschrank", 1);
    }
```

Autres réglages dans ce fichier :
- **`STUDENT`** (l. 71) : la regex qui donne le bonus de +2. Adaptez-la à ce que vous cherchez, par exemple `"werkstudent|working student|studentische|praktikum|abschlussarbeit|duales studium"`, ou `"ausbildung|azubi"` pour une Ausbildung.
- **`GUESSED_PATHS`** (l. 34) et **`isTargetLink()`** (l. 240) : les pages visitées en plus de l'accueil. Pour l'industrie, ajouter `"/produkte"`, `"/leistungen"`, `"/unternehmen"` donne plus de texte à scorer. Ne montez pas `MAX_PAGES` (7) : c'est l'un des garde-fous de politesse décrits dans `PIPELINE.md`.
- `HR_PREFIXES` / `BLOCKED_PREFIXES` (l. 284–286) ne dépendent pas du secteur. Vous pouvez les laisser.

**Recalibrez `outreach.min-score`** (`application.properties`, actuellement `1`). Avec la map ci-dessus, `4` est un point de départ raisonnable : par exemple « SPS » + « Elektrotechnik » trouvés sur le site, ou un mot-clé fort + une page Werkstudent. Vérifiez la répartition avec `--mode=stats` et sur quelques sites avec `--mode=test-scrape --url=…`.

**Le test `ScraperServiceTest.scoresJavaButNotJavascript` cassera** : il attend les poids Java/Spring/Docker. Adaptez-le à vos nouveaux mots-clés, sinon `./gradlew build` échoue. Utilisez `bootJar -x test` en attendant.

## 4. Lettre / mail (`DraftService`)

Le texte actuel est **personnel** : nom de l'université, employeurs, projets, disponibilité et langues sont écrits en dur. Tout est à réécrire. Voici ce qu'il faut toucher :

| Emplacement | Contenu actuel |
|---|---|
| l. 43 `new CoverLetter(c, "…")` | Intitulé du poste enregistré en base : `"Werkstudent Softwareentwicklung (Initiativbewerbung)"` |
| `subject()` l. 55 | Objet du mail, choisi selon les mots-clés trouvés (Java, Kotlin, DevOps) |
| `body()` l. 63 | Corps du mail, avec la phrase « Bremen vor Ort » en dur (l. 66–69) |
| `followUpBody()` l. 94 | Relance, qui cite « Softwareentwicklung (Java / Spring Boot) » |
| `stackSentence()` l. 121 | Phrase pour les offres BA : « … im Bereich Softwareentwicklung wächst » |
| `stackOnly()` l. 129 | Liste `mine` : les mots-clés que vous maîtrisez, cités dans le mail |
| `pretty()` l. 139 | Orthographe affichée de chaque mot-clé (`"sps"` → `"SPS"`) |

> Les entrées de `mine` (dans `stackOnly()`) et de `has(c, "…")` (dans `subject()`) **doivent être des clés de `KEYWORDS`** (section 3), en minuscules. Sinon la phrase « Auf Ihrer Website habe ich gesehen… » n'apparaît jamais et l'objet du mail reste générique.

### Exemple prêt à l'emploi : Elektrotechnik

Remplacez les `[…]` par vos vraies informations. Le nombre de `%s` (8) correspond exactement aux 8 arguments de `.formatted(...)` : si vous en ajoutez ou en retirez un, faites-le des deux côtés.

```java
    String subject(Company c) {
        String focus = has(c, "sps") || has(c, "tia portal") ? "Automatisierungstechnik / SPS"
                : has(c, "embedded") || has(c, "fpga") ? "Embedded Systems"
                : has(c, "energietechnik") || has(c, "photovoltaik") ? "Energietechnik"
                : "Elektrotechnik";
        return "Initiativbewerbung als Werkstudent " + focus + " – " + props.senderName();
    }

    String body(Company c) {
        String greeting = greeting(c.getContactPerson());
        String stackSentence = stackSentence(c);
        String where = c.getCity() != null && !c.getCity().isBlank()
                ? "vor Ort in " + c.getCity() + " oder hybrid"
                : "vor Ort oder hybrid";

        return """
                %s

                ich studiere Elektrotechnik und Informationstechnik (B.Sc.) an der [HOCHSCHULE] und möchte mich bei %s initiativ als Werkstudent im Bereich Elektrotechnik bewerben.%s

                [ERFAHRUNG, 2–3 Sätze: z. B. SPS-Programmierung mit TIA Portal, Schaltpläne in EPLAN, Inbetriebnahme von Anlagen, Laborpraktika in Mess- und Regelungstechnik.]

                Ich kann ab [MONAT JAHR] [N] Stunden pro Woche arbeiten, %s. Meinen Lebenslauf finden Sie im Anhang.

                Über eine kurze Rückmeldung oder ein Kennenlernen würde ich mich sehr freuen.

                Mit freundlichen Grüßen
                %s
                %s · %s
                %s

                ---
                Ich habe Ihre Kontaktadresse Ihrem öffentlichen Impressum bzw. Ihrer Karriereseite entnommen und nutze sie ausschließlich für diese Bewerbung. Wenn Sie keine weitere Nachricht von mir wünschen, antworten Sie einfach kurz mit „Nein danke“ – ich lösche Ihre Daten dann und melde mich nicht mehr.
                """.formatted(greeting, c.getName(), stackSentence, where,
                props.senderName(), props.phone(), props.senderEmail(), props.linkedinUrl()).strip();
    }
```

Dans `stackSentence()` (l. 121), remplacez « im Bereich Softwareentwicklung » par « im Bereich Elektrotechnik ». Dans `stackOnly()` et `pretty()` :

```java
        List<String> mine = List.of("sps", "tia portal", "eplan", "elektrotechnik", "embedded", "matlab", "simulink", "c++", "schaltplan");
```

```java
    private static String pretty(String kw) {
        return switch (kw) {
            case "sps" -> "SPS";
            case "tia portal" -> "TIA Portal";
            case "eplan" -> "EPLAN";
            case "elektrotechnik" -> "Elektrotechnik";
            case "embedded" -> "Embedded Systems";
            case "matlab" -> "MATLAB";
            case "simulink" -> "Simulink";
            case "c++" -> "C++";
            case "schaltplan" -> "Schaltplänen";
            default -> kw;
        };
    }
```

Dans `stackOnly()`, vous pouvez aussi adoucir la fin de phrase : « mit denen ich täglich arbeite » → « mit denen ich bereits Erfahrung gesammelt habe ».

Dans `followUpBody()` (l. 94), remplacez « Softwareentwicklung (Java / Spring Boot) » par votre domaine. Les relances sont désactivées par défaut (`outreach.follow-up-after-days=9999`), mais le texte doit rester cohérent si vous les activez.

**Gardez le paragraphe final après `---`** (provenance de l'adresse + « Nein danke ») dans le mail et dans la relance : c'est l'un des garde-fous DSGVO/UWG décrits dans `PIPELINE.md`.

Après `--mode=export`, relisez quelques mails dans `data/review.csv` avant de valider avec `x`.

## 5. Paramètres et identité (`application.properties`)

| Propriété | À adapter |
|---|---|
| `outreach.cities` | Villes de recherche, telles qu'écrites dans OSM (`Osnabrück` avec umlaut) |
| `outreach.ba-queries` | Voir section 2 |
| `outreach.min-score` | Voir section 3 |
| `outreach.daily-limit`, `min-delay-seconds`, `max-delay-seconds` | Rythme d'envoi. Rester bas protège la réputation de l'adresse Gmail |
| `outreach.sender-name`, `phone`, `portfolio-url`, `github-url`, `linkedin-url` | **Vos** coordonnées : les valeurs actuelles sont celles de l'auteur |
| `outreach.cv-path` / `CV_PATH` | Votre CV. Le nom de la pièce jointe est `Lebenslauf_<sender-name>.pdf` |

Mettez aussi **votre adresse de contact dans le User-Agent** des deux clients HTTP : `SourcingService` (l. 32) et `ScraperService` (l. 29). Les sites qui vous scrapent doivent pouvoir vous joindre (voir `PIPELINE.md`).

`ScraperService` envoie `Accept-Language: de-DE` (l. 214). Changez-le si vous ciblez des sites dans une autre langue, et dans ce cas traduisez aussi vos mots-clés.

## 6. Repartir d'une base propre

La base garde les entreprises déjà collectées, et `scrape` ne traite que celles où `scraped_at` est vide. Après un changement de secteur, les anciennes entreprises IT restent donc en base avec leur ancien score, et `draft` peut encore leur écrire. Deux options :

- **Recommandé** : utiliser une nouvelle base (une nouvelle base ou branche Neon, puis changer `NEON_DB_URL` dans `.env`). Hibernate recrée les tables au démarrage.
- **Recalculer les scores sur la base existante** (SQL, à lancer dans la console de votre provider). Cela supprime les brouillons pas encore envoyés et force un nouveau scraping :

  ```sql
  DELETE FROM cover_letters WHERE status IN ('DRAFT', 'APPROVED');
  UPDATE companies SET scraped_at = NULL, match_score = NULL, tech_stack = NULL;
  ```

  Ne supprimez pas les lignes déjà envoyées (`SENT`) : c'est ce qui évite de recontacter une entreprise.

## 7. Checklist de vérification

```bash
./gradlew test                                              # après adaptation de ScraperServiceTest
./gradlew bootRun --args="--mode=test-scrape --url=https://<site-du-secteur>"   # score/stack/e-mail sur un site, rien n'est enregistré
./gradlew bootRun --args="--mode=source --city=Hannover"    # une seule ville pour commencer
./gradlew bootRun --args="--mode=scrape --city=Hannover --limit=20"
./gradlew bootRun --args="--mode=stats"                     # combien passent min-score ?
./gradlew bootRun --args="--mode=draft"
./gradlew bootRun --args="--mode=export"                    # relire data/review.csv
./gradlew bootRun --args="--mode=send --dry-run"            # avant tout envoi réel
```

- [ ] Requête Overpass testée sur overpass-turbo.eu
- [ ] `isRelevant()` et `outreach.ba-queries` adaptés ensemble
- [ ] `KEYWORDS` entièrement remplacée, `min-score` recalibré
- [ ] `mine`, `pretty()` et `subject()` n'utilisent que des clés de `KEYWORDS`
- [ ] `body()`, `followUpBody()` et `stackSentence()` ne parlent plus d'informatique, et aucun `[…]` ne reste
- [ ] Coordonnées, CV et User-Agent remplacés par les vôtres
- [ ] Base neuve ou scores remis à zéro
