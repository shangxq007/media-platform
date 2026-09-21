package com.example.platform.identity.api.reads;
import java.util.Optional;
/** Current verified tenant-scoped public identity projection. */
public interface TenantReadQuery {
 record View(String id,String name,String status) {}
 Optional<View> findById(String id);
}
