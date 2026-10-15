package com.claveri.Oneclickapply.service;

import com.claveri.Oneclickapply.config.OutreachProperties;
import com.claveri.Oneclickapply.domain.ApplicationStatus;
import com.claveri.Oneclickapply.domain.CoverLetter;
import com.claveri.Oneclickapply.repository.CoverLetterRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Envoie les mails APPROVED (jamais DRAFT) + les relances à J+N,
 * avec une limite quotidienne et un délai aléatoire entre deux envois.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final CoverLetterRepository repo;
    private final DraftService draftService;
    private final OutreachProperties props;

    public MailService(JavaMailSender mailSender, CoverLetterRepository repo, DraftService draftService, OutreachProperties props) {
        this.mailSender = mailSender;
        this.repo = repo;
        this.draftService = draftService;
        this.props = props;
    }

    /** @param dryRun true = affiche seulement ce qui partirait, n'envoie rien. */
    public void sendBatch(boolean dryRun) throws InterruptedException {
        File cv = new File(props.cvPath());
        if (!cv.isFile()) {
            throw new IllegalStateException("CV introuvable : " + cv.getAbsolutePath() + " (variable CV_PATH)");
        }
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long alreadyToday = repo.countBySentAtAfter(startOfDay) + repo.countByFollowUpSentAtAfter(startOfDay);
        long budget = props.dailyLimit() - alreadyToday;
        if (budget <= 0) {
            log.info("Limite quotidienne atteinte ({}). Rien à envoyer aujourd'hui.", props.dailyLimit());
            return;
        }

        // 1) Relances en priorité (elles ont le meilleur taux de réponse)
        List<CoverLetter> followUps = repo.findDueForFollowUp(LocalDateTime.now().minusDays(props.followUpAfterDays()));
        // 2) Nouvelles candidatures validées, meilleurs scores d'abord
        List<CoverLetter> fresh = repo.findByStatusWithCompany(ApplicationStatus.APPROVED);
        log.info("Budget du jour : {} | relances dues : {} | candidatures validées : {}", budget, followUps.size(), fresh.size());

        int sent = 0;
        for (CoverLetter cl : followUps) {
            if (sent >= budget) break;
            String subject = "Re: " + cl.getSubject();
            if (send(cl, subject, draftService.followUpBody(cl), cv, dryRun)) {
                if (!dryRun) {
                    cl.setFollowUpSentAt(LocalDateTime.now());
                    cl.setStatus(ApplicationStatus.FOLLOWED_UP);
                    repo.save(cl);
                }
                sent++;
                pause(dryRun, sent < budget);
            }
        }
        for (CoverLetter cl : fresh) {
            if (sent >= budget) break;
            if (send(cl, cl.getSubject(), cl.getContentBody(), cv, dryRun)) {
                if (!dryRun) {
                    cl.setSentAt(LocalDateTime.now());
                    cl.setStatus(ApplicationStatus.SENT);
                    repo.save(cl);
                }
                sent++;
                pause(dryRun, sent < budget);
            }
        }
        log.info("Terminé : {} mail(s) {}.", sent, dryRun ? "simulés (dry-run)" : "envoyés");
    }

    private boolean send(CoverLetter cl, String subject, String body, File cv, boolean dryRun) {
        String company = cl.getCompany().getName();
        if (Boolean.TRUE.equals(cl.getCompany().getOptedOut())) {
            log.info("Opt-out : {} ne sera pas contacté.", company);
            cl.setStatus(ApplicationStatus.SKIPPED);
            repo.save(cl);
            return false;
        }
        if (dryRun) {
            log.info("[DRY-RUN] -> {} <{}> | {}", company, cl.getRecipient(), subject);
            return true;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setFrom(props.senderEmail(), props.senderName());
            h.setTo(cl.getRecipient());
            h.setSubject(subject);
            h.setText(body, false);
            h.addAttachment("Lebenslauf_" + props.senderName().replace(' ', '_') + ".pdf", new FileSystemResource(cv));
            mailSender.send(msg);
            log.info("Envoyé -> {} <{}>", company, cl.getRecipient());
            return true;
        } catch (Exception e) {
            log.warn("Échec d'envoi à {} : {}", cl.getRecipient(), e.getMessage());
            cl.setLastError(e.getMessage());
            cl.setStatus(ApplicationStatus.FAILED);
            repo.save(cl);
            return false;
        }
    }

    private void pause(boolean dryRun, boolean more) throws InterruptedException {
        if (dryRun || !more) return;
        int s = ThreadLocalRandom.current().nextInt(props.minDelaySeconds(), props.maxDelaySeconds() + 1);
        log.info("Pause de {} s avant le prochain envoi...", s);
        Thread.sleep(s * 1000L);
    }
}
