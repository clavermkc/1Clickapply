package com.claveri.Oneclickapply;

import com.claveri.Oneclickapply.domain.Company;
import com.claveri.Oneclickapply.repository.CompanyRepository;
import com.claveri.Oneclickapply.service.ScraperService;
import com.claveri.Oneclickapply.service.SourcingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TestSourcingRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TestSourcingRunner.class);

    private final SourcingService sourcingService;
    private final ScraperService scraperService;
    private final CompanyRepository companyRepository;

    public TestSourcingRunner(SourcingService sourcingService,
                              ScraperService scraperService,
                              CompanyRepository companyRepository) {
        this.sourcingService = sourcingService;
        this.scraperService = scraperService;
        this.companyRepository = companyRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("===========================================");
        log.info("=== DÉBUT DU TEST DU MODULE 1 ===");
        log.info("===========================================");

        String targetCity = "Hannover";

        // 1. Collecte Overpass & Sauvegarde PostgreSQL
        log.info("1. Extraction des entreprises depuis Overpass pour {}...", targetCity);
        List<Company> newCompanies = sourcingService.fetchAndSaveFromOverpass(targetCity);
        log.info("{} nouvelles entreprises insérées en BDD.", newCompanies.size());

        // 2. Scraping & Enrichissement (limité aux 3 premières pour un test rapide)
        List<Company> companiesToScrape = companyRepository.findByCityIgnoreCase(targetCity)
                .stream()
                .limit(3)
                .toList();

        log.info("2. Lancement du scraping pour {} entreprise(s)...", companiesToScrape.size());
        for (Company company : companiesToScrape) {
            scraperService.enrichCompanyData(company);
        }

        // 3. Affichage du résultat final extrait de PostgreSQL
        log.info("===========================================");
        log.info("=== VÉRIFICATION DU CONTENU DE LA BDD ===");
        log.info("===========================================");

        companyRepository.findAll().forEach(c ->
                log.info("ID: {} | Nom: {} | Domaine: {} | Email: {} | Contact: {}",
                        c.getId(),
                        c.getName(),
                        c.getDomain(),
                        c.getPrimaryEmail() != null ? c.getPrimaryEmail() : "NON TROUVÉ",
                        c.getContactPerson() != null ? c.getContactPerson() : "NON TROUVÉ")
        );

        log.info("===========================================");
        log.info("=== FIN DU TEST DU MODULE 1 ===");
        log.info("===========================================");
    }
}