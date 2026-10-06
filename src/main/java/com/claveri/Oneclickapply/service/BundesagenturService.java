package com.claveri.Oneclickapply.service;

import com.claveri.Oneclickapply.config.OutreachProperties;
import com.claveri.Oneclickapply.domain.Company;
import com.claveri.Oneclickapply.dto.BaJobsResponseDto;
import com.claveri.Oneclickapply.repository.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Source "entreprises qui recrutent des profils tech" : API publique de la Jobbörse
 * de la Bundesagentur für Arbeit (https://jobsuche.api.bund.dev). On ne garde que
 * l'employeur (nom, ville, site web), jamais de données personnelles.
 */
@Service
public class BundesagenturService {

    private static final Logger log = LoggerFactory.getLogger(BundesagenturService.class);
    private static final String BASE = "https://rest.arbeitsagentur.de/jobboerse/jobsuche-service";
    private static final String API_KEY = "jobboerse-jobsuche"; // clé publique documentée

    /** Plateformes d'offres / ATS : leur domaine n'est pas celui de l'employeur. */
    private static final Pattern NOT_EMPLOYER = Pattern.compile(
            "arbeitsagentur|personio|softgarden|stepstone|indeed|linkedin|xing|greenhouse|smartrecruiters|workday|successfactors|"
            + "jobs\\.|join\\.com|recruitee|lever\\.co|onlyfy|prescreen|d\\.vinci|dvinci|rexx|umantis|concludis|kununu|monster|"
            + "jobware|stellenanzeigen|meinestadt|jobvector|heise|gulp|instaffo|absolventa|praktikum|google|facebook|instagram");
    private static final Pattern URL = Pattern.compile("https?://[a-zA-Z0-9.-]+\\.[a-z]{2,}[^\"\\s]*");

    private final RestClient rest;
    private final CompanyRepository companies;
    private final OutreachProperties props;

    public BundesagenturService(CompanyRepository companies, OutreachProperties props) {
        this.companies = companies;
        this.props = props;
        this.rest = RestClient.builder().baseUrl(BASE).defaultHeader("X-API-Key", API_KEY).build();
    }

    /** Lance toutes les requêtes (mots-clés x villes + remote) et enregistre les nouveaux employeurs. */
    public int fetchAll(String onlyCity) {
        List<String> cities = onlyCity != null ? List.of(onlyCity) : props.cities();
        int added = 0;
        for (String q : props.baQueries()) {
            for (String city : cities) {
                added += search(q, city, 25, false);
            }
            added += search(q, null, 0, true); // Homeoffice, toute l'Allemagne
        }
        log.info("Bundesagentur : {} nouveaux employeurs enregistrés", added);
        return added;
    }

    int search(String was, String wo, int umkreis, boolean homeoffice) {
        int added = 0;
        try {
            BaJobsResponseDto res = rest.get().uri((UriBuilder b) -> {
                b.path("/pc/v4/jobs").queryParam("was", was).queryParam("size", 100).queryParam("page", 1)
                        .queryParam("veroeffentlichtseit", 60);
                if (wo != null) b.queryParam("wo", wo).queryParam("umkreis", umkreis);
                if (homeoffice) b.queryParam("arbeitszeit", "ho");
                return b.build();
            }).retrieve().body(BaJobsResponseDto.class);
            Thread.sleep(1000);
            if (res == null || res.stellenangebote() == null) return 0;

            for (BaJobsResponseDto.Stelle s : res.stellenangebote()) {
                if (s.arbeitgeber() == null || !isRelevant(s)) continue;
                String website = employerWebsite(s);
                if (website == null) continue;
                String domain = domainOf(website);
                if (domain.isBlank() || !matchesName(s.arbeitgeber(), domain) || companies.existsByDomain(domain)) continue;

                String city = s.arbeitsort() != null ? s.arbeitsort().ort() : wo;
                Company c = new Company(s.arbeitgeber(), domain, "https://" + domain, null,
                        s.arbeitsort() != null ? s.arbeitsort().plz() : null, city);
                c.setRegion(s.arbeitsort() != null && s.arbeitsort().region() != null ? s.arbeitsort().region() : "Deutschland");
                c.setSource("BA");
                c.setBaJobTitle(s.titel());
                companies.save(c);
                added++;
                log.info("   + {} ({}) - offre : {}", s.arbeitgeber(), domain, s.titel());
            }
            log.info("BA '{}' {} : {} offres, {} nouveaux employeurs", was, homeoffice ? "[Homeoffice]" : wo,
                    res.stellenangebote().size(), added);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("BA '{}' {} : échec {}", was, wo, e.getMessage());
        }
        return added;
    }

    /** Offre tech ? (évite vente, pflege, etc.) */
    private boolean isRelevant(BaJobsResponseDto.Stelle s) {
        String t = ((s.titel() == null ? "" : s.titel()) + " " + (s.beruf() == null ? "" : s.beruf())).toLowerCase();
        return t.matches(".*(java|kotlin|spring|backend|software|entwickl|developer|devops|informatik|full.?stack|programm|it-).*");
    }

    /** 1) externeUrl si ce n'est pas un job board ; 2) sinon une URL employeur trouvée dans le détail de l'offre. */
    private String employerWebsite(BaJobsResponseDto.Stelle s) {
        if (s.externeUrl() != null && !NOT_EMPLOYER.matcher(s.externeUrl().toLowerCase()).find()) {
            return s.externeUrl();
        }
        if (s.refnr() == null) return null;
        String enc = Base64.getEncoder().encodeToString(s.refnr().getBytes(StandardCharsets.UTF_8));
        for (String path : List.of("/pc/v4/jobdetails/" + enc, "/pc/v2/jobdetails/" + enc)) {
            try {
                String json = rest.get().uri(path).retrieve().body(String.class);
                Thread.sleep(500);
                if (json == null) continue;
                Matcher m = URL.matcher(json);
                while (m.find()) {
                    String u = m.group();
                    if (!NOT_EMPLOYER.matcher(u.toLowerCase()).find()) return u;
                }
                return null;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception ignored) { /* on essaie la version suivante */ }
        }
        return null;
    }

    /** Garde-fou : le domaine doit ressembler au nom de l'employeur (évite de prendre un lien tiers). */
    static boolean matchesName(String employer, String domain) {
        String label = domain.split("\\.")[domain.split("\\.").length >= 2 ? domain.split("\\.").length - 2 : 0]
                .replaceAll("[^a-z0-9]", "");
        String name = employer.toLowerCase()
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        String compact = name.replaceAll("[^a-z0-9]", "");
        if (label.length() >= 3 && compact.contains(label)) return true;
        for (String token : name.split("[^a-z0-9]+")) {
            if (token.length() >= 4 && !token.matches("gmbh|mbh|group|gruppe|holding|deutschland|germany|systems|solutions|services|consulting") && label.contains(token)) {
                return true;
            }
        }
        return false;
    }

    static String domainOf(String url) {
        try {
            String host = new URI(url.trim()).getHost();
            if (host == null) return "";
            host = host.toLowerCase();
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return "";
        }
    }
}
