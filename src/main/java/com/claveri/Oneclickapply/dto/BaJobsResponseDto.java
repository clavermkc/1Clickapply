package com.claveri.Oneclickapply.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Réponse de l'API publique Jobsuche de la Bundesagentur für Arbeit (champs utiles uniquement). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BaJobsResponseDto(List<Stelle> stellenangebote, Integer maxErgebnisse) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stelle(String titel, String beruf, String refnr, String arbeitgeber,
                         Arbeitsort arbeitsort, String externeUrl) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Arbeitsort(String plz, String ort, String region, String strasse) {}
}
