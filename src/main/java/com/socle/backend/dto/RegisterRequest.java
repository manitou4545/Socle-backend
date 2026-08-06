package com.socle.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class RegisterRequest {
    @NotBlank
    public String name;

    @NotBlank
    @Pattern(regexp = "^\\d{2}A\\d{4}EM$", flags = Pattern.Flag.CASE_INSENSITIVE,
             message = "Format attendu : 22A0199EM")
    public String matricule;

    @NotBlank
    public String filiere;

    public Integer niveau;
}
