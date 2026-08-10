import com.example.platform.storage.contract.namespace.*;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.contract.error.StorageError;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import com.example.platform.storage.contract.identity.StorageObjectLocation;

class StoragePlacementValidatorTest {
    @Test void placement_validRegion() {
        StorageProviderId pid = new StorageProviderId("s3");
        StorageNamespace ns = new StorageNamespace("t1", "p1", NamespaceClass.SOURCE, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        StorageObjectLocation loc = new StorageObjectLocation(pid, ns, "bucket/key", null, "us-east-1");
        StoragePlacementPolicy policy = new StoragePlacementPolicy(DataClassification.INTERNAL, Set.of("us-east-1"), false);
        List<StorageError.Error> errors = StoragePlacementValidator.validatePlacement(loc, policy);
        assertTrue(errors.isEmpty());
    }
    @Test void placement_invalidRegion_detected() {
        StorageProviderId pid = new StorageProviderId("s3");
        StorageNamespace ns = new StorageNamespace("t1", "p1", NamespaceClass.SOURCE, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        StorageObjectLocation loc = new StorageObjectLocation(pid, ns, "bucket/key", null, "eu-west-1");
        StoragePlacementPolicy policy = new StoragePlacementPolicy(DataClassification.INTERNAL, Set.of("us-east-1"), false);
        List<StorageError.Error> errors = StoragePlacementValidator.validatePlacement(loc, policy);
        assertFalse(errors.isEmpty());
    }
    @Test void tenantIsolation_crossTenantAccess_detected() {
        StorageNamespace ns1 = new StorageNamespace("tenant-a", "p1", NamespaceClass.SOURCE, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        StorageNamespace ns2 = new StorageNamespace("tenant-b", "p1", NamespaceClass.SOURCE, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        List<StorageError.Error> errors = StoragePlacementValidator.validateTenantIsolation(ns1, ns2);
        assertFalse(errors.isEmpty());
    }
    @Test void tenantIsolation_sameTenant_passes() {
        StorageNamespace ns1 = new StorageNamespace("tenant-a", "p1", NamespaceClass.SOURCE, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        StorageNamespace ns2 = new StorageNamespace("tenant-a", "p2", NamespaceClass.DERIVED, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        List<StorageError.Error> errors = StoragePlacementValidator.validateTenantIsolation(ns1, ns2);
        assertTrue(errors.isEmpty());
    }
}
