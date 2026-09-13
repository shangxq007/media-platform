package com.example.platform.render.testsupport.fakes;
import com.example.platform.storage.api.*;
import com.example.platform.storage.api.StorageObjectIssuance.*;
import com.example.platform.storage.app.*;
import com.example.platform.storage.app.identity.*;
import com.example.platform.storage.contract.*;
import com.example.platform.storage.domain.identity.*;
import com.example.platform.storage.infrastructure.LocalFsStorageProvider;
import com.example.platform.shared.web.TenantContext;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;

/** Product unit-test fixture: actual LocalFs writer with mocked durable receipt persistence.
 * Real Flyway/issuance/Artifact acceptance is tested by RenderOutputAcceptanceTest. */
public final class OutputStorageFixture {
 public static StorageOutputPort create(StorageRuntimeService references, Path root) {
    var recovery=mock(StorageWriteIntentRecovery.class);
    var objects=mock(StorageObjectAuthorityRepository.class);
    var store=mock(StorageReferenceStore.class);
    var intents=new HashMap<String,StorageWriteIntent>();
    var placements=new HashMap<String,BackendPlacementResult>();
    var accepted=new HashMap<String,IssuanceResult>();
    when(objects.findOriginalIssuance(any(),any())).thenAnswer(i->Optional.ofNullable(accepted.get(((IssuanceIdempotencyKey)i.getArgument(1)).value())));
    when(store.save(any())).thenAnswer(i->references.register(i.getArgument(0)));
    when(recovery.beginOrResume(any())).thenAnswer(i->{
        StorageWriteIntentRecovery.BeginWriteIntentCommand c=i.getArgument(0);
        var intent=new StorageWriteIntent("swi-"+UUID.randomUUID(),new CanonicalStorageObjectIdAllocator().allocate(),
           c.owner(),c.idempotencyKey(),c.semanticFingerprint(),null,null,null,StorageWriteIntent.State.PENDING_PROVIDER,
           null,null,Instant.now(),Instant.now(),null);
        intents.put(intent.writeIntentId(),intent); return intent;
    });
    when(recovery.recordProviderCompleted(anyString(),any())).thenAnswer(i->{placements.put(i.getArgument(0),i.getArgument(1));return intents.get(i.getArgument(0));});
    when(recovery.complete(any())).thenAnswer(i->{
       StorageWriteIntentRecovery.CompleteWriteIntentCommand c=i.getArgument(0); var intent=intents.get(c.writeIntentId()); var v=c.backendPlacement();
       var issued = new IssuanceResult(intent.owner(),intent.objectId(),v,new PlacementReceipt("receipt-"+UUID.randomUUID(),
          intent.idempotencyKey(),intent.semanticFingerprint(),ReceiptPurpose.ORIGINAL_ISSUANCE,intent.objectId(),v.replicaId(),v.location(),v.state(),v.committedDigest(),v.committedLength(),v.providerCorrelationId(),Instant.now()));
       accepted.put(intent.idempotencyKey().value(),issued);return issued;
    });
    var service=new StorageOutputService(new LocalFsStorageProvider(root.toString()),recovery,objects,store,root.toString(),new OutputStorageProperties(),new com.example.platform.storage.infrastructure.StorageS3Properties());
    return command->{String old=TenantContext.get(); TenantContext.set(command.owner().tenantId());
       try {return service.write(command);} finally {if(old==null)TenantContext.clear();else TenantContext.set(old);}};
 }
}
