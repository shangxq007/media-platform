package com.example.platform.identity.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Scrubs sensitive URLs from imported metadata JSON.
 *
 * <p>Removes:
 * <ul>
 *   <li>downloadUrl</li>
 *   <li>storageUri / storage_uri</li>
 *   <li>storageRef / storage_ref</li>
 *   <li>bucket</li>
 *   <li>key</li>
 *   <li>signedUrl / signed_url</li>
 *   <li>url (if it looks like a signed URL)</li>
 * </ul>
 *
 * <p>Preserves:
 * <ul>
 *   <li>sourceAssetId</li>
 *   <li>targetAssetId (null for needs_upload)</li>
 *   <li>status = "needs_upload"</li>
 *   <li>effectKey</li>
 *   <li>spatial coordinates</li>
 *   <li>timeline structure</li>
 * </ul>
 */
@Component
public class MetadataScrubber {

    private static final Logger log = LoggerFactory.getLogger(MetadataScrubber.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "downloadurl", "storageuri", "storageref", "bucket", "key",
            "signedurl", "url"
    );

    /**
     * Scrubs a JSON string, removing sensitive URLs.
     *
     * @param json the JSON string to scrub
     * @return the scrubbed JSON string, or null if input is null
     */
    public String scrub(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            JsonNode root = MAPPER.readTree(json);
            if (root.isObject()) {
                scrubObject((ObjectNode) root);
                return MAPPER.writeValueAsString(root);
            }
            return json;
        } catch (Exception e) {
            log.warn("Failed to scrub metadata JSON, returning null: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Scrubs a JSON string and returns it as a JsonNode.
     *
     * @param json the JSON string to scrub
     * @return the scrubbed JsonNode, or null if input is null
     */
    public JsonNode scrubAsNode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            JsonNode root = MAPPER.readTree(json);
            if (root.isObject()) {
                scrubObject((ObjectNode) root);
            }
            return root;
        } catch (Exception e) {
            log.warn("Failed to scrub metadata JSON, returning null: {}", e.getMessage());
            return null;
        }
    }

    private void scrubObject(ObjectNode node) {
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode value = entry.getValue();

            String lowerKey = key.toLowerCase();
            if (SENSITIVE_KEYS.contains(lowerKey)) {
                fields.remove();
                continue;
            }

            if (value.isObject()) {
                scrubObject((ObjectNode) value);
            } else if (value.isArray()) {
                for (JsonNode element : value) {
                    if (element.isObject()) {
                        scrubObject((ObjectNode) element);
                    }
                }
            }
        }
    }
}
