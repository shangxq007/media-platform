package com.example.platform.config.providerinteroperability;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * V0 configuration carrier for the Provider Interoperability Authority.
 *
 * <p>Bound to the {@code platform.provider-interoperability} prefix. {@code format} is the
 * required version token; {@code declarations} is the optional declaration set.
 *
 * <p>The declaration ITEM schema is deliberately NOT frozen in V0: raw entries are bound but
 * never interpreted, and the only supported production value is an empty list (see
 * {@link ProviderInteroperabilityLoader}). A non-empty value fails closed.
 */
@ConfigurationProperties(prefix = "platform.provider-interoperability")
public class ProviderInteroperabilityProperties {

    /** Required version token; must equal the loader's supported format. */
    private String format;

    /**
     * Declaration set. V0 item schema is deliberately NOT frozen — entries are bound as raw
     * values and never interpreted; a non-empty value fails closed in the loader.
     */
    private List<Object> declarations = new ArrayList<>();

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public List<Object> getDeclarations() {
        return declarations;
    }

    public void setDeclarations(List<Object> declarations) {
        this.declarations = declarations;
    }
}
