package com.claveri.Oneclickapply.service;

import com.claveri.Oneclickapply.config.OutreachProperties;
import com.claveri.Oneclickapply.domain.ApplicationStatus;
import com.claveri.Oneclickapply.domain.Company;
import com.claveri.Oneclickapply.domain.CoverLetter;
import com.claveri.Oneclickapply.repository.CoverLetterRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validation humaine : export des brouillons dans review.csv (ouvrable dans Excel / LibreOffice),
 * tu mets "x" dans la colonne approve (ou "n" pour refuser), tu peux corriger email / sujet / texte,
 * puis import -> APPROVED ou SKIPPED. Rien n'est envoyé sans ce passage.
 */
@Service
public class ReviewCsvService {

    private static final Logger log = LoggerFactory.getLogger(ReviewCsvService.class);
    private static final String[] HEADERS = {"approve", "id", "company", "city", "website", "score", "stack", "werkstudent_page", "recipient", "subject", "body"};

    private final CoverLetterRepository coverLetterRepository;
    private final OutreachProperties props;

    public ReviewCsvService(CoverLetterRepository coverLetterRepository, OutreachProperties props) {
        this.coverLetterRepository = coverLetterRepository;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public int export() throws IOException {
        var drafts = coverLetterRepository.findByStatusWithCompany(ApplicationStatus.DRAFT);
        CSVFormat fmt = CSVFormat.DEFAULT.builder().setHeader(HEADERS).setDelimiter(';').build();
        try (Writer w = Files.newBufferedWriter(Path.of(props.reviewCsvPath()), StandardCharsets.UTF_8)) {
            w.write('﻿'); // BOM pour qu'Excel lise l'UTF-8 (umlauts)
            try (CSVPrinter p = new CSVPrinter(w, fmt)) {
                for (CoverLetter cl : drafts) {
                    Company c = cl.getCompany();
                    p.printRecord("", cl.getId(), c.getName(), c.getCity(), c.getWebsiteUrl(), c.getMatchScore(),
                            c.getTechStack(), Boolean.TRUE.equals(c.getHiresStudents()) ? "oui" : "",
                            cl.getRecipient(), cl.getSubject(), cl.getContentBody());
                }
            }
        }
        log.info("{} brouillons exportés dans {} -> mets 'x' dans la colonne approve puis lance --mode=import",
                drafts.size(), props.reviewCsvPath());
        return drafts.size();
    }

    @Transactional
    public void importDecisions() throws IOException {
        int approved = 0, skipped = 0;
        CSVFormat fmt = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setDelimiter(';').build();
        try (Reader r = Files.newBufferedReader(Path.of(props.reviewCsvPath()), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(r, fmt)) {
            for (CSVRecord rec : parser) {
                String decision = rec.get(0).replace("﻿", "").trim().toLowerCase(); // 1re colonne = approve
                String idText = rec.isMapped("id") && rec.isSet("id") ? rec.get("id").trim() : "";
                if (!idText.matches("\\d+")) {
                    // Ligne vide ou modifiée à la main dans LibreOffice : on l'ignore au lieu de tout planter
                    log.warn("Ligne {} ignorée (id invalide : '{}')", rec.getRecordNumber() + 1, idText);
                    continue;
                }
                Long id = Long.parseLong(idText);
                CoverLetter cl = coverLetterRepository.findById(id).orElse(null);
                if (cl == null || cl.getStatus() != ApplicationStatus.DRAFT) continue;
                if (decision.equals("x") || decision.equals("y") || decision.equals("oui")) {
                    // Les corrections faites à la main dans le CSV sont reprises
                    String recipient = rec.get("recipient").trim();
                    if (!recipient.contains("@")) {
                        log.warn("Ligne {} ignorée : adresse e-mail invalide '{}'", rec.getRecordNumber() + 1, recipient);
                        continue;
                    }
                    cl.setRecipient(recipient);
                    cl.setSubject(rec.get("subject").trim());
                    cl.setContentBody(rec.get("body").trim());
                    cl.setStatus(ApplicationStatus.APPROVED);
                    approved++;
                } else if (decision.equals("n") || decision.equals("non")) {
                    cl.setStatus(ApplicationStatus.SKIPPED);
                    skipped++;
                }
                coverLetterRepository.save(cl);
            }
        }
        log.info("Import : {} validés, {} refusés, le reste reste en DRAFT", approved, skipped);
    }
}
