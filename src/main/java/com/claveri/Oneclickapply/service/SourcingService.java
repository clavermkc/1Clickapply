package com.claveri.Oneclickapply.service;

import com.claveri.Oneclickapply.domain.Company;
import com.claveri.Oneclickapply.dto.OverpassResponseDto;
import com.claveri.Oneclickapply.repository.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class SourcingService {

    private static final Logger log = LoggerFactory.getLogger(SourcingService.class);
    private static final String OVERPASS_API_URL = "https://overpass-api.de/api";

    private final RestClient restClient;
    private final CompanyRepository companyRepository;

    public SourcingService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
        this.restClient = RestClient.builder()
                .baseUrl(OVERPASS_API_URL)
                .build();
    }

    @Transactional
    public List<Company> fetchAndSaveFromOverpass(String city) {
        String overpassQuery = buildQuery(city);
        log.info("Lancement de la collecte Overpass pour la ville : {}", city);

        try {
            OverpassResponseDto response = restClient.post()
                    .uri("/interpreter")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("data=" + URLEncoder.encode(overpassQuery, StandardCharsets.UTF_8))
                    .retrieve()
                    .body(OverpassResponseDto.class);

            if (response == null || response.elements() == null) {
                return List.of();
            }

            List<Company> savedCompanies = new ArrayList<>();

            for (OverpassResponseDto.Element element : response.elements()) {
                String name = element.getTag("name");
                String website = element.getTag("website");

                if (website == null || website.isBlank()) {
                    website = element.getTag("contact:website");
                }

                if (name == null || website == null) {
                    continue;
                }

                String domain = extractDomain(website);
                if (domain.isBlank() || companyRepository.existsByDomain(domain)) {
                    continue;
                }

                String street = element.getTag("addr:street");
                String houseNumber = element.getTag("addr:housenumber");
                String streetAndNum = (street != null ? street : "") + (houseNumber != null ? " " + houseNumber : "");
                String postcode = element.getTag("addr:postcode");
                String elementCity = element.getTag("addr:city") != null ? element.getTag("addr:city") : city;

                Company company = new Company(
                        name,
                        domain,
                        website.startsWith("http") ? website : "https://" + website,
                        streetAndNum.trim(),
                        postcode,
                        elementCity
                );

                savedCompanies.add(companyRepository.save(company));
            }

            log.info("{} nouvelles entreprises enregistrées pour {}", savedCompanies.size(), city);
            return savedCompanies;

        } catch (Exception e) {
            log.error("Erreur lors de la récupération Overpass pour {}", city, e);
            return List.of();
        }
    }

    private String buildQuery(String city) {
        return """
            [out:json][timeout:60];
            area["name"="%s"]["admin_level"~"[4|6|8]"]->.searchArea;
            (
              node["office"="it"](area.searchArea);
              way["office"="it"](area.searchArea);
              node["office"="company"](area.searchArea);
              way["office"="company"](area.searchArea);
              node["office"="coworking"](area.searchArea);
              way["office"="coworking"](area.searchArea);
              node["shop"="computer"](area.searchArea);
              way["shop"="computer"](area.searchArea);
            );
            out center tags;
            """.formatted(city);
    }

    private String extractDomain(String url) {
        try {
            String cleanUrl = url.trim().toLowerCase();
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                cleanUrl = "https://" + cleanUrl;
            }
            URI uri = new URI(cleanUrl);
            String host = uri.getHost();
            if (host != null) {
                return host.startsWith("www.") ? host.substring(4) : host;
            }
        } catch (Exception ignored) {}
        return "";
    }
}
