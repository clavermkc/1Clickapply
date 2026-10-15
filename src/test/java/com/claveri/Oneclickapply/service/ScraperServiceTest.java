package com.claveri.Oneclickapply.service;

import com.claveri.Oneclickapply.domain.Company;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ScraperServiceTest {

    private final ScraperService scraper = new ScraperService(null, null);

    @Test
    void decodesCloudflareProtectedEmail() {
        // "info@firma.de" encodé avec la clé 0x42
        String email = "info@firma.de";
        StringBuilder hex = new StringBuilder("42");
        for (char ch : email.toCharArray()) hex.append(String.format("%02x", ch ^ 0x42));
        assertEquals(Optional.of(email), ScraperService.decodeCloudflare(hex.toString()));
    }

    @Test
    void extractsObfuscatedAndMailtoEmails() {
        var doc = Jsoup.parse("<p>Kontakt: jobs (at) firma (dot) de</p><a href='mailto:Info@Firma.de?subject=x'>Mail</a>");
        List<String> emails = scraper.extractEmails(doc);
        assertTrue(emails.contains("jobs@firma.de"));
        assertTrue(emails.contains("info@firma.de"));
    }

    @Test
    void prefersHrAddressOfCompanyDomain() {
        var best = scraper.selectBestEmail(List.of("datenschutz@firma.de", "info@firma.de", "jobs@firma.de", "karriere@gmail.com"), "firma.de");
        assertEquals(Optional.of("jobs@firma.de"), best);
    }

    @Test
    void scoresJavaButNotJavascript() {
        Company c = new Company();
        scraper.scoreProfile(c, "wir entwickeln mit javascript und php");
        assertEquals(0, c.getMatchScore());
        scraper.scoreProfile(c, "wir suchen werkstudenten für java, spring boot und docker");
        assertEquals(3 + 3 + 1 + 2, c.getMatchScore());
        assertTrue(c.getTechStack().contains("java"));
    }

    @Test
    void parsesRobotsTxtForAllAgents() {
        String robots = "User-agent: Googlebot\nDisallow: /nur-google\n\nUser-agent: *\nDisallow: /intern\nDisallow:\n";
        assertEquals(List.of("/intern"), ScraperService.parseRobots(robots));
    }

    @Test
    void acceptsRecruiterAddressFromCareerPage() {
        var best = scraper.selectBestEmail(
                List.of("datenschutz@firma.de", "info@firma.de", "anna.mueller@firma.de"),
                List.of("anna.mueller@firma.de"), "firma.de");
        assertEquals(Optional.of("anna.mueller@firma.de"), best);
    }

    @Test
    void keepsPersonalAddressAsFallbackButNeverBlockedOnes() {
        assertEquals(Optional.of("max.mustermann@firma.de"),
                scraper.selectBestEmail(List.of("noreply@firma.de", "max.mustermann@firma.de"), "firma.de"));
        assertEquals(Optional.empty(),
                scraper.selectBestEmail(List.of("datenschutz@firma.de", "presse@firma.de"), "firma.de"));
    }
}
