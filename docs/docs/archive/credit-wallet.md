# Credit Wallet

> Doc index: [docs/README.md](./README.md).

## Overview

Credit wallets provide a pre-paid balance system. Users top up their wallet with credits, which are then debited when consuming platform resources (render jobs, prompt executions, etc.). The `CreditWalletService` manages wallets and transactions.

## Credit Wallet Concept

### CreditWallet

```java
public record CreditWallet(
    String walletId,
    String tenantId,
    String workspaceId,
    String userId,
    long balanceMinor,       // Balance in minor currency units (cents)
    String currencyCode,     // e.g., "USD"
    String status,           // ACTIVE, FROZEN, CLOSED
    Instant createdAt,
    Instant updatedAt
)
```

A wallet can be scoped to:
- A tenant (shared across the tenant)
- A workspace (shared within a workspace)
- An individual user

### CreditTransaction

```java
public record CreditTransaction(
    String transactionId,
    String walletId,
    String transactionType,  // CREDIT, DEBIT, RESERVE, FINALIZE, RELEASE, REFUND
    long amountMinor,
    long balanceAfterMinor,
    String referenceType,    // e.g., "render_job", "top_up", "refund"
    String referenceId,
    String description,
    Instant createdAt
)
```

## Wallet Lifecycle

### Create

```java
CreditWallet wallet = creditWalletService.createWallet(
    "tenant-1", "ws-1", "user-123", "USD");
```

Creates a new wallet with zero balance.

### Credit (Top Up)

```java
CreditWallet updated = creditWalletService.credit(
    "wlt-abc", 10000L, "top_up", "topup-001", "Account top-up $100.00");
```

Adds credits to the wallet. Creates a `CREDIT` transaction record.

> **Production Blocker**: Real payment provider integration for top-ups is not implemented. The `credit()` method can be called directly for testing.

### Debit

```java
CreditWallet updated = creditWalletService.debit(
    "wlt-abc", 500L, "render_job", "job-456", "Render job #456");
```

Deducts credits from the wallet. Creates a `DEBIT` transaction record. Throws `IllegalStateException` if balance is insufficient.

### Reserve

```java
String reservationId = creditWalletService.reserve(
    "wlt-abc", 2000L, "render_job", "job-789", "Reserve for render job #789");
```

Reserves credits for a future operation. The reserved amount is not available for other debits. Creates a `RESERVE` transaction record.

### Finalize

```java
creditWalletService.finalize(
    "wlt-abc", "res-xyz", 1500L, "render_job", "job-789", "Final charge for render job #789");
```

Finalizes a reservation with the actual amount consumed. The difference between reserved and actual is released back to available balance. Creates a `FINALIZE` transaction record.

### Release

```java
creditWalletService.release(
    "wlt-abc", "res-xyz", "render_job", "job-789", "Release reservation for cancelled job");
```

Releases a reservation without debiting. The full reserved amount returns to available balance. Creates a `RELEASE` transaction record.

## Transaction Types

| Type | Description | Balance Effect |
|------|-------------|----------------|
| `CREDIT` | Add funds (top-up, refund) | Increases |
| `DEBIT` | Consume funds (render, prompt) | Decreases |
| `RESERVE` | Hold funds for pending operation | No change (reserved) |
| `FINALIZE` | Complete a reservation with actual amount | Decreases by actual |
| `RELEASE` | Cancel a reservation | No change (released) |
| `REFUND` | Refund for cancelled/failed operation | Increases |

## How Credits Are Used

### Render Jobs

```
1. Before render: reserve(walletId, estimatedCost)
2. After render:  finalize(walletId, reservationId, actualCost)
3. If cancelled:  release(walletId, reservationId)
```

### Prompt Executions

```
1. Before execution: reserve(walletId, estimatedTokenCost)
2. After execution:  finalize(walletId, reservationId, actualTokenCost)
3. If failed:         release(walletId, reservationId)
```

### Extension Executions

```
1. Before execution: reserve(walletId, estimatedCost)
2. After execution:  finalize(walletId, reservationId, actualCost)
```

## Admin Wallet Management

### Creating Wallets

Admins can create wallets for tenants, workspaces, or users:

```java
// Tenant-level wallet
CreditWallet tenantWallet = creditWalletService.createWallet("tenant-1", null, null, "USD");

// Workspace-level wallet
CreditWallet wsWallet = creditWalletService.createWallet("tenant-1", "ws-1", null, "USD");

// User-level wallet
CreditWallet userWallet = creditWalletService.createWallet("tenant-1", null, "user-123", "USD");
```

### Crediting Wallets

Admins can credit wallets (e.g., for promotions, compensation, or manual top-ups):

```java
creditWalletService.credit("wlt-abc", 50000L, "admin", "admin-001", "Promotional credit $500.00");
```

### Viewing Transactions

```java
List<CreditTransaction> transactions = creditWalletService.getTransactions("wlt-abc");
```

### Listing Wallets by Tenant

```java
List<CreditWallet> wallets = creditWalletService.getWalletsByTenant("tenant-1");
```

## Current Limitations

- **In-memory storage**: Wallets and transactions are stored in `ConcurrentHashMap`. Not persisted across restarts.
- **No payment provider**: Top-ups via real payment providers are not integrated.
- **Single currency**: Each wallet supports one currency. No currency conversion.
- **No wallet-to-wallet transfers**: Credits cannot be transferred between wallets.

## Error Codes

| Code | Description |
|------|-------------|
| `WALLET-400-001` | Invalid wallet operation |
| `WALLET-403-001` | Insufficient balance |
| `WALLET-404-001` | Wallet not found |
| `WALLET-404-002` | Reservation not found |
| `WALLET-409-001` | Wallet already exists |
