package com.claveri.Oneclickapply.service;

import com.claveri.Oneclickapply.config.OutreachProperties;
import com.claveri.Oneclickapply.domain.ApplicationStatus;
import com.claveri.Oneclickapply.domain.Company;
import com.claveri.Oneclickapply.domain.CoverLetter;
import com.claveri.Oneclickapply.repository.CompanyRepository;
import com.claveri.Oneclickapply.repository.CoverLetterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Génère un mail d'Initiativbewerbung personnalisé (nom, ville, stack détectée)
 * pour chaque entreprise avec e-mail et score suffisant. Statut -> DRAFT.
 */
@Service
public class DraftService {

    private static final Logger log = LoggerFactory.getLogger(DraftService.class);

    private final CompanyRepository companyRepository;
    private final CoverLetterRepository coverLetterRepository;
    private final OutreachProperties props;

    public DraftService(CompanyRepository companyRepository, CoverLetterRepository coverLetterRepository, OutreachProperties props) {
        this.companyRepository = companyRepository;
        this.coverLetterRepository = coverLetterRepository;
        this.props = props;
    }

    @Transactional
    public int createDrafts() {
        int created = 0;
        for (Company c : companyRepository.findByPrimaryEmailIsNotNullAndMatchScoreGreaterThanEqualOrderByMatchScoreDesc(props.minScore())) {
            if (coverLetterRepository.existsByCompanyId(c.getId())) continue; // jamais deux fois la même entreprise
            if (Boolean.TRUE.equals(c.getOptedOut())) continue;               // opt-out respecté
            CoverLetter cl = new CoverLetter(c, "Werkstudent Softwareentwicklung (Initiativbewerbung)");
            cl.setRecipient(c.getPrimaryEmail());
            cl.setSubject(subject(c));
            cl.setContentBody(body(c));
            cl.setStatus(ApplicationStatus.DRAFT);
            coverLetterRepository.save(cl);
            created++;
        }
        log.info("{} brouillons créés (score >= {})", created, props.minScore());
        return created;
    }

    String subject(Company c) {
        String focus = has(c, "java") || has(c, "spring") ? "Java / Spring Boot"
                : has(c, "kotlin") ? "Kotlin / JVM"
                : has(c, "devops") || has(c, "docker") || has(c, "kubernetes") ? "Backend & DevOps"
                : "Backend-Entwicklung";
        return "Initiativbewerbung als Werkstudent Softwareentwicklung (" + focus + ") – " + props.senderName();
    }

    String body(Company c) {
        String greeting = greeting(c.getContactPerson());
        String stackSentence = stackSentence(c);
        String where = c.getCity() != null && !c.getCity().isBlank()
                ? (c.getCity().toLowerCase().contains("bremen") ? "gerne vor Ort in Bremen oder hybrid"
                   : "hybrid in " + c.getCity() + " oder remote")
                : "remote oder hybrid";

        return """
                %s

                ich studiere Informatik (B.Sc.) an der Universität Bremen und entwickle seit über zwei Jahren als Werkstudent Backend-Systeme in Java. Ich möchte mich bei %s initiativ als Werkstudent in der Softwareentwicklung bewerben.%s

                Aktuell arbeite ich bei Next And Timeless Technology in Bremen an REST-APIs und Backend-Services mit Flask und habe dort einen Model-Context-Protocol-Server für die Verarbeitung sensibler Daten gebaut. Zuvor habe ich bei Niedersachsen Wasser Java-Anwendungen entwickelt und mit JUnit 5 und Mockito getestet, im Scrum-Team mit Code Reviews. Privat baue ich u. a. eine CI/CD-Automatisierung mit Spring AI und Ollama sowie einen Caching-Proxy in Java.

                Ich kann ab Oktober 2026 20 Stunden pro Woche arbeiten, %s. Deutsch C1, Englisch B2, Französisch Muttersprache. Meinen Lebenslauf finden Sie im Anhang, meine Projekte auf %s und %s.

                Über eine kurze Rückmeldung oder ein Kennenlernen würde ich mich sehr freuen.

                Mit freundlichen Grüßen
                %s
                %s · %s
                %s

                ---
                Ich habe Ihre Kontaktadresse Ihrem öffentlichen Impressum bzw. Ihrer Karriereseite entnommen und nutze sie ausschließlich für diese Bewerbung. Wenn Sie keine weitere Nachricht von mir wünschen, antworten Sie einfach kurz mit „Nein danke“ – ich lösche Ihre Daten dann und melde mich nicht mehr.
                """.formatted(greeting, c.getName(), stackSentence, where,
                props.githubUrl(), props.portfolioUrl(),
                props.senderName(), props.phone(), props.senderEmail(), props.linkedinUrl()).strip();
    }

    String followUpBody(CoverLetter cl) {
        Company c = cl.getCompany();
        return """
                %s

                vor einer Woche habe ich mich bei %s initiativ als Werkstudent in der Softwareentwicklung (Java / Spring Boot) beworben. Ich wollte kurz nachfragen, ob meine Bewerbung angekommen ist und ob es aktuell oder in den nächsten Monaten Bedarf an studentischer Unterstützung gibt.

                Meinen Lebenslauf habe ich noch einmal angehängt.

                Mit freundlichen Grüßen
                %s
                %s · %s

                ---
                Ich habe Ihre Kontaktadresse Ihrem öffentlichen Impressum bzw. Ihrer Karriereseite entnommen und nutze sie ausschließlich für diese Bewerbung. Wenn Sie keine weitere Nachricht von mir wünschen, antworten Sie einfach kurz mit „Nein danke“ – ich lösche Ihre Daten dann und melde mich nicht mehr.
                """.formatted(greeting(c.getContactPerson()), c.getName(),
                props.senderName(), props.phone(), props.senderEmail()).strip();
    }

    private String greeting(String contact) {
        if (contact != null && contact.startsWith("Herr ")) return "Sehr geehrter " + contact + ",";
        if (contact != null && contact.startsWith("Frau ")) return "Sehr geehrte " + contact + ",";
        return "Sehr geehrte Damen und Herren,";
    }

    private String stackSentence(Company c) {
        String job = c.getBaJobTitle() != null
                ? " Ich habe gesehen, dass Sie aktuell eine Stelle als „" + c.getBaJobTitle().trim() + "“ ausgeschrieben haben – das zeigt mir, dass Ihr Team im Bereich Softwareentwicklung wächst."
                : "";
        if (c.getTechStack() == null || c.getTechStack().isBlank()) return job;
        String stack = stackOnly(c);
        return job + stack;
    }

    private String stackOnly(Company c) {
        List<String> mine = List.of("java", "spring", "kotlin", "docker", "kubernetes", "devops", "typescript", "react", "vue", "postgres", "microservice", "rest api", "llm");
        List<String> common = Arrays.stream(c.getTechStack().split(",\\s*"))
                .filter(mine::contains).limit(3)
                .map(DraftService::pretty).collect(Collectors.toList());
        if (common.isEmpty()) return "";
        String list = common.size() == 1 ? common.get(0)
                : String.join(", ", common.subList(0, common.size() - 1)) + " und " + common.get(common.size() - 1);
        return " Auf Ihrer Website habe ich gesehen, dass Sie mit " + list + " arbeiten – genau die Technologien, mit denen ich täglich arbeite.";
    }

    private static String pretty(String kw) {
        return switch (kw) {
            case "java" -> "Java";
            case "spring" -> "Spring";
            case "kotlin" -> "Kotlin";
            case "docker" -> "Docker";
            case "kubernetes" -> "Kubernetes";
            case "devops" -> "DevOps";
            case "typescript" -> "TypeScript";
            case "react" -> "React";
            case "vue" -> "Vue.js";
            case "postgres" -> "PostgreSQL";
            case "microservice" -> "Microservices";
            case "rest api" -> "REST-APIs";
            case "llm" -> "LLMs";
            default -> kw;
        };
    }

    private boolean has(Company c, String kw) {
        return c.getTechStack() != null && Arrays.asList(c.getTechStack().split(",\\s*")).contains(kw);
    }
}
