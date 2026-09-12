package com.example.platform.social.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class ConnectedPlatformRepositoryReadQueryTest {

    @Mock private JdbcTemplate jdbc;

    @Test
    void publicationAccountQueryDoesNotHydrateCredentialsOrProviderIdentity() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class)))
                .thenReturn(List.of());
        ConnectedPlatformRepository repository = new ConnectedPlatformRepository(jdbc);

        repository.findPublicationAccounts("tenant-a", "actor-1", "project-1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        String normalized = sql.getValue().replaceAll("\\s+", " ").trim();
        assertTrue(normalized.startsWith(
                "SELECT a.id, a.platform_username, a.platform_type, a.status, a.binding_version"));
        assertTrue(normalized.contains("a.tenant_id = ? AND a.user_id = ?"));
        assertTrue(normalized.contains("a.status = 'ACTIVE'"));
        assertTrue(normalized.contains("p.project_id = ?"));
        assertTrue(normalized.contains("p.connected_platform_id = a.id"));
        assertTrue(normalized.contains("p.connected_platform_binding_version = a.binding_version"));
        assertFalse(normalized.contains("SELECT *"));
        assertFalse(normalized.contains("platform_user_id"));
        assertFalse(normalized.contains("access_token"));
        assertFalse(normalized.contains("refresh_token"));
        assertFalse(normalized.contains("token_expires_at"));
    }

    @Test
    void exactPublicationAccountLookupRequiresProjectAndExpectedCurrentBinding() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class)))
                .thenReturn(List.of());
        ConnectedPlatformRepository repository = new ConnectedPlatformRepository(jdbc);

        repository.findPublicationAccount(
                "tenant-a", "actor-1", "project-1", "account-1", 9L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), arguments.capture());
        String normalized = sql.getValue().replaceAll("\\s+", " ").trim();
        assertTrue(normalized.contains("a.binding_version = ?"));
        assertTrue(normalized.contains("p.project_id = ?"));
        assertTrue(normalized.contains("p.connected_platform_id = a.id"));
        assertTrue(normalized.contains("p.connected_platform_binding_version = a.binding_version"));
        assertTrue(normalized.contains("p.platform_type = a.platform_type"));
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                new Object[] {"tenant-a", "actor-1", "account-1", 9L, "project-1"},
                arguments.getValue());
    }
}
