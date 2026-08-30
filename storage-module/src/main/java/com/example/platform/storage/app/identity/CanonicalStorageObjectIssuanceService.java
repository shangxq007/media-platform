package com.example.platform.storage.app.identity;

import com.example.platform.storage.api.StorageObjectIssuance;
import com.example.platform.storage.api.StorageWriteIntentRecovery;
import com.example.platform.storage.api.StorageWriteIntentRecovery.BeginWriteIntentCommand;
import com.example.platform.storage.api.StorageWriteIntentRecovery.CompleteWriteIntentCommand;
import com.example.platform.storage.domain.identity.StorageWriteIntent;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Canonical Storage application authority for logical object issuance. */
@Service
public class CanonicalStorageObjectIssuanceService implements StorageObjectIssuance {

    private final StorageWriteIntentRecovery recovery;

    public CanonicalStorageObjectIssuanceService(StorageWriteIntentRecovery recovery) {
        this.recovery = Objects.requireNonNull(recovery, "recovery");
    }

    @Override
    public IssuanceResult issue(IssuanceCommand command) {
        Objects.requireNonNull(command, "command");
        StorageWriteIntent intent = recovery.beginOrResume(new BeginWriteIntentCommand(
                command.owner(),
                command.idempotencyKey(),
                command.semanticFingerprint(),
                null));
        recovery.recordProviderCompleted(
                intent.writeIntentId(), command.backendPlacement());
        return recovery.complete(new CompleteWriteIntentCommand(
                intent.writeIntentId(), command.backendPlacement()));
    }
}
