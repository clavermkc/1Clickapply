package com.claveri.Oneclickapply.service;

import com.claveri.Oneclickapply.config.OutreachProperties;
import com.claveri.Oneclickapply.domain.Company;
import com.claveri.Oneclickapply.repository.CompanyRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Visite le site d'une entreprise pour :
 *  1. trouver une adresse e-mail (Impressum en priorité : obligatoire en Allemagne, §5 DDG),
 *  2. détecter la stack technique et calculer un score de correspondance avec le profil.
 */
@Service
public class ScraperService {

    private static final Logger log = LoggerFactory.getLogger(ScraperService.class);
    /** User-Agent honnête : on s'identifie au lieu de se faire passer pour un navigateur. */
    private static final String USER_AGENT = "1ClickApply-Bewerbungsbot/1.0 (Initiativbewerbung eines Studenten; Kontakt: monthekamenic@gmail.com)";
    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_PAGES = 7;

    /** Chemins testés directement, même si aucun lien n'y mène depuis la page d'accueil. */
    private static final List<String> GUESSED_PATHS = List.of(
            "/impressum", "/karriere", "/jobs", "/kontakt", "/de/impressum", "/de/karriere");

    private static final Pattern EMAIL = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    /** Mots-clés du profil et leur poids. */
    private static final Map<String, Integer> KEYWORDS = new LinkedHashMap<>();
    static {
        KEYWORDS.put("java", 3);
        KEYWORDS.put("spring", 3);
        KEYWORDS.put("kotlin", 2);
        KEYWORDS.put("jakarta ee", 2);
        KEYWORDS.put("backend", 1);
        KEYWORDS.put("microservice", 1);
        KEYWORDS.put("rest api", 1);
        KEYWORDS.put("devops", 1);
        KEYWORDS.put("docker", 1);
        KEYWORDS.put("kubernetes", 1);
        KEYWORDS.put("ci/cd", 1);
        KEYWORDS.put("typescript", 1);
        KEYWORDS.put("react", 1);
        KEYWORDS.put("vue", 1);
        KEYWORDS.put("postgres", 1);
        KEYWORDS.put("softwareentwicklung", 1);
        KEYWORDS.put("software development", 1);
        KEYWORDS.put("llm", 1);
        KEYWORDS.put("künstliche intelligenz", 1);
        // Signaux "entreprise de logiciel" (même si le site ne cite pas Java)
        KEYWORDS.put("software", 1);
        KEYWORDS.put("softwareentwickler", 1);
        KEYWORDS.put("developer", 1);
        KEYWORDS.put("informatik", 1);
        KEYWORDS.put("saas", 1);
        KEYWORDS.put("cloud", 1);
        KEYWORDS.put("scrum", 1);
        KEYWORDS.put("api", 1);
    }
    private static final Pattern STUDENT = Pattern.compile("werkstudent|working student|studentische", Pattern.CASE_INSENSITIVE);

    private final CompanyRepository companyRepository;
    private final OutreachProperties props;

    public ScraperService(CompanyRepository companyRepository, OutreachProperties props) {
        this.companyRepository = companyRepository;
        this.props = props;
    }

    public void enrichCompanyData(Company company) {
        company.setScrapedAt(LocalDateTime.now());
        if (company.getWebsiteUrl() == null || company.getWebsiteUrl().isBlank()) {
            if (!debugMode) companyRepository.save(company);
            return;
        }

        Set<String> visited = new HashSet<>();
        List<String> emails = new ArrayList<>();
        List<String> careerEmails = new ArrayList<>(); // adresses publiées sur une page Karriere/Jobs
        StringBuilder allText = new StringBuilder();

        try {
            String base = baseUrl(company.getWebsiteUrl());
            Document home = fetch(company.getWebsiteUrl());
            if (home == null) {
                if (!debugMode) companyRepository.save(company);
                return;
            }
            visited.add(normalize(company.getWebsiteUrl()));
            collect(home, emails, allText);

            // 1) Liens pertinents trouvés sur la page d'accueil (même domaine uniquement)
            Deque<String> queue = new ArrayDeque<>();
            for (Element a : home.select("a[href]")) {
                String href = a.attr("abs:href");
                if (isSameSite(href, company.getDomain()) && isTargetLink(href, a.text())) {
                    // Impressum / Kontakt en tête de file
                    if (href.toLowerCase().contains("impressum") || href.toLowerCase().contains("imprint")) queue.addFirst(href);
                    else queue.addLast(href);
                }
            }
            // 2) Chemins devinés
            for (String p : GUESSED_PATHS) queue.addLast(base + p);

            int pages = 1;
            while (!queue.isEmpty() && pages < MAX_PAGES) {
                String url = queue.pollFirst();
                if (!visited.add(normalize(url))) continue;
                Document doc = fetch(url);
                pages++;
                if (doc == null) continue;
                collect(doc, emails, allText);
                if (url.toLowerCase().matches(".*(karriere|career|jobs|stellen|bewerb).*")) {
                    careerEmails.addAll(extractEmails(doc));
                }
                if (company.getContactPerson() == null) {
                    extractContactPerson(doc).ifPresent(company::setContactPerson);
                }
                // Arrêt anticipé : on a un mail RH + assez de texte pour le matching
                if (hasHrEmail(emails) && pages >= 4) break;
            }

            selectBestEmail(emails, careerEmails, company.getDomain()).ifPresent(company::setPrimaryEmail);
            if (company.getBaJobTitle() != null) {
                // L'entreprise recrute déjà un profil tech : le titre de l'offre compte dans le score
                allText.append(' ').append(company.getBaJobTitle());
            }
            scoreProfile(company, allText.toString().toLowerCase());
            if (debugMode) {
                log.info("E-mails bruts trouvés : {}", emails.stream().distinct().toList());
            } else {
                companyRepository.save(company);
            }
            log.info("[{}] score={} stack=[{}] email={}", company.getName(), company.getMatchScore(),
                    company.getTechStack(), company.getPrimaryEmail());

        } catch (Exception e) {
            log.warn("Erreur scraping {} : {}", company.getName(), e.getMessage(), e);
            if (!debugMode) companyRepository.save(company);
        }
    }

    // ---------------------------------------------------------------- helpers

    private void collect(Document doc, List<String> emails, StringBuilder text) {
        emails.addAll(extractEmails(doc));
        text.append(' ').append(doc.text());
    }

    /** Cache robots.txt par hôte : liste des préfixes interdits pour User-agent: *. */
    private final Map<String, List<String>> robotsCache = new HashMap<>();

    /** Respecte robots.txt (règles "User-agent: *" et "1ClickApply"). */
    boolean allowedByRobots(String url) {
        try {
            URI u = new URI(url);
            String host = u.getScheme() + "://" + u.getHost();
            List<String> disallow = robotsCache.computeIfAbsent(host, h -> {
                List<String> rules = new ArrayList<>();
                try {
                    String txt = Jsoup.connect(h + "/robots.txt").userAgent(USER_AGENT).timeout(TIMEOUT_MS)
                            .ignoreContentType(true).execute().body();
                    rules.addAll(parseRobots(txt));
                } catch (Exception ignored) { /* pas de robots.txt = tout est permis */ }
                return rules;
            });
            String path = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();
            for (String d : disallow) {
                if (d.equals("/") || path.startsWith(d)) {
                    log.info("   robots.txt interdit {} -> ignoré", url);
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    static List<String> parseRobots(String txt) {
        List<String> rules = new ArrayList<>();
        boolean applies = false;
        for (String raw : txt.split("\\R")) {
            String line = raw.replaceAll("#.*$", "").trim();
            if (line.isEmpty()) continue;
            String lower = line.toLowerCase();
            if (lower.startsWith("user-agent:")) {
                String ua = lower.substring(11).trim();
                applies = ua.equals("*") || ua.contains("1clickapply");
            } else if (applies && lower.startsWith("disallow:")) {
                String d = line.substring(9).trim();
                if (!d.isEmpty()) rules.add(d);
            }
        }
        return rules;
    }

    private Document fetch(String url) {
        if (!allowedByRobots(url)) return null;
        try {
            Thread.sleep(props.scrapeDelayMillis()); // politesse envers les serveurs
            Document d = Jsoup.connect(url).userAgent(USER_AGENT).timeout(TIMEOUT_MS)
                    .header("Accept-Language", "de-DE,de;q=0.9")
                    .followRedirects(true).ignoreHttpErrors(false).get();
            log.info("   GET {} -> OK ({} car., {} e-mail(s))", url, d.text().length(), extractEmails(d).size());
            return d;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.info("   GET {} -> ÉCHEC : {} {}", url, e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /** Diagnostic : scrape une seule URL sans rien enregistrer. */
    public void debugUrl(String url) {
        Company c = new Company();
        String host = url.replaceFirst("^https?://", "").replaceFirst("^www\\.", "").replaceFirst("/.*$", "");
        c.setName(host);
        c.setDomain(host);
        c.setWebsiteUrl(url);
        debugMode = true;
        enrichCompanyData(c);
    }

    private boolean debugMode = false;

    private boolean isTargetLink(String href, String text) {
        String h = href.toLowerCase(), t = text.toLowerCase();
        return h.contains("impressum") || h.contains("imprint") || h.contains("kontakt") || h.contains("contact")
                || h.contains("karriere") || h.contains("career") || h.contains("jobs") || h.contains("stellen")
                || h.contains("team") || h.contains("technolog") || h.contains("leistungen")
                || t.contains("impressum") || t.contains("karriere") || t.contains("jobs") || t.contains("kontakt");
    }

    List<String> extractEmails(Document doc) {
        List<String> out = new ArrayList<>();
        // mailto:
        for (Element a : doc.select("a[href^=mailto:]")) {
            String m = a.attr("href").substring(7).split("\\?")[0].trim();
            if (EMAIL.matcher(m).matches()) out.add(m.toLowerCase());
        }
        // Protection Cloudflare (data-cfemail)
        for (Element e : doc.select("[data-cfemail]")) {
            decodeCloudflare(e.attr("data-cfemail")).ifPresent(out::add);
        }
        // Texte avec obfuscations courantes : "info (at) firma (dot) de", "info[at]firma.de", "info @ firma.de"
        String txt = doc.text()
                .replaceAll("\\s*[\\[(\\{]\\s*(?i:at|ät)\\s*[\\])\\}]\\s*", "@")
                .replaceAll("\\s*[\\[(\\{]\\s*(?i:dot|punkt)\\s*[\\])\\}]\\s*", ".")
                .replaceAll("\\s+@\\s+", "@");
        Matcher m = EMAIL.matcher(txt);
        while (m.find()) out.add(m.group().toLowerCase());
        out.removeIf(e -> e.matches(".*\\.(png|jpe?g|gif|svg|webp)$") || e.contains("example.") || e.contains("sentry"));
        return out;
    }

    static Optional<String> decodeCloudflare(String hex) {
        try {
            int key = Integer.parseInt(hex.substring(0, 2), 16);
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < hex.length(); i += 2) {
                sb.append((char) (Integer.parseInt(hex.substring(i, i + 2), 16) ^ key));
            }
            String email = sb.toString().toLowerCase();
            return EMAIL.matcher(email).matches() ? Optional.of(email) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static final List<String> HR_PREFIXES = List.of("bewerbung", "jobs", "job", "karriere", "career", "careers", "hr", "personal", "recruiting", "talent");
    private static final List<String> GENERIC_PREFIXES = List.of("info", "kontakt", "contact", "hallo", "hello", "mail", "office", "team");
    private static final List<String> BLOCKED_PREFIXES = List.of("datenschutz", "privacy", "dsb", "noreply", "no-reply", "webmaster", "abuse", "presse", "press", "media", "ir@", "investor", "rechnung", "buchhaltung", "support", "service", "inclusion");

    private boolean hasHrEmail(List<String> emails) {
        return emails.stream().anyMatch(e -> HR_PREFIXES.stream().anyMatch(p -> e.startsWith(p)));
    }

    /** Rétrocompatibilité (tests) : sans information sur les pages carrière. */
    Optional<String> selectBestEmail(List<String> emails, String domain) {
        return selectBestEmail(emails, List.of(), domain);
    }

    /**
     * Choisit la meilleure adresse, par ordre de priorité. On ne rejette que les boîtes qui
     * n'atteindront jamais un recruteur (datenschutz@, noreply@, presse@, investor relations...).
     * Une adresse nominative publiée sur la page Karriere est souvent celle du/de la recruteur·se :
     * elle est acceptée. Dans tous les cas, tu valides l'adresse dans review.csv avant l'envoi.
     */
    Optional<String> selectBestEmail(List<String> emails, List<String> careerEmails, String domain) {
        List<String> candidates = emails.stream().distinct()
                .filter(e -> BLOCKED_PREFIXES.stream().noneMatch(e::startsWith))
                .toList();
        if (candidates.isEmpty()) return Optional.empty();

        java.util.function.Predicate<String> sameDomain = e -> domain != null
                && (e.endsWith("@" + domain) || e.endsWith("." + domain));
        java.util.function.Predicate<String> hr = e -> HR_PREFIXES.stream().anyMatch(e::startsWith);
        java.util.function.Predicate<String> generic = e -> GENERIC_PREFIXES.stream().anyMatch(e::startsWith);
        java.util.function.Predicate<String> onCareerPage = careerEmails::contains;

        List<java.util.function.Predicate<String>> tiers = List.of(
                sameDomain.and(hr),            // 1. jobs@firma.de
                sameDomain.and(onCareerPage),  // 2. anna.mueller@firma.de publiée sur /karriere
                sameDomain.and(generic),       // 3. info@firma.de
                sameDomain,                    // 4. toute autre adresse du domaine
                hr,                            // 5. jobs@firma-gruppe.de (domaine du groupe)
                onCareerPage,                  // 6. adresse d'un autre domaine publiée sur /karriere
                generic,                       // 7. info@... (ex. petite entreprise en @gmx.de)
                e -> true                      // 8. dernier recours : tu vérifieras dans review.csv
        );
        for (var tier : tiers) {
            Optional<String> hit = candidates.stream().filter(tier).findFirst();
            if (hit.isPresent()) return hit;
        }
        return Optional.empty();
    }

    void scoreProfile(Company c, String text) {
        int score = 0;
        List<String> stack = new ArrayList<>();
        for (var kw : KEYWORDS.entrySet()) {
            if (containsWord(text, kw.getKey())) {
                score += kw.getValue();
                stack.add(kw.getKey());
            }
        }
        boolean students = STUDENT.matcher(text).find();
        if (students) score += 2;
        c.setMatchScore(score);
        c.setTechStack(String.join(", ", stack));
        c.setHiresStudents(students);
    }

    /** "java" ne doit pas matcher "javascript". */
    private boolean containsWord(String text, String kw) {
        return Pattern.compile("(?<![a-z])" + Pattern.quote(kw) + "(?![a-z])").matcher(text).find();
    }

    private Optional<String> extractContactPerson(Document doc) {
        Matcher m = Pattern.compile("(?:Ansprechpartner(?:in)?|Kontaktperson|Contact Person)\\s*:?\\s*(Herr|Frau)?\\s*([A-ZÄÖÜ][a-zäöüß]+\\s+[A-ZÄÖÜ][a-zäöüß-]+)")
                .matcher(doc.text());
        if (m.find() && m.group(1) != null) {
            return Optional.of(m.group(1) + " " + m.group(2));
        }
        return Optional.empty();
    }

    private boolean isSameSite(String href, String domain) {
        try {
            String host = new URI(href).getHost();
            return host != null && domain != null && (host.equals(domain) || host.endsWith("." + domain));
        } catch (Exception e) {
            return false;
        }
    }

    private String baseUrl(String url) {
        try {
            URI u = new URI(url.startsWith("http") ? url : "https://" + url);
            return u.getScheme() + "://" + u.getHost();
        } catch (Exception e) {
            return url;
        }
    }

    private String normalize(String url) {
        return url.toLowerCase().replaceAll("[#?].*$", "").replaceAll("/+$", "");
    }
}
