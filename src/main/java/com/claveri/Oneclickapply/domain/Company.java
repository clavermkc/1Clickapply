package com.claveri.Oneclickapply.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Setter
@Entity
@Table(name = "companies", indexes = {
    @Index(name = "idx_company_domain", columnList = "domain", unique = true)
})
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String domain;

    private String streetAndNumber;
    private String zipCode;
    private String city;
    private String websiteUrl;
    private String primaryEmail;
    private String contactPerson;

    @Column(nullable = false)
    private String region = "Niedersachsen";

    /** Technologies détectées sur le site (ex: "Java, Spring, Docker"). */
    private String techStack;

    /** Score de correspondance avec le profil (plus haut = mieux). */
    private Integer matchScore;

    /** true si la page carrière mentionne Werkstudent / studentische Mitarbeit. */
    private Boolean hiresStudents;

    /** true = l'entreprise a demandé à ne plus être contactée (opt-out). Plus aucun mail, jamais. */
    private Boolean optedOut = false;

    /** Origine : "OSM" (OpenStreetMap) ou "BA" (Bundesagentur für Arbeit). */
    private String source;

    /** Titre d'une offre tech publiée par l'entreprise sur la Jobbörse (si source = BA). */
    private String baJobTitle;

    /** Date du dernier scraping (null = jamais scrapé). */
    private LocalDateTime scrapedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Company() {}

    public Company(String name, String domain, String websiteUrl, String streetAndNumber, String zipCode, String city) {
        this.name = name;
        this.domain = domain;
        this.websiteUrl = websiteUrl;
        this.streetAndNumber = streetAndNumber;
        this.zipCode = zipCode;
        this.city = city;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Company company = (Company) o;
        return Objects.equals(domain, company.domain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(domain);
    }
}
