package com.example.platform.billing.domain;

import com.example.platform.shared.commercial.Money;
public record InvoiceCommandResult(String invoiceId, InvoiceStatus status, long version, Money total) {}
