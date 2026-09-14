package com.example.platform.identity.app;

import com.example.platform.identity.domain.User;
import com.example.platform.identity.infrastructure.JooqRecords;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.User.USER;


@Repository
public class UserRepository {

    private final DSLContext dsl;

    public UserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public User save(User user) {
        dsl.insertInto(USER)
                .columns(USER.ID, USER.TENANT_ID, USER.USERNAME,
                        USER.EMAIL, USER.ROLE, USER.STATUS, USER.CREATED_AT)
                .values(user.id(), user.tenantId(), user.username(),
                        user.email(), user.role().name(), user.status().name(), LocalDateTime.ofInstant(user.createdAt(), ZoneOffset.UTC))
                .execute();
        return user;
    }

    public Optional<User> findById(String id) {
        Record record = dsl.select()
                .from(USER)
                .where(USER.ID.eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    /** One canonical persisted human-membership eligibility rule for authentication and ownership. */
    public boolean isUsableMembership(String id,String tenantId) {
        var account=com.example.platform.typedschema.jooq.generated.tables.Account.ACCOUNT;
        var tenant=com.example.platform.typedschema.jooq.generated.tables.Tenant.TENANT;
        return dsl.fetchExists(dsl.selectOne().from(USER)
                .join(account).on(USER.ACCOUNT_ID.eq(account.ID)).join(tenant).on(USER.TENANT_ID.eq(tenant.ID))
                .where(USER.ID.eq(id)).and(USER.TENANT_ID.eq(tenantId)).and(USER.STATUS.eq("ACTIVE"))
                .and(account.STATUS.eq("ACTIVE")).and(tenant.STATUS.eq("ACTIVE")));
    }

    public List<User> findByTenantId(String tenantId) {
        return dsl.select()
                .from(USER)
                .where(USER.TENANT_ID.eq(tenantId))
                .orderBy(USER.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    public Optional<User> findByTenantIdAndEmail(String tenantId, String email) {
        Record record = dsl.select()
                .from(USER)
                .where(USER.TENANT_ID.eq(tenantId))
                .and(USER.EMAIL.eq(email))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public void updateRole(String userId, User.UserRole role) {
        dsl.update(USER)
                .set(USER.ROLE, role.name())
                .where(USER.ID.eq(userId))
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
