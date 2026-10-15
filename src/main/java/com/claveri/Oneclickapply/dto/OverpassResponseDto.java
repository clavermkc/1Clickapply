package com.claveri.Oneclickapply.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OverpassResponseDto(List<Element> elements) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Element(
        long id,
        String type,
        Map<String, String> tags
    ) {
        public String getTag(String key) {
            return tags != null ? tags.get(key) : null;
        }
    }
}
