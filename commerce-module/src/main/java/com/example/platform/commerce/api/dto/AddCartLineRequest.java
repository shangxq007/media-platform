package com.example.platform.commerce.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AddCartLineRequest(
        @NotBlank String productCode,
        int quantity) {
}
