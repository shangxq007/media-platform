package com.example.platform.commerce.infrastructure;

import com.example.platform.commerce.domain.*;
import com.example.platform.shared.commercial.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Sole physical SQL writer for ProductCatalog and CommercialOffering state. */
@Repository
public class ProductCatalogJdbcRepository {
    private final JdbcTemplate jdbc;

    public ProductCatalogJdbcRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean claimCommand(String id, CatalogActor actor, String type, String key, String fingerprint,
            String productId, String offeringId, String mappingId, String source, String reason,
            String traceId, Instant at) {
        return jdbc.update("""
                INSERT INTO product_catalog_command
                  (id,catalog_scope,actor_tenant_id,actor_principal_type,actor_principal_id,command_type,
                   idempotency_key,payload_fingerprint,product_id,offering_id,provider_mapping_id,
                   result_state,result_version,source,reason,trace_id,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,'PENDING',0,?,?,?,?) ON CONFLICT (catalog_scope,idempotency_key) DO NOTHING
                """, id, actor.catalogScope(), actor.tenantId(), actor.principalType(), actor.principalId(), type,
                key, fingerprint, productId, offeringId, mappingId, source, reason, traceId, Timestamp.from(at)) == 1;
    }

    public Optional<CommandAudit> findCommand(String scope, String key) {
        return jdbc.query("SELECT * FROM product_catalog_command WHERE catalog_scope=? AND idempotency_key=?",
                (rs, row) -> new CommandAudit(rs.getString("payload_fingerprint"), rs.getString("product_id"),
                        rs.getString("offering_id"), rs.getString("provider_mapping_id"),
                        rs.getString("result_state"), rs.getLong("result_version")), scope, key).stream().findFirst();
    }

    public void completeCommand(String scope, String key, String state, long version) {
        if (jdbc.update("UPDATE product_catalog_command SET result_state=?, result_version=? WHERE catalog_scope=? AND idempotency_key=? AND result_state='PENDING'",
                state, version, scope, key) != 1) throw new IllegalStateException("catalog command completion lost");
    }

    public void insertProductIfAbsent(CreateCommercialOfferingCommand c) {
        jdbc.update("""
                INSERT INTO commerce_product
                  (id,product_code,product_line_type,display_name,lifecycle_state,version,created_at,updated_at)
                VALUES (?,?,?,?, 'DRAFT',1,?,?)
                ON CONFLICT DO NOTHING
                """, c.productId(), c.productCode(), c.lineType().name(), c.displayName(),
                Timestamp.from(c.occurredAt()), Timestamp.from(c.occurredAt()));
    }

    public void insertOffering(CreateCommercialOfferingCommand c) {
        jdbc.update("""
                INSERT INTO commercial_offering
                  (id,product_id,offering_key,offering_version,lifecycle_state,row_version,purchase_mode,
                   tenant_scope,market_scope,valid_from,valid_to,entitlement_bundle_ref,entitlement_bundle_version,
                   quota_profile_ref,quota_profile_version,subscription_plan_ref,subscription_plan_version,
                   commercial_price_ref,commercial_price_version,amount_minor_snapshot,currency_code_snapshot,
                   credit_quantity_minor,seat_quantity,seat_feature_key,created_at,updated_at)
                VALUES (?,?,?,?, 'DRAFT',1,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, c.offeringId(), c.productId(), c.offeringKey(), c.offeringVersion(), c.purchaseMode().name(),
                c.tenantScope(), c.marketScope().toUpperCase(), Timestamp.from(c.validFrom()), timestamp(c.validTo()),
                key(c.entitlementBundleReference()), version(c.entitlementBundleReference()),
                key(c.quotaProfileReference()), version(c.quotaProfileReference()),
                key(c.subscriptionPlanReference()), version(c.subscriptionPlanReference()),
                c.commercialPriceReference().key(), c.commercialPriceReference().version(),
                c.priceSnapshot().amountMinor(), c.priceSnapshot().currency(), c.creditQuantityMinor(),
                c.seatQuantity(), c.seatFeatureKey(), Timestamp.from(c.occurredAt()), Timestamp.from(c.occurredAt()));
    }

    public Optional<ProductCatalogEntry> findProductById(String id) {
        return jdbc.query("SELECT * FROM commerce_product WHERE id=?", this::mapProduct, id).stream().findFirst();
    }

    public Optional<ProductCatalogEntry> findProduct(CatalogReadScope scope, String id) {
        return jdbc.query("""
                SELECT p.* FROM commerce_product p WHERE p.id=? AND EXISTS (
                  SELECT 1 FROM commercial_offering o WHERE o.product_id=p.id
                    AND (o.tenant_scope='GLOBAL' OR o.tenant_scope=?))
                """, this::mapProduct, id, scope.tenantId()).stream().findFirst();
    }

    public Optional<CommercialOffering> findOfferingById(String id) {
        return jdbc.query("""
                SELECT o.*,p.product_code,p.product_line_type,p.display_name FROM commercial_offering o JOIN commerce_product p ON p.id=o.product_id
                WHERE o.id=?
                """, this::mapOffering, id).stream().findFirst();
    }

    public Optional<CommercialOffering> findHistorical(CatalogReadScope scope, String id, long offeringVersion) {
        return jdbc.query("""
                SELECT o.*,p.product_code,p.product_line_type,p.display_name FROM commercial_offering o JOIN commerce_product p ON p.id=o.product_id
                WHERE o.id=? AND o.offering_version=? AND (o.tenant_scope='GLOBAL' OR o.tenant_scope=?)
                """, this::mapOffering, id, offeringVersion, scope.tenantId()).stream().findFirst();
    }

    public Optional<CommercialOffering> resolveActive(CatalogReadScope scope, String market, String productCode, Instant at) {
        return jdbc.query("""
                SELECT o.*,p.product_code,p.product_line_type,p.display_name FROM commercial_offering o JOIN commerce_product p ON p.id=o.product_id
                WHERE p.product_code=? AND p.lifecycle_state='ACTIVE' AND o.lifecycle_state='ACTIVE'
                  AND (o.tenant_scope='GLOBAL' OR o.tenant_scope=?)
                  AND (o.market_scope='GLOBAL' OR o.market_scope=?)
                  AND o.valid_from<=? AND (o.valid_to IS NULL OR o.valid_to>?)
                ORDER BY CASE WHEN o.tenant_scope=? THEN 0 ELSE 1 END, o.offering_version DESC LIMIT 1
                """, this::mapOffering, productCode, scope.tenantId(), market.toUpperCase(), Timestamp.from(at),
                Timestamp.from(at), scope.tenantId()).stream().findFirst();
    }

    public List<CommercialOffering> listActive(CatalogReadScope scope, String market, Instant at) {
        return jdbc.query("""
                SELECT o.*,p.product_code,p.product_line_type,p.display_name FROM commercial_offering o JOIN commerce_product p ON p.id=o.product_id
                WHERE p.lifecycle_state='ACTIVE' AND o.lifecycle_state='ACTIVE'
                  AND (o.tenant_scope='GLOBAL' OR o.tenant_scope=?)
                  AND (o.market_scope='GLOBAL' OR o.market_scope=?) AND o.valid_from<=?
                  AND (o.valid_to IS NULL OR o.valid_to>?) ORDER BY p.product_code,o.offering_version DESC
                """, this::mapOffering, scope.tenantId(), market.toUpperCase(), Timestamp.from(at), Timestamp.from(at));
    }

    public void transitionOffering(String offeringId, long expected, OfferingLifecycleState from,
            OfferingLifecycleState to, Instant at) {
        int updated = jdbc.update("""
                UPDATE commercial_offering SET lifecycle_state=?,row_version=row_version+1,updated_at=?
                WHERE id=? AND row_version=? AND lifecycle_state=?
                """, to.name(), Timestamp.from(at), offeringId, expected, from.name());
        if (updated != 1) throw new IllegalStateException("stale or illegal offering lifecycle transition");
    }

    public void transitionProduct(String productId, long expected, ProductLifecycleState from,
            ProductLifecycleState to, Instant at) {
        int updated = jdbc.update("""
                UPDATE commerce_product SET lifecycle_state=?,version=version+1,updated_at=?
                WHERE id=? AND version=? AND lifecycle_state=?
                """, to.name(), Timestamp.from(at), productId, expected, from.name());
        if (updated != 1) throw new IllegalStateException("stale or illegal product lifecycle transition");
    }

    public ProviderProductMapping insertMapping(MapProviderOfferingCommand c) {
        jdbc.update("""
                INSERT INTO provider_product_mapping
                  (id,provider_code,external_product_ref,external_price_ref,product_id,offering_id,
                   offering_version,version,created_at,updated_at) VALUES (?,?,?,?,?,?,?,1,?,?)
                """, c.mappingId(), c.providerCode(), c.externalProductReference(), c.externalPriceReference(),
                c.productId(), c.offeringId(), c.offeringVersion(), Timestamp.from(c.occurredAt()), Timestamp.from(c.occurredAt()));
        return findMapping(c.mappingId()).orElseThrow();
    }

    public Optional<ProviderProductMapping> findMapping(String id) {
        return jdbc.query("SELECT * FROM provider_product_mapping WHERE id=?", (rs, row) -> new ProviderProductMapping(
                rs.getString("id"), rs.getString("provider_code"), rs.getString("external_product_ref"),
                rs.getString("external_price_ref"), rs.getString("product_id"), rs.getString("offering_id"),
                rs.getLong("offering_version"), rs.getLong("version"), instant(rs, "created_at"), instant(rs, "updated_at")), id).stream().findFirst();
    }

    private ProductCatalogEntry mapProduct(ResultSet rs, int row) throws SQLException {
        return new ProductCatalogEntry(rs.getString("id"), rs.getString("product_code"),
                ProductLineType.valueOf(rs.getString("product_line_type")), rs.getString("display_name"),
                ProductLifecycleState.valueOf(rs.getString("lifecycle_state")), rs.getLong("version"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private CommercialOffering mapOffering(ResultSet rs, int row) throws SQLException {
        return new CommercialOffering(rs.getString("id"), rs.getString("product_id"), rs.getString("product_code"),
                ProductLineType.valueOf(rs.getString("product_line_type")), rs.getString("display_name"),
                rs.getString("offering_key"), rs.getLong("offering_version"),
                OfferingLifecycleState.valueOf(rs.getString("lifecycle_state")), rs.getLong("row_version"),
                PurchaseMode.valueOf(rs.getString("purchase_mode")), rs.getString("tenant_scope"), rs.getString("market_scope"),
                instant(rs, "valid_from"), nullableInstant(rs, "valid_to"),
                reference(rs, "entitlement_bundle_ref", "entitlement_bundle_version"),
                reference(rs, "quota_profile_ref", "quota_profile_version"),
                reference(rs, "subscription_plan_ref", "subscription_plan_version"),
                new AuthorityReference(rs.getString("commercial_price_ref"), rs.getLong("commercial_price_version")),
                new Money(rs.getLong("amount_minor_snapshot"), rs.getString("currency_code_snapshot")),
                nullableLong(rs, "credit_quantity_minor"), nullableInteger(rs, "seat_quantity"), rs.getString("seat_feature_key"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private static AuthorityReference reference(ResultSet rs, String key, String version) throws SQLException {
        String value = rs.getString(key); return value == null ? null : new AuthorityReference(value, rs.getLong(version));
    }
    private static String key(AuthorityReference ref) { return ref == null ? null : ref.key(); }
    private static Long version(AuthorityReference ref) { return ref == null ? null : ref.version(); }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(ResultSet rs, String name) throws SQLException { return rs.getTimestamp(name).toInstant(); }
    private static Instant nullableInstant(ResultSet rs, String name) throws SQLException { Timestamp value=rs.getTimestamp(name); return value==null?null:value.toInstant(); }
    private static Long nullableLong(ResultSet rs, String name) throws SQLException { long value=rs.getLong(name); return rs.wasNull()?null:value; }
    private static Integer nullableInteger(ResultSet rs, String name) throws SQLException { int value=rs.getInt(name); return rs.wasNull()?null:value; }

    public record CommandAudit(String fingerprint, String productId, String offeringId, String mappingId,
            String resultState, long resultVersion) {}
}
