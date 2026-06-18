---
status: roadmap
last_verified: 2026-06-18
scope: future
truth_level: target
owner: platform
---

# AI Provider Ecosystem Roadmap

> **Last verified:** 2026-06-18
> **Current State:** Spring AI isolated in `ai-module`, stub provider active
> **Source:** [AI Engine SPI](../ai-engine-spi.md) · [Current System State](../architecture/current/current-system-state.md)

---

## Current State

### Spring AI Isolation

**Status:** Spring AI is fully isolated from the main platform runtime

| Aspect | Current State | Notes |
|--------|---------------|-------|
| Module isolation | ✅ Complete | `spring-ai-adapter` not pulled into `platform-app` |
| Spring AI dependency | ⚠️ Present in `spring-ai-adapter` | `org.springframework.ai:spring-ai-starter-model-openai` |
| Runtime path | ✅ Excluded | No `spring.ai` properties in `platform-app` |
| Test scope | ✅ Isolated | H2 only in test scope |

**Evidence:**
- `grep -R "spring.ai" platform-app/` → No results
- `spring-ai-adapter/build.gradle.kts` → NOT included in `platform-app` by default

---

### Current Provider Implementations

| Provider | Status | Module | Notes |
|----------|--------|--------|-------|
| `StubChatProvider` | ✅ Active | `ai-module` | Default provider, deterministic mock |
| `OpenAiChatProvider` | ⚠️ Placeholder | `ai-module` | Returns hardcoded response |
| `ReplicateProvider` | ❌ Not implemented | — | No classes, config, or dependencies |
| `GoogleGenAiProvider` | ❌ Not implemented | — | No classes, config, or dependencies |
| `VolcEngineArkProvider` | ❌ Not implemented | — | No classes, config, or dependencies |
| `AnthropicProvider` | ❌ Not implemented | — | No classes, config, or dependencies |
| `AmazonBedrockProvider` | ❌ Not implemented | — | No classes, config, or dependencies |

---

### Current Model Router

**Status:** Simple, single-provider routing

```java
@Component
public class SimpleModelRouter implements ModelRouter {
    private final String defaultProvider;
    
    @Override
    public String route(String capability) {
        // Ignores capability entirely
        return defaultProvider;
    }
}
```

**Limitations:**
- No capability-based routing
- No fallback strategy
- No health checking
- No load balancing

---

## Future Integration Plans

### Phase 1: OpenAI Integration (Q3 2026)

**Goal:** Replace stub with real OpenAI integration

**Scope:**
- Implement `OpenAiChatProvider` with Spring AI `ChatClient`
- Configure API key via environment variables
- Add error handling and retry logic
- Add token counting and rate limiting

**Implementation:**
```java
@Component
@ConditionalOnProperty(name = "app.ai.providers.openai.enabled")
public class OpenAiChatProvider implements ChatProvider {
    
    private final ChatClient chatClient;
    
    public OpenAiChatProvider(
        @Value("${app.ai.providers.openai.api-key}") String apiKey,
        ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
            .apiKey(apiKey)
            .build();
    }
    
    @Override
    public ChatResult chat(ChatRequest request) {
        var response = chatClient.prompt(request.prompt())
            .call()
            .content();
            
        return new ChatResult("openai", "gpt-4", response);
    }
}
```

**Configuration:**
```yaml
app:
  ai:
    providers:
      openai:
        enabled: true
        api-key: ${OPENAI_API_KEY}
        organization: ${OPENAI_ORG}
        base-url: https://api.openai.com/v1
        default-model: gpt-4
        max-tokens: 4000
        temperature: 0.7
```

**Deliverables:**
- [ ] `OpenAiChatProvider` implementation
- [ ] API key configuration via Vault/env vars
- [ ] Error handling (rate limits, timeouts, invalid requests)
- [ ] Token counting and cost tracking
- [ ] Integration tests (mock + real API)
- [ ] Documentation

---

### Phase 2: Multi-Provider Support (Q4 2026)

**Goal:** Support multiple AI providers with capability-based routing

**New Providers:**
- Google GenAI (Vertex AI / Studio API)
- Anthropic (Claude)
- Replicate (open-source models)

**Implementation:**
```java
@Component
public class CapabilityModelRouter implements ModelRouter {
    private final Map<String, List<ChatProvider>> capabilityProviders;
    
    @Override
    public String route(String capability) {
        List<ChatProvider> providers = capabilityProviders.get(capability);
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("No provider for capability: " + capability);
        }
        // Health check, load balancing, fallback
        return selectProvider(providers);
    }
}
```

**Configuration:**
```yaml
app:
  ai:
    providers:
      openai:
        enabled: true
        capabilities: ["script-generation", "summarize", "translate"]
        default-model: gpt-4
      google:
        enabled: true
        api-key: ${GOOGLE_API_KEY}
        project: ${GOOGLE_PROJECT}
        capabilities: ["script-generation", "image-generation"]
        default-model: gemini-pro
      anthropic:
        enabled: true
        api-key: ${ANTHROPIC_API_KEY}
        capabilities: ["summarize", "translate"]
        default-model: claude-3-opus
      replicate:
        enabled: true
        api-key: ${REPLICATE_API_TOKEN}
        capabilities: ["image-generation", "video-generation"]
```

**Deliverables:**
- [ ] `GoogleGenAiProvider` implementation
- [ ] `AnthropicProvider` implementation
- [ ] `ReplicateProvider` implementation
- [ ] `CapabilityModelRouter` with fallback
- [ ] Provider health checks
- [ ] Load balancing (round-robin, least-latency)
- [ ] Configuration validation

---

### Phase 3: Async & Streaming (Q1 2027)

**Goal:** Support async execution and streaming responses

**Current Limitation:** All AI calls are synchronous and blocking

**Future Design:**
```java
public interface ChatProvider {
    ChatResult chat(ChatRequest request);  // Sync (existing)
    CompletableFuture<ChatResult> chatAsync(ChatRequest request);  // Async
    Flux<ChatResult> chatStream(ChatRequest request);  // Streaming
}
```

**Async Flow:**
```
Client → POST /ai/chat (async=true) → 202 Accepted + Job ID
Client → GET /ai/jobs/{jobId} → Status updates
Client → GET /ai/jobs/{jobId}/result → Final result
```

**Streaming Flow:**
```
Client → POST /ai/chat (stream=true) → SSE stream
Server → data: {"chunk": "..."} (multiple)
Server → data: [DONE]
```

**Deliverables:**
- [ ] `chatAsync()` method in `ChatProvider` interface
- [ ] `chatStream()` method in `ChatProvider` interface
- [ ] Job queue for async execution
- [ ] SSE endpoint for streaming
- [ ] Client SDK with async/streaming support
- [ ] Error handling for async operations

---

### Phase 4: Provider Abstraction Layer (Q2 2027)

**Goal:** Abstract away provider-specific details for portability

**Abstraction Levels:**
1. **Capability:** What the AI can do (script-generation, summarize, translate)
2. **Model:** Specific model (gpt-4, gemini-pro, claude-3-opus)
3. **Provider:** Company/service (OpenAI, Google, Anthropic)

**Unified API:**
```java
// Request specifies capability, not provider
ChatRequest request = ChatRequest.builder()
    .capability("script-generation")
    .prompt("Generate a video script for...")
    .context(Map.of("duration", "30s", "style", "cinematic"))
    .build();

// Router selects best provider based on capability, cost, latency
ChatResult result = aiGateway.chat(request);
```

**Provider Registry:**
```java
@Component
public class ProviderRegistry {
    private final Map<String, ChatProvider> providers;
    
    public ChatProvider getProvider(String name) {
        return providers.get(name);
    }
    
    public List<ChatProvider> getProvidersForCapability(String capability) {
        return providers.values().stream()
            .filter(p -> p.supports(capability))
            .collect(Collectors.toList());
    }
}
```

**Deliverables:**
- [ ] Unified `AiGateway` interface
- [ ] Provider registry with capability mapping
- [ ] Cost/latency-based provider selection
- [ ] Provider fallback chains
- [ ] Provider health monitoring
- [ ] Provider configuration management

---

## Provider Abstraction Design

### Core Interfaces

```java
// Chat Provider SPI
public interface ChatProvider {
    String name();
    boolean supports(String capability);
    ChatResult chat(ChatRequest request);
    CompletableFuture<ChatResult> chatAsync(ChatRequest request);
    Flux<ChatResult> chatStream(ChatRequest request);
    ProviderHealth health();
}

// Model Router
public interface ModelRouter {
    String route(String capability);
    String route(String capability, Map<String, Object> context);
    List<String> getAvailableProviders();
    void reloadConfiguration();
}

// AI Gateway (unified entry point)
public interface AiGateway {
    ChatResult chat(ChatRequest request);
    CompletableFuture<ChatResult> chatAsync(ChatRequest request);
    Flux<ChatResult> chatStream(ChatRequest request);
    List<ProviderInfo> getProviders();
    ProviderHealth getProviderHealth(String provider);
}
```

### Capability Model

```java
public record Capability(
    String name,
    String description,
    List<String> supportedProviders,
    Map<String, Object> defaultConfig
) {}

// Example capabilities
public class Capabilities {
    public static final String SCRIPT_GENERATION = "script-generation";
    public static final String SUMMARIZE = "summarize";
    public static final String TRANSLATE = "translate";
    public static final String IMAGE_GENERATION = "image-generation";
    public static final String VIDEO_GENERATION = "video-generation";
}
```

### Provider Health

```java
public record ProviderHealth(
    String provider,
    Status status,  // HEALTHY, DEGRADED, UNHEALTHY
    double latencyMs,
    double errorRate,
    long requestsLastMinute,
    long tokensUsedLastMinute,
    Map<String, Object> metadata
) {}
```

---

## Integration with Platform

### Prompt Module Integration

**Current:** `PromptTemplateService` uses in-memory storage

**Future:** AI providers generate content from prompt templates

```java
@Service
public class PromptExecutionService {
    private final AiGateway aiGateway;
    private final PromptTemplateService templateService;
    
    public PromptResult execute(String templateId, Map<String, Object> variables) {
        PromptTemplate template = templateService.get(templateId);
        String prompt = template.render(variables);
        
        ChatRequest request = ChatRequest.builder()
            .capability(template.getCapability())
            .prompt(prompt)
            .build();
        
        ChatResult result = aiGateway.chat(request);
        return new PromptResult(result.content(), result.provider(), result.model());
    }
}
```

### Render Module Integration

**Future:** AI generates render parameters, scripts, or effects

```java
@Service
public class AiRenderAssistant {
    private final AiGateway aiGateway;
    
    public RenderSuggestion suggestRenderSettings(MediaAsset asset) {
        ChatRequest request = ChatRequest.builder()
            .capability("render-optimization")
            .prompt("Suggest render settings for: " + asset.getMetadata())
            .build();
        
        ChatResult result = aiGateway.chat(request);
        return parseRenderSuggestion(result.content());
    }
}
```

### Commerce Module Integration

**Future:** AI generates product descriptions, marketing copy

```java
@Service
public class AiCommerceAssistant {
    private final AiGateway aiGateway;
    
    public String generateProductDescription(Product product) {
        ChatRequest request = ChatRequest.builder()
            .capability("content-generation")
            .prompt("Generate product description for: " + product.getName())
            .context(Map.of("tone", "professional", "length", "200 words"))
            .build();
        
        ChatResult result = aiGateway.chat(request);
        return result.content();
    }
}
```

---

## Security & Governance

### API Key Management

**Rule:** Never commit API keys to Git

**Strategy:**
1. Environment variables: `OPENAI_API_KEY`, `GOOGLE_API_KEY`, etc.
2. Secrets management: Vault, AWS Secrets Manager, GCP Secret Manager
3. Service accounts: For cloud providers (Google, AWS)
4. Rotation: Regular key rotation (quarterly)

**Configuration:**
```yaml
app:
  ai:
    providers:
      openai:
        api-key: ${OPENAI_API_KEY}  # From env var
      google:
        credentials-file: ${GOOGLE_APPLICATION_CREDENTIALS}  # From file
      anthropic:
        api-key: ${ANTHROPIC_API_KEY}  # From env var
```

### Rate Limiting

**Per-Provider Limits:**
- OpenAI: 60 RPM (default), 90,000 TPM
- Google: 60 RPM, 60,000 TPM
- Anthropic: 60 RPM, 100,000 TPM

**Implementation:**
```java
@Component
public class RateLimiter {
    private final Map<String, RateLimit> providerLimits;
    
    public boolean allowRequest(String provider) {
        RateLimit limit = providerLimits.get(provider);
        return limit.tryAcquire();
    }
}
```

### Cost Tracking

**Metrics:**
- Tokens used per provider
- Cost per request (based on model pricing)
- Cost per tenant/project
- Budget alerts

**Implementation:**
```java
@Component
public class CostTracker {
    private final MeterRegistry registry;
    
    public void trackUsage(String provider, String model, int tokens, double cost) {
        registry.counter("ai.cost.total",
            "provider", provider,
            "model", model
        ).increment(cost);
        
        registry.counter("ai.tokens.total",
            "provider", provider,
            "model", model
        ).increment(tokens);
    }
}
```

---

## Migration Path

### Step 1: Enable Spring AI Adapter (Q3 2026)

```kotlin
// platform-app/build.gradle.kts
dependencies {
    implementation(project(":spring-ai-adapter"))
}
```

```yaml
# application.yml
platform:
  ai:
    spring-ai:
      enabled: true
```

### Step 2: Configure OpenAI (Q3 2026)

```bash
# Set API key
export OPENAI_API_KEY=sk-...

# Or use Vault
export SPRING_CLOUD_VAULT_ENABLED=true
export SPRING_CLOUD_VAULT_TOKEN=...
```

### Step 3: Enable Real Provider (Q3 2026)

```yaml
app:
  ai:
    default-chat-provider: openAiChatProvider
    providers:
      openai:
        enabled: true
        api-key: ${OPENAI_API_KEY}
```

### Step 4: Add More Providers (Q4 2026)

```yaml
app:
  ai:
    providers:
      google:
        enabled: true
        api-key: ${GOOGLE_API_KEY}
      anthropic:
        enabled: true
        api-key: ${ANTHROPIC_API_KEY}
```

---

## Testing Strategy

### Unit Tests

```java
@ExtendWith(MockitoExtension.class)
class OpenAiChatProviderTest {
    @Mock
    private ChatClient chatClient;
    
    @Test
    void shouldCallOpenAiApi() {
        // Given
        when(chatClient.prompt(any())).thenReturn(...);
        
        // When
        ChatResult result = provider.chat(new ChatRequest("summarize", "test"));
        
        // Then
        assertThat(result.provider()).isEqualTo("openai");
    }
}
```

### Integration Tests

```java
@SpringBootTest
@TestPropertySource(properties = {
    "app.ai.providers.openai.enabled=true",
    "OPENAI_API_KEY=test-key"
})
class OpenAiChatProviderIntegrationTest {
    @Autowired
    private ChatProvider provider;
    
    @Test
    void shouldGenerateScript() {
        ChatResult result = provider.chat(
            new ChatRequest("script-generation", "Generate a 30s video script")
        );
        assertThat(result.content()).isNotEmpty();
    }
}
```

### Mock Tests

```java
@SpringBootTest
class AiGatewayMockTest {
    @MockBean
    private ChatProvider openAiProvider;
    
    @Test
    void shouldFallbackToStub() {
        when(openAiProvider.chat(any())).thenThrow(new RuntimeException("API error"));
        
        ChatResult result = aiGateway.chat(new ChatRequest("summarize", "test"));
        assertThat(result.provider()).isEqualTo("stub");
    }
}
```

---

## Monitoring & Observability

### Metrics

```java
// Request count
Counter.builder("ai.requests.total")
    .tag("provider", "openai")
    .tag("capability", "script-generation")
    .register(registry);

// Latency
Timer.builder("ai.latency")
    .tag("provider", "openai")
    .register(registry);

// Tokens used
Counter.builder("ai.tokens.used")
    .tag("provider", "openai")
    .tag("model", "gpt-4")
    .register(registry);

// Errors
Counter.builder("ai.errors.total")
    .tag("provider", "openai")
    .tag("error", "rate-limit")
    .register(registry);
```

### Dashboards

- **Provider Health:** Latency, error rate, availability
- **Usage:** Requests per minute, tokens per minute
- **Cost:** Cost per provider, cost per tenant
- **Capabilities:** Which capabilities are used most

### Alerts

- **Provider Down:** Error rate > 10% for 5 minutes
- **Rate Limit Hit:** Rate limit errors > 5 per minute
- **High Latency:** P95 latency > 5 seconds
- **Budget Exceeded:** Cost > 80% of budget

---

## Open Questions

1. **Provider priority:** Should we prefer cheaper providers or faster providers?
2. **Fallback strategy:** Fail over to another provider or return error?
3. **Caching:** Should we cache AI responses for identical prompts?
4. **Prompt versioning:** Should we version prompt templates?
5. **A/B testing:** Should we support A/B testing different providers?

---

## Related Documentation

- [AI Engine SPI](../ai-engine-spi.md)
- [Spring AI Adapter](../../spring-ai-adapter/README.md)
- [Prompt Module](../../prompt-module/README.md)
- [Current System State](../architecture/current/current-system-state.md)

---

*This roadmap describes the future vision for AI provider ecosystem integration. Current implementation is limited to stub provider in `ai-module`. Spring AI is isolated and not in the active runtime path. All timelines are targets and subject to change based on priorities and resources.*
