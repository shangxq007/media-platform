package fixture;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
interface MediaProbePort { }
@Component
@Profile("legacy-media-disabled")
final class MediaProbePortAdapter implements MediaProbePort { }
