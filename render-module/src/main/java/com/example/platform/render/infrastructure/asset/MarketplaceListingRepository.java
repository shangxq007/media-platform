package com.example.platform.render.infrastructure.asset;

import com.example.platform.render.domain.asset.marketplace.*;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.MarketplaceListing.MARKETPLACE_LISTING;


@Repository
public class MarketplaceListingRepository {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceListingRepository.class);
    private final DSLContext dsl;

    public MarketplaceListingRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void upsert(MarketplaceListing listing) {
        LocalDateTime now = LocalDateTime.now();
        String searchText = listing.title() != null ? listing.title() : "";
        if (listing.summary() != null) searchText += " " + listing.summary();
        dsl.execute(
                "INSERT INTO marketplace_listing (id, asset_id, tenant_id, project_id, listing_type, "
                        + "title, summary, description, preview_url, cover_url, version, status, "
                        + "search_text, search_vector, review_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, to_tsvector('english', ?), ?, ?, ?) "
                        + "ON CONFLICT (asset_id) DO UPDATE SET "
                        + "title = EXCLUDED.title, summary = EXCLUDED.summary, "
                        + "description = EXCLUDED.description, preview_url = EXCLUDED.preview_url, "
                        + "cover_url = EXCLUDED.cover_url, version = EXCLUDED.version, "
                        + "status = EXCLUDED.status, "
                        + "search_text = EXCLUDED.search_text, "
                        + "search_vector = to_tsvector('english', ?), "
                        + "review_id = EXCLUDED.review_id, "
                        + "updated_at = EXCLUDED.updated_at",
                listing.id(), listing.assetId(), listing.tenantId(), listing.projectId(),
                listing.listingType().name(), listing.title(), listing.summary(),
                listing.description(), listing.previewUrl(), listing.coverUrl(),
                listing.version(), listing.status().name(), searchText, searchText,
                listing.reviewId(), toOdt(listing.createdAt()), now, searchText);
    }

    public Optional<MarketplaceListing> findByAssetId(String assetId, String tenantId) {
        var cond = MARKETPLACE_LISTING.ASSET_ID.eq(assetId);
        if (tenantId != null) cond = cond.and(MARKETPLACE_LISTING.TENANT_ID.eq(tenantId));
        Record r = dsl.select().from(MARKETPLACE_LISTING).where(cond).fetchOne();
        return r == null ? Optional.empty() : Optional.of(map(r));
    }

    public void updateStatus(String listingId, String status) {
        dsl.update(MARKETPLACE_LISTING)
                .set(MARKETPLACE_LISTING.STATUS, status).set(MARKETPLACE_LISTING.UPDATED_AT, LocalDateTime.now())
                .where(MARKETPLACE_LISTING.ID.eq(listingId)).execute();
    }

    public SearchResult search(String query, String status, String listingType,
                                  String tenantId, String projectId, int offset, int limit) {
        long start = System.currentTimeMillis();
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        boolean hasQuery = query != null && !query.isBlank();
        if (hasQuery) {
            conditions.add("search_vector @@ plainto_tsquery('english', ?)");
            params.add(query);
        }
        if (status != null) { conditions.add("status = ?"); params.add(status); }
        if (listingType != null) { conditions.add("listing_type = ?"); params.add(listingType); }
        if (tenantId != null) { conditions.add("tenant_id = ?"); params.add(tenantId); }
        if (projectId != null) { conditions.add("project_id = ?"); params.add(projectId); }

        String where = String.join(" AND ", conditions);
        if (where.isEmpty()) where = "1=1";

        String countSql = "SELECT COUNT(*) as cnt FROM marketplace_listing WHERE " + where;
        int total = dsl.fetchOne(countSql, params.toArray()).get("cnt", Integer.class);

        String selectSql = "SELECT *";
        if (hasQuery) selectSql += ", ts_rank(search_vector, plainto_tsquery('english', ?)) as rank";
        selectSql += " FROM marketplace_listing WHERE " + where;
        String orderBy = hasQuery ? "ORDER BY rank DESC" : "ORDER BY updated_at DESC";
        selectSql += " " + orderBy + " LIMIT ? OFFSET ?";

        List<Object> selectParams = new ArrayList<>(params);
        if (hasQuery) selectParams.add(query);
        selectParams.add(limit);
        selectParams.add(offset);

        List<MarketplaceListing> results = dsl.fetch(selectSql, selectParams.toArray())
                .map(this::mapWithRank);
        long elapsed = System.currentTimeMillis() - start;

        log.info("Marketplace search: query='{}' listingType={} status={} tenant={} "
                + "total={} returned={} offset={} latency={}ms",
                query, listingType, status, tenantId, total, results.size(), offset, elapsed);
        return new SearchResult(total, offset, limit, results);
    }

    private MarketplaceListing mapWithRank(org.jooq.Record r) {
        return map(r);
    }

    public List<MarketplaceListing> listByStatus(String status, int limit) {
        return dsl.select().from(MARKETPLACE_LISTING)
                .where(MARKETPLACE_LISTING.STATUS.eq(status))
                .orderBy(MARKETPLACE_LISTING.UPDATED_AT.desc()).limit(limit)
                .fetch().map(MarketplaceListingRepository::map);
    }

    private static MarketplaceListing map(Record r) {
        return new MarketplaceListing(
                r.get(MARKETPLACE_LISTING.ID), r.get(MARKETPLACE_LISTING.ASSET_ID),
                r.get(MARKETPLACE_LISTING.TENANT_ID), r.get(MARKETPLACE_LISTING.PROJECT_ID),
                tryEnum(MarketplaceListingType.class, r.get(MARKETPLACE_LISTING.LISTING_TYPE)),
                r.get(MARKETPLACE_LISTING.TITLE), r.get(MARKETPLACE_LISTING.SUMMARY),
                r.get(MARKETPLACE_LISTING.DESCRIPTION),
                r.get(MARKETPLACE_LISTING.PREVIEW_URL), r.get(MARKETPLACE_LISTING.COVER_URL),
                r.get(MARKETPLACE_LISTING.VERSION),
                tryEnum(MarketplaceListingStatus.class, r.get(MARKETPLACE_LISTING.STATUS)),
                r.get(MARKETPLACE_LISTING.REVIEW_ID),
                toInstant(r.get(MARKETPLACE_LISTING.CREATED_AT)),
                toInstant(r.get(MARKETPLACE_LISTING.UPDATED_AT)));
    }

    public record SearchResult(int total, int offset, int limit, List<MarketplaceListing> results) {}

    private static OffsetDateTime toOdt(java.time.Instant i) {
        return i != null ? OffsetDateTime.ofInstant(i, java.time.ZoneOffset.UTC) : null;
    }
    private static java.time.Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(java.time.ZoneOffset.UTC) : null;
    }
    private static <E extends Enum<E>> E tryEnum(Class<E> type, String value) {
        try { return Enum.valueOf(type, value); } catch (Exception e) { return null; }
    }
}
