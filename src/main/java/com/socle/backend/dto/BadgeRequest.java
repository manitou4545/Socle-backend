package com.socle.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class BadgeRequest {
    @NotBlank
    public String matricule;
    @NotBlank
    public String badgeKey;
    public String docTitre;
}
