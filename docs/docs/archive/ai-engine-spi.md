# AI Engine SPI

> **Last updated**: 2026-05-12
> **Status**: Stub implementation active, real providers not yet integrated
> **Module**: `ai-module`

## SPI Overview

### Core Interface

```java
public interface ChatProvider {
    ChatResult chat(ChatRequest request);
}

public record ChatRequest(String capability, String prompt) {}
public record ChatResult(String provider, String model, String content) {}
```

**Provider Discovery:**
```java
// Spring-managed beans with names
Map<String, ChatProvider> providers = {
    "stubChatProvider": StubChatProvider,
    "openAiChatProvider": OpenAiChatProvider (placeholder)
}
```

## Current Provider Implementations

### 1. StubChatProvider (Active Default)

**Status:** ✅ **Functional Mock / Development Stub**

**Capabilities:**
- `script-generation`: Generates structured JSON video scripts
- `summarize`: Creates text summaries (stubbed)
- `translate`: Language translation (stubbed)

**Implementation Details:**
```java
@Component
public class StubChatProvider implements ChatProvider {
    @Override
    public ChatResult chat(ChatRequest request) {
        // Simulate AI processing
        Thread.sleep(10 + random*50);
        
        // Generate mock response based on capability
        String content = switch (request.capability()) {
            case "script-generation" -> generateScript(request.prompt());
            case "summarize" -> "Summary of: " + request.prompt();
            case "translate" -> "Translated: " + request.prompt();
            default -> "Mock response for " + request.capability();
        };
        
        return new ChatResult("stub", "local-dev-model", content);
    }
}
```

**Features:**
- ✅ Deterministic output structure
- ✅ Configurable failure simulation
- ✅ Retry logic (max 3 attempts)
- ✅ Micrometer metrics
- ✅ Latency simulation

**Configuration:**
```yaml
app:
  ai:
    default-chat-provider: stubChatProvider
    stub:
      failure-rate: 0.0
      enable-failures: false
      max-retries: 3
      retry-delay-ms: 1000
```

### 2. OpenAiChatProvider (Placeholder)

**Status:** ❌ **Not Implemented / Placeholder**

**Current Code:**
```java
@Component
@ConditionalOnProperty(name = "app.ai.default-chat-provider", 
                       havingValue = "openAiChatProvider")
public class OpenAiChatProvider implements ChatProvider {
    @Override
    public ChatResult chat(ChatRequest request) {
        return new ChatResult("openai", "gpt-4", 
            "Replace with Spring AI ChatClient integration");
    }
}
```

**Dependencies Present:**
```kotlin
// platform-app/build.gradle.kts
implementation("org.springframework.ai:spring-ai-starter-model-openai")
```

**Missing Implementation:**
- No API key configuration
- No `ChatClient` wiring
- No error handling
- No token estimation
- No rate limiting

## Non-Existent Providers

### Providers Mentioned But Not Present

| Provider | Status | Evidence |
|----------|--------|----------|
| **Replicate** | ❌ Not implemented | No classes, no config, no dependencies |
| **Google GenAI** | ❌ Not implemented | No classes, no config, no dependencies |
| **VolcEngine Ark** | ❌ Not implemented | No classes, no config, no dependencies |
| **Anthropic** | ❌ Not implemented | No classes, no config, no dependencies |
| **Amazon Bedrock** | ❌ Not implemented | No classes, no config, no dependencies |

## Model Router

### Current Implementation

```java
@Component
public class SimpleModelRouter implements ModelRouter {
    private final String defaultProvider;
    
    public SimpleModelRouter(
        @Value("${app.ai.default-chat-provider:stubChatProvider}") 
        String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }
    
    @Override
    public String route(String capability) {
        // ❌ Ignores capability entirely
        return defaultProvider;
    }
}
```

**Limitations:**
- No capability-based routing
- No fallback strategy
- No health checking
- No load balancing

### Future Router Design

```java
public interface ModelRouter {
    String route(String capability);
    String route(String capability, Map<String, Object> context);
    List<String> getAvailableProviders();
    void reloadConfiguration();
}

// Planned: Capability-based routing
@Component
public class CapabilityModelRouter implements ModelRouter {
    private final Map<String, List<String>> capabilityProviders;
    
    @Override
    public String route(String capability) {
        List<String> providers = capabilityProviders.get(capability);
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("No provider for capability: " + capability);
        }
        // TODO: Health check, load balancing, fallback
        return providers.get(0);
    }
}
```

## Async Execution Flow

### Current: Synchronous Only

```mermaid
graph TD
    A[Client Request] --> B[AiController]
    B --> C[AiGatewayService]
    C --> D[ModelRouter.route]
    D --> E[ChatProvider.chat]
    E --> F[Return Result]
    F --> G[Client Response]
    
    style E fill:#f9f,stroke:#333
    note over E "Synchronous blocking call"
```

**No async support:**
- No `@Async` annotation
- No `CompletableFuture` return type
- No callback mechanism
- No WebSocket streaming

### Future: Async Design

```java
public interface ChatProvider {
    ChatResult chat(ChatRequest request);  // Sync
    CompletableFuture<ChatResult> chatAsync(ChatRequest request);  // Async
    Flux<ChatResult> chatStream(ChatRequest request);  // Streaming
}
```

**Async Flow:**
```
Client → Request accepted (202 Accepted) → Job ID
Client → Poll /status/{jobId} → Status updates
Client → GET /result/{jobId} → Final result
```

## Error Handling

### StubChatProvider Retry Logic

```java
public ChatResult chat(ChatRequest request) {
    int attempt = 0;
    while (attempt <= maxRetries) {
        try {
            // Simulate processing
            Thread.sleep(latency);
            
            // Simulate failure
            if (enableFailures && random.nextDouble() < failureRate) {
                throw new RuntimeException("Simulated AI failure");
            }
            
            return generateMockResult(request);
            
        } catch (Exception e) {
            if (attempt == maxRetries) {
                throw new PlatformException("AI provider failed after retries");
            }
            attempt++;
            Thread.sleep(retryDelayMs);
        }
    }
}
```

### Error Types

| Error Type | Handling Strategy |
|------------|------------------|
| `ProviderNotFound` | 500 Internal Server Error |
| `ModelNotAvailable` | 503 Service Unavailable |
| `RateLimited` | 429 Too Many Requests |
| `InvalidPrompt` | 400 Bad Request |
| `Timeout` | 504 Gateway Timeout |

## API Key Configuration

### Current: No Real Configuration

**Missing Properties:**
```yaml
# ❌ NOT PRESENT in ai-module
app:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      organization: ${OPENAI_ORG}
      base-url: https://api.openai.com/v1
    google:
      api-key: ${GOOGLE_API_KEY}
      project: ${GOOGLE_PROJECT}
```

**Stub Only:**
```yaml
# ✅ Only stub configuration exists
app:
  ai:
    default-chat-provider: stubChatProvider
    stub:
      failure-rate: 0.0
      enable-failures: false
```

### Security Rules

**DO NOT:**
- Commit real API keys to Git
- Store keys in plain text
- Log key prefixes or hashes
- Expose keys in error messages

**DO:**
- Use environment variables
- Use secrets management (Vault, AWS Secrets Manager)
- Use service account credentials
- Rotate keys regularly

## Provider Integration Guide

### Adding a New Provider

**Step 1: Implement ChatProvider**

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
        // Real implementation
        var response = chatClient.prompt(request.prompt())
            .call()
            .content();
            
        return new ChatResult("openai", "gpt-4", response);
    }
}
```

**Step 2: Register Capabilities**

```yaml
app:
  ai:
    providers:
      openai:
        enabled: true
        capabilities: ["script-generation", "summarize", "translate"]
        default-model: gpt-4
        max-tokens: 4000
        temperature: 0.7
```

**Step 3: Add Router Logic**

```java
@Component
public class CapabilityModelRouter implements ModelRouter {
    private final Map<String, ChatProvider> providers;
    
    @Override
    public String route(String capability) {
        return providers.entrySet().stream()
            .filter(e -> e.getValue().supports(capability))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No provider for: " + capability));
    }
}
```

**Step 4: Add Error Handling**

```java
@ExceptionHandler(ApiException.class)
public ProblemDetail handleAiError(ApiException ex) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.from(ex.getStatusCode()),
        ex.getMessage()
    );
}
```

### Provider-Specific Considerations

**OpenAI:**
- Rate limits: 60 RPM default
- Token counting required
- Streaming support available
- Cost tracking needed

**Google GenAI:**
- Vertex AI vs. Studio API
- Project-based authentication
- Batch processing support
- Custom model training

**Replicate:**
- Async prediction support
- Webhook callbacks
- Model versioning
- Cost estimation

## Testing Strategy

### Unit Tests
```bash
./gradlew :ai-module:test
```

**Coverage:**
- StubChatProvider behavior
- Router logic
- Error scenarios
- Metrics collection

### Integration Tests
```bash
# No real provider tests yet
# All tests use stub implementation
```

### Mock Testing
```java
@ExtendWith(MockitoExtension.class)
class AiGatewayServiceTest {
    @Mock
    private ChatProvider mockProvider;
    
    @Test
    void testProviderRouting() {
        when(mockProvider.chat(any()))
            .thenReturn(new ChatResult("test", "model", "response"));
        
        // Test service logic
    }
}
```

## Monitoring and Observability

### Current Metrics (Stub Only)

```java
// StubChatProvider metrics
Counter.builder("ai.provider.requests")
    .tag("provider", "stub")
    .register(registry);

Counter.builder("ai.provider.successes")
    .tag("provider", "stub")
    .register(registry);

Counter.builder("ai.provider.failures")
    .tag("provider", "stub")
    .tag("error", "simulated")
    .register(registry);

Timer.builder("ai.provider.processing.time")
    .tag("provider", "stub")
    .register(registry);
```

### Future Metrics

```java
// Planned for real providers
Counter.builder("ai.provider.tokens.used")
    .tag("provider", "openai")
    .tag("model", "gpt-4")
    .register(registry);

Timer.builder("ai.provider.latency")
    .tag("provider", "openai")
    .tag("operation", "completion")
    .register(registry);

Gauge.builder("ai.provider.rate.limit.remaining")
    .tag("provider", "openai")
    .register(registry);
```

## Next Steps

### P0 (Immediate)
1. ✅ Stub provider (done)
2. ✅ Basic routing (done)
3. 🔄 API key configuration design (in progress)
4. 🔄 OpenAI implementation (planned)

### P1 (Next Quarter)
1. 📋 Real OpenAI integration
2. 📋 Async support
3. 📋 Rate limiting
4. 📋 Token counting

### P2 (Future)
1. 📋 Google GenAI integration
2. 📋 Replicate integration
3. 📋 Multi-provider fallback
4. 📋 Cost tracking

---

*This document reflects the current state as of 2026-05-12. Only stub implementation exists; real provider integration is pending.*