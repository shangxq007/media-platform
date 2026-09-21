package com.example.platform.identity.api.reads;
import java.util.Optional;
/** Current verified tenant-scoped public identity projection. */
public interface UserReadQuery {
 record View(String id,String tenantId,String username,String status) {}
 Optional<View> findById(String id);
}
