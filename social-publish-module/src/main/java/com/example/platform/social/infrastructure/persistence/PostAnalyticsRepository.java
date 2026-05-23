package com.example.platform.social.infrastructure.persistence;

import com.example.platform.social.domain.PostAnalytics;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class PostAnalyticsRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<PostAnalytics> rowMapper = (rs, rowNum) -> mapRow(rs);

    public PostAnalyticsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PostAnalytics save(PostAnalytics analytics) {
        String sql = """
                INSERT INTO social_post_analytics (id, post_id, platform_type, impressions, reach, likes,
                    comments, shares, clicks, fetched_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.update(sql,
                analytics.id(), analytics.postId(), analytics.platformType(),
                analytics.impressions(), analytics.reach(), analytics.likes(),
                analytics.comments(), analytics.shares(), analytics.clicks(),
                analytics.fetchedAt(), analytics.createdAt());
        return analytics;
    }

    public Optional<PostAnalytics> findById(String id) {
        List<PostAnalytics> results = jdbc.query(
                "SELECT * FROM social_post_analytics WHERE id = ?", rowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<PostAnalytics> findByPostId(String postId) {
        return jdbc.query(
                "SELECT * FROM social_post_analytics WHERE post_id = ? ORDER BY created_at DESC",
                rowMapper, postId);
    }

    private PostAnalytics mapRow(ResultSet rs) throws SQLException {
        return new PostAnalytics(
                rs.getString("id"),
                rs.getString("post_id"),
                rs.getString("platform_type"),
                rs.getInt("impressions"),
                rs.getInt("reach"),
                rs.getInt("likes"),
                rs.getInt("comments"),
                rs.getInt("shares"),
                rs.getInt("clicks"),
                rs.getTimestamp("fetched_at") != null ? rs.getTimestamp("fetched_at").toInstant() : null,
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
