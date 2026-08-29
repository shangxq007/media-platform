package com.example.platform.artifact.infrastructure;

import com.example.platform.artifact.app.ArtifactAccessGrantProvider;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactReplicaBinding;
import com.example.platform.storage.infrastructure.S3ObjectMaterializer;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** S3-compatible mechanics adapter. Raw coordinates never cross this class. */
@Component
public class S3ArtifactAccessGrantProvider implements ArtifactAccessGrantProvider {

    private static final Pattern SIMPLE_DURATION = Pattern.compile("^(\\d+)([smhd])$", Pattern.CASE_INSENSITIVE);

    private final S3ObjectMaterializer materializer;
    private final boolean enabled;
    private final Duration ttl;

    public S3ArtifactAccessGrantProvider(
            ObjectProvider<S3ObjectMaterializer> materializerProvider,
            @Value("${storage.s3.signed-access.enabled:false}") boolean enabled,
            @Value("${storage.s3.signed-access.ttl:15m}") String ttl) {
        this.materializer = materializerProvider.getIfAvailable();
        this.enabled = enabled;
        this.ttl = parseDuration(ttl);
    }

    @Override
    public Optional<Grant> grant(Artifact artifact, ArtifactReplicaBinding replica) {
        if (!enabled || materializer == null || !supports(replica.providerId().value())) {
            return Optional.empty();
        }
        String location = replica.storageObjectId().value();
        if (location.startsWith("s3://")) {
            location = location.substring("s3://".length());
        }
        String[] parts = location.split("/", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return Optional.empty();
        }
        URI url = URI.create(materializer.createPresignedGetUrl(parts[0], parts[1], ttl));
        return Optional.of(new Grant(url, Instant.now().plus(ttl)));
    }

    private static boolean supports(String providerId) {
        String normalized = providerId.toLowerCase(Locale.ROOT);
        return normalized.contains("s3") || normalized.contains("r2") || normalized.contains("rustfs");
    }

    static Duration parseDuration(String value) {
        if (value == null || value.isBlank()) {
            return Duration.ofMinutes(15);
        }
        try {
            return Duration.parse(value);
        } catch (RuntimeException ignored) {
            Matcher matcher = SIMPLE_DURATION.matcher(value.trim());
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid signed access TTL: " + value);
            }
            long amount = Long.parseLong(matcher.group(1));
            return switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "s" -> Duration.ofSeconds(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                case "d" -> Duration.ofDays(amount);
                default -> throw new IllegalArgumentException("Invalid signed access TTL: " + value);
            };
        }
    }
}
