package com.claveri.Oneclickapply.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Paramètres de la campagne d'Initiativbewerbungen (voir application.properties).
 */
@ConfigurationProperties(prefix = "outreach")
public record OutreachProperties(
        List<String> cities,
        int minScore,
        int dailyLimit,
        int minDelaySeconds,
        int maxDelaySeconds,
        int followUpAfterDays,
        String cvPath,
        String reviewCsvPath,
        String senderName,
        String senderEmail,
        String phone,
        String portfolioUrl,
        String githubUrl,
        String linkedinUrl,
        int scrapeDelayMillis,
        List<String> baQueries
) {}
