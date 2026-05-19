package com.example.platform.federation.graphql.dto;

public record UsageSummary(
        Double renderMinutes,
        Double storageGb,
        Integer apiCalls
) {}
