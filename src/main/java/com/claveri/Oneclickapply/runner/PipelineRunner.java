package com.claveri.Oneclickapply.runner;

import com.claveri.Oneclickapply.config.OutreachProperties;
import com.claveri.Oneclickapply.domain.Company;
import com.claveri.Oneclickapply.repository.CompanyRepository;
import com.claveri.Oneclickapply.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Point d'entrée en ligne de commande :
 *
 *   ./gradlew bootRun --args="--mode=source"          # 1. entreprises via OpenStreetMap (villes de outreach.cities)
 *   ./gradlew bootRun --args="--mode=source-ba"       # 1b. employeurs qui publient des offres tech (Bundesagentur)
 *   ./gradlew bootRun --args="--mode=scrape"          # 2. e-mails + stack + score (seulement les non scrapées)
 *   ./gradlew bootRun --args="--mode=draft"           # 3. mails personnalisés pour score >= outreach.min-score
 *   ./gradlew bootRun --args="--mode=export"          # 4. review.csv à valider (colonne approve = x / n)
 *   ./gradlew bootRun --args="--mode=import"          # 5. relit review.csv -> APPROVED / SKIPPED
 *   ./gradlew bootRun --args="--mode=send --dry-run"  # 6a. simulation
 *   ./gradlew bootRun --args="--mode=send"            # 6b. envoi réel (30/jour max, + relances J+7)
 *
 * Options : --city=Bremen (limite source/scrape à une ville), --limit=50 (nb max d'entreprises à scraper).
 */
@Component
public class PipelineRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PipelineRunner.class);

    private final SourcingService sourcing;
    private final BundesagenturService ba;
    private final ScraperService scraper;
    private final DraftService drafts;
    private final ReviewCsvService review;
    private final MailService mail;
    private final CompanyRepository companies;
    private final OutreachProperties props;

    public PipelineRunner(SourcingService sourcing, BundesagenturService ba, ScraperService scraper, DraftService drafts, ReviewCsvService review,
                          MailService mail, CompanyRepository companies, OutreachProperties props) {
        this.sourcing = sourcing;
        this.ba = ba;
        this.scraper = scraper;
        this.drafts = drafts;
        this.review = review;
        this.mail = mail;
        this.companies = companies;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String mode = first(args, "mode", "help");
        String city = first(args, "city", null);
        int limit = Integer.parseInt(first(args, "limit", "200"));

        switch (mode) {
            case "source" -> {
                List<String> cities = city != null ? List.of(city) : props.cities();
                for (String c : cities) {
                    sourcing.fetchAndSaveFromOverpass(c);
                    Thread.sleep(5000); // Overpass est un service gratuit : ne pas le surcharger
                }
            }
            case "source-ba" -> ba.fetchAll(city);
            case "scrape" -> {
                List<Company> todo = companies.findByScrapedAtIsNull().stream()
                        .filter(c -> city == null || city.equalsIgnoreCase(c.getCity()))
                        .limit(limit).toList();
                log.info("Scraping de {} entreprise(s)...", todo.size());
                int i = 0;
                for (Company c : todo) {
                    log.info("({}/{})", ++i, todo.size());
                    scraper.enrichCompanyData(c);
                }
                long withMail = companies.findByPrimaryEmailIsNotNull().size();
                log.info("Terminé. Entreprises avec e-mail en base : {}", withMail);
            }
            case "test-scrape" -> scraper.debugUrl(first(args, "url", "https://www.governikus.de"));
            case "stats" -> {
                var all = companies.findAll();
                long scraped = all.stream().filter(c -> c.getScrapedAt() != null).count();
                long mail = all.stream().filter(c -> c.getPrimaryEmail() != null).count();
                long good = all.stream().filter(c -> c.getPrimaryEmail() != null && c.getMatchScore() != null && c.getMatchScore() >= props.minScore()).count();
                long noSite = all.stream().filter(c -> c.getWebsiteUrl() == null || c.getWebsiteUrl().isBlank()).count();
                log.info("Entreprises : {} | sans site : {} | scrapées : {} | avec e-mail : {} | e-mail + score >= {} : {}",
                        all.size(), noSite, scraped, mail, props.minScore(), good);
            }
            case "optout" -> {
                // --email=jobs@firma.de ou --domain=firma.de : plus jamais de mail + suppression des données de contact
                String email = first(args, "email", null);
                String domain = first(args, "domain", null);
                List<Company> hits = new java.util.ArrayList<>();
                if (email != null) hits.addAll(companies.findByPrimaryEmailIgnoreCase(email));
                if (domain != null) companies.findByDomain(domain.toLowerCase()).ifPresent(hits::add);
                for (Company c : hits) {
                    c.setOptedOut(true);
                    c.setPrimaryEmail(null);      // DSGVO : on efface l'adresse
                    c.setContactPerson(null);
                    companies.save(c);
                    log.info("Opt-out enregistré pour {} ({})", c.getName(), c.getDomain());
                }
                if (hits.isEmpty()) log.info("Aucune entreprise trouvée pour email={} domain={}", email, domain);
            }
            case "draft" -> drafts.createDrafts();
            case "export" -> review.export();
            case "import" -> review.importDecisions();
            case "send" -> mail.sendBatch(args.containsOption("dry-run"));
            default -> log.info("Usage : --mode=source|source-ba|scrape|test-scrape --url=...|stats|optout --email=...|draft|export|import|send [--dry-run] [--city=Bremen] [--limit=50]");
        }
    }

    private static String first(ApplicationArguments args, String name, String def) {
        List<String> v = args.getOptionValues(name);
        return v == null || v.isEmpty() ? def : v.get(0);
    }
}
