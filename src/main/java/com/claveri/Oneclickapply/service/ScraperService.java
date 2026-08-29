package com.claveri.Oneclickapply.service;

import com.claveri.Oneclickapply.domain.Company;
import com.claveri.Oneclickapply.repository.CompanyRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ScraperService {

    private static final Logger log = LoggerFactory.getLogger(ScraperService.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 5000;

    private final CompanyRepository companyRepository;

    public ScraperService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @Transactional
    public void enrichCompanyData(Company company) {
        if (company.getWebsiteUrl() == null || company.getWebsiteUrl().isBlank()) {
            return;
        }

        log.info("Scraping en cours pour : {}", company.getName());
        Set<String> visitedUrls = new HashSet<>();
        List<String> emailsFound = new ArrayList<>();

        try {
            Document homePage = fetchPage(company.getWebsiteUrl());
            if (homePage == null) return;

            visitedUrls.add(company.getWebsiteUrl());
            emailsFound.addAll(extractEmails(homePage.html()));

            Elements links = homePage.select("a[href]");
            List<String> targetUrls = new ArrayList<>();

            for (Element link : links) {
                String href = link.attr("abs:href");
                String linkText = link.text();

                if (isTargetLink(href, linkText) && visitedUrls.add(href)) {
                    targetUrls.add(href);
                }
            }

            for (String targetUrl : targetUrls.stream().limit(3).toList()) {
                Document subPage = fetchPage(targetUrl);
                if (subPage != null) {
                    emailsFound.addAll(extractEmails(subPage.html()));
                    if (company.getContactPerson() == null) {
                        extractContactPerson(subPage).ifPresent(company::setContactPerson);
                    }
                }
            }

            selectBestEmail(emailsFound).ifPresent(company::setPrimaryEmail);
            companyRepository.save(company);
            log.info("Enrichissement terminé pour {} - Email trouvé : {}", company.getName(), company.getPrimaryEmail());

        } catch (Exception e) {
            log.warn("Erreur lors du scraping de {} : {}", company.getName(), e.getMessage());
        }
    }

    private Document fetchPage(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .get();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isTargetLink(String href, String linkText) {
        String lowerHref = href.toLowerCase();
        String lowerText = linkText.toLowerCase();

        return lowerHref.contains("karriere") || lowerHref.contains("jobs") || lowerHref.contains("stellen")
                || lowerHref.contains("careers") || lowerHref.contains("impressum") || lowerHref.contains("about")
                || lowerHref.contains("contact") || lowerHref.contains("kontakt")
                || lowerText.contains("karriere") || lowerText.contains("jobs") || lowerText.contains("careers")
                || lowerText.contains("about") || lowerText.contains("contact");
    }

    private List<String> extractEmails(String htmlContent) {
        List<String> emails = new ArrayList<>();
        String cleanedHtml = htmlContent.replaceAll("\\[at\\]|\\(at\\)", "@").replaceAll("\\[dot\\]|\\(dot\\)", ".");

        var matcher = java.util.regex.Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
                .matcher(cleanedHtml);

        while (matcher.find()) {
            String email = matcher.group().toLowerCase();
            if (!email.endsWith(".png") && !email.endsWith(".jpg") && !email.endsWith(".jpeg") && !email.endsWith(".gif")) {
                emails.add(email);
            }
        }
        return emails;
    }

    private Optional<String> selectBestEmail(List<String> emails) {
        if (emails.isEmpty()) return Optional.empty();

        for (String email : emails) {
            if (email.startsWith("bewerbung") || email.startsWith("jobs") || email.startsWith("karriere") || email.startsWith("hr") || email.startsWith("careers")) {
                return Optional.of(email);
            }
        }
        for (String email : emails) {
            if (email.startsWith("kontakt") || email.startsWith("contact") || email.startsWith("info") || email.startsWith("hallo")) {
                return Optional.of(email);
            }
        }
        return Optional.of(emails.get(0));
    }

    private Optional<String> extractContactPerson(Document doc) {
        String text = doc.text();
        var matcher = java.util.regex.Pattern.compile("(?:Ansprechpartner|Kontaktperson|Ihr Ansprechpartner|Contact Person):?\\s*(Herr|Frau|Mr\\.|Ms\\.)?\\s*([A-Z][a-z]+\\s+[A-Z][a-z]+)")
                .matcher(text);

        if (matcher.find()) {
            String salutation = matcher.group(1) != null ? matcher.group(1) + " " : "";
            String name = matcher.group(2);
            return Optional.of(salutation + name);
        }
        return Optional.empty();
    }
}
