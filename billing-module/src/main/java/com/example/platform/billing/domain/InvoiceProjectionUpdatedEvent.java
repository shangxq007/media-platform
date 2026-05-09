package com.example.platform.billing.domain;

public record InvoiceProjectionUpdatedEvent(String invoiceId, String subjectId, String invoiceStatus) {}
