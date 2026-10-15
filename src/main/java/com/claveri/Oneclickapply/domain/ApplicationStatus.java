package com.claveri.Oneclickapply.domain;

public enum ApplicationStatus {
    SCRAPED,
    DRAFT,          // Mail généré, en attente de validation humaine (CSV)
    AI_TAILORED,
    EDITED,
    APPROVED,       // Validé dans review.csv (colonne approve = x)
    READY_TO_SEND,
    SENT,
    FOLLOWED_UP,    // Relance envoyée
    SKIPPED,        // Refusé lors de la validation
    FAILED
}
