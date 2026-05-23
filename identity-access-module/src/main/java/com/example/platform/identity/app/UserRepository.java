package com.example.platform.identity.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.identity.domain.User;
import com.example.platform.identity.infrastructure.JooqRecords;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final DSLContext dsl;

    public UserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public User save(User user) {
        dsl.insertInto(table("\"user\""))
                .columns(field("id"), field("tenant_id"), field("username"),
                        field("email"), field("role"), field("status"), field("created_at"))
                .values(user.id(), user.tenantId(), user.username(),
                        user.email(), user.role().name(), user.status().name(), user.createdAt())
                .execute();
        return user;
    }

    public Optional<User> findById(String id) {
        Record record = dsl.select()
                .from(table("\"user\""))
                .where(field("id").eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<User> findByTenantId(String tenantId) {
        return dsl.select()
                .from(table("\"user\""))
                .where(field("tenant_id").eq(tenantId))
                .orderBy(field("created_at").desc())
                .fetch(this::mapRecord);
    }

    public Optional<User> findByTenantIdAndEmail(String tenantId, String email) {
        Record record = dsl.select()
                .from(table("\"user\""))
                .where(field("tenant_id").eq(tenantId))
                .and(field("email").eq(email))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public void updateRole(String userId, User.UserRole role) {
        dsl.update(table("\"user\""))
                .set(field("role"), role.name())
                .where(field("id").eq(userId))
                .execute();
    }

    private User mapRecord(Record record) {
        return new User(
                JooqRecords.string(record, "id"),
                JooqRecords.string(record, "tenant_id"),
                JooqRecords.string(record, "username"),
                JooqRecords.string(record, "email"),
                User.UserRole.valueOf(JooqRecords.string(record, "role")),
                User.UserStatus.valueOf(JooqRecords.string(record, "status")),
                JooqRecords.offsetDateTime(record, "created_at").toInstant()
        );
    }
}
