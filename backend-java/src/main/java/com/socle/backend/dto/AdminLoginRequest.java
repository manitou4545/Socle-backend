package com.socle.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class AdminLoginRequest {
    @NotBlank
    public String password;
}
