package com.example.platform.billing.app;

import com.example.platform.billing.domain.*;
import com.example.platform.billing.infrastructure.BillingInvoiceRepository;
import com.example.platform.billing.infrastructure.SubscriptionContractRepository;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BillingProjectionService {

    private static final Logger log = LoggerFactory.getLogger(BillingProjectionService.class);

    private final Map<String, BillingState> states = new ConcurrentHashMap<>();
    private final Map<String, SubscriptionContract> contracts = new ConcurrentHashMap<>();
    private final Map<String, InvoiceProjectionUpdatedEvent> invoices = new ConcurrentHashMap<>();
    private final SubscriptionContractRepository subscriptionContractRepository;
    private final BillingInvoiceRepository billingInvoiceRepository;

    public BillingProjectionService(@Autowired(required = false) SubscriptionContractRepository subscriptionContractRepository,
                                    @Autowired(required = false) BillingInvoiceRepository billingInvoiceRepository) {
        this.subscriptionContractRepository = subscriptionContractRepository;
        this.billingInvoiceRepository = billingInvoiceRepository;
    }

    public BillingState currentState(String subjectId) {
        if (subscriptionContractRepository != null) {
            try {
                List<SubscriptionContractRepository.SubscriptionContractRecord> records =
                        subscriptionContractRepository.findBySubjectId(subjectId);
                if (!records.isEmpty()) {
                    SubscriptionContractRepository.SubscriptionContractRecord latest = records.get(0);
                    BillingState state = new BillingState(
                            latest.subjectId(),
                            latest.contractState(),
                            latest.periodEndAt(),
                            latest.canonicalProductCode()
                    );
                    states.put(subjectId, state);
                    return state;
                }
            } catch (Exception e) {
                log.warn("Failed to load billing state from DB for {}: {}", subjectId, e.getMessage());
            }
        }

        return states.getOrDefault(subjectId,
                new BillingState(subjectId, "active", Instant.now().plusSeconds(86400 * 30L), "pro_monthly"));
    }

    public BillingState activateSubscription(SubscriptionContract contract) {
        if (contract == null) {
            throw new IllegalArgumentException("contract is required");
        }

        if (subscriptionContractRepository != null) {
            try {
                subscriptionContractRepository.save(
                        contract.contractId(),
                        "tenant",
                        contract.userId(),
                        contract.planKey(),
                        null,
                        null,
                        contract.lifecycleState(),
                        contract.periodStartAt(),
                        contract.periodEndAt()
                );
                log.debug("Persisted subscription contract: {}", contract.contractId());
            } catch (Exception e) {
                log.warn("Failed to persist subscription contract {}: {}", contract.contractId(), e.getMessage());
            }
        }

        contracts.put(contract.contractId(), contract);

        BillingState state = new BillingState(
                contract.userId(),
                contract.lifecycleState(),
                contract.periodEndAt(),
                contract.planKey()
        );
        states.put(contract.userId(), state);
        return state;
    }

    public InvoiceProjectionUpdatedEvent updateInvoice(String invoiceId, String subjectId, String invoiceStatus) {
        String id = invoiceId != null ? invoiceId : Ids.newId("inv");

        if (billingInvoiceRepository != null) {
            try {
                billingInvoiceRepository.save(id, null, null, null,
                        invoiceStatus, null, null, null);
                log.debug("Persisted billing invoice: {}", id);
            } catch (Exception e) {
                log.warn("Failed to persist billing invoice {}: {}", id, e.getMessage());
            }
        }

        InvoiceProjectionUpdatedEvent event = new InvoiceProjectionUpdatedEvent(id, subjectId, invoiceStatus);
        invoices.put(id, event);
        return event;
    }

    public BillingState getBillingState(String subscriptionId) {
        BillingState state = states.get(subscriptionId);
        if (state == null && subscriptionContractRepository != null) {
            try {
                SubscriptionContractRepository.SubscriptionContractRecord record =
                        subscriptionContractRepository.findById(subscriptionId).orElse(null);
                if (record != null) {
                    state = new BillingState(record.subjectId(), record.contractState(),
                            record.periodEndAt(), record.canonicalProductCode());
                    states.put(subscriptionId, state);
                }
            } catch (Exception e) {
                log.warn("Failed to load subscription contract from DB: {}", e.getMessage());
            }
        }
        return state;
    }

    public SubscriptionContract getContract(String contractId) {
        SubscriptionContract contract = contracts.get(contractId);
        if (contract == null && subscriptionContractRepository != null) {
            try {
                SubscriptionContractRepository.SubscriptionContractRecord record =
                        subscriptionContractRepository.findById(contractId).orElse(null);
                if (record != null) {
                    contract = new SubscriptionContract(
                            record.id(), "tenant", record.subjectId(), record.canonicalProductCode(),
                            record.periodStartAt(), record.periodEndAt(),
                            record.contractState(), 0L, "USD",
                            Map.of(), Map.of(),
                            SubscriptionContractRole.BASE,
                            record.canonicalProductCode()
                    );
                    contracts.put(contractId, contract);
                }
            } catch (Exception e) {
                log.warn("Failed to load subscription contract from DB: {}", e.getMessage());
            }
        }
        return contract;
    }

    public List<InvoiceProjectionUpdatedEvent> getInvoiceEvents() {
        return List.copyOf(invoices.values());
    }

    public BillingEvent createBillingEvent(String eventType, String subjectId, String canonicalProductCode, String state) {
        return new BillingEvent(eventType, 1, subjectId, canonicalProductCode, state);
    }

    public SubscriptionContract createContract(String subjectId, String canonicalProductCode, String lifecycleState, int periodDays) {
        String contractId = Ids.newId("sub");
        Instant now = Instant.now();
        SubscriptionContract contract = new SubscriptionContract(
                contractId,
                "tenant",
                subjectId,
                canonicalProductCode,
                now,
                now.plus(periodDays, ChronoUnit.DAYS),
                lifecycleState,
                0L,
                "USD",
                Map.of(),
                Map.of(),
                SubscriptionContractRole.BASE,
                canonicalProductCode
        );

        if (subscriptionContractRepository != null) {
            try {
                subscriptionContractRepository.save(
                        contractId, "tenant", subjectId, canonicalProductCode,
                        null, null, lifecycleState,
                        now, now.plus(periodDays, ChronoUnit.DAYS));
                log.debug("Persisted new subscription contract: {}", contractId);
            } catch (Exception e) {
                log.warn("Failed to persist new subscription contract {}: {}", contractId, e.getMessage());
            }
        }

        contracts.put(contractId, contract);
        return contract;
    }
}
