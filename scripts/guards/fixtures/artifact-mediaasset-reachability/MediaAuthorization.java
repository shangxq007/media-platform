package fixture;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
@Component
@Profile("legacy-media-disabled")
final class MediaAuthorization { }
