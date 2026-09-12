package com.example.platform.social.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class SocialPostRepositoryReadQueryTest {

    @Mock private JdbcTemplate jdbc;

    @Test
    void listQueryRequiresExplicitBindingAndRangesOnlyPlannedScheduleTime() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class)))
                .thenReturn(List.of());
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-02-01T00:00:00Z");
        SocialPostRepository repository = new SocialPostRepository(jdbc);

        repository.findReadProjection(
                "tenant-a", "actor-1", "project-1", "account-1", 7L, start, end, 25);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), arguments.capture());
        String normalized = normalize(sql.getValue());
        assertTrue(normalized.contains("p.tenant_id = ?"));
        assertTrue(normalized.contains("p.user_id = ?"));
        assertTrue(normalized.contains("p.project_id = ?"));
        assertTrue(normalized.contains("p.connected_platform_id = ?"));
        assertTrue(normalized.contains("p.connected_platform_binding_version = ?"));
        assertTrue(normalized.contains("a.id = p.connected_platform_id"));
        assertTrue(normalized.contains("a.binding_version = p.connected_platform_binding_version"));
        assertTrue(normalized.contains("a.status = 'ACTIVE'"));
        assertTrue(normalized.contains("p.scheduled_at >= ?"));
        assertTrue(normalized.contains("p.scheduled_at < ?"));
        assertTrue(normalized.contains("ORDER BY p.scheduled_at ASC, p.id ASC"));
        assertFalse(normalized.contains("created_at >="));
        assertFalse(normalized.contains("read_only"));
        assertFalse(normalized.contains("platform_type = ?"));
        assertTrue(normalized.contains("p.artifact_id"));
        assertTrue(normalized.contains("p.project_id"));
        assertTrue(normalized.contains("p.connected_platform_id"));
        assertFalse(normalized.contains("platform_post"));
        assertArrayEquals(new Object[] {
                "tenant-a", "actor-1", "project-1", "account-1", 7L,
                Timestamp.from(start), Timestamp.from(end), 25
        }, arguments.getValue());
    }

    @Test
    void detailQueryAlsoExcludesUnboundAndStaleBindingRowsWithoutHydratingHiddenMetadata() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class)))
                .thenReturn(List.of());
        SocialPostRepository repository = new SocialPostRepository(jdbc);

        repository.findReadProjectionById(
                "tenant-a", "actor-1", "project-1", "account-1", 9L, "post-1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        String normalized = normalize(sql.getValue());
        assertTrue(normalized.contains("p.project_id = ?"));
        assertTrue(normalized.contains("p.connected_platform_id = ?"));
        assertTrue(normalized.contains("p.connected_platform_binding_version = ?"));
        assertTrue(normalized.contains("JOIN social_connected_platform a"));
        assertTrue(normalized.contains("p.artifact_id"));
        assertFalse(normalized.contains("error_message"));
        assertFalse(normalized.contains("published_at"));
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
