# Dependency Injection Policy — media-platform

**Date:** 2026-07-05
**Status:** ACTIVE
**Authority:** APPLICATION-DI.0

---

## Core Rules

### 1. Required Dependencies

```java
// ✅ CORRECT: Constructor injection with final fields
@Service
public class MyService {
    private final MyRepository repo;
    
    @Autowired
    public MyService(MyRepository repo) {
        this.repo = repo;
    }
}

// ❌ WRONG: Field injection
@Service
public class MyService {
    @Autowired
    private MyRepository repo;
}
```

### 2. Optional Single Dependency

```java
// ✅ CORRECT: ObjectProvider
@Service
public class MyService {
    private final OptionalCapability capability;
    
    @Autowired
    public MyService(ObjectProvider<OptionalCapability> capabilityProvider) {
        this.capability = capabilityProvider.getIfAvailable();
    }
}

// ❌ WRONG: @Autowired(required=false)
@Service
public class MyService {
    @Autowired(required = false)
    private OptionalCapability capability;
}
```

### 3. Multiple Optional Implementations

```java
// ✅ CORRECT: List injection
@Service
public class MyService {
    private final List<Capability> capabilities;
    
    @Autowired
    public MyService(List<Capability> capabilities) {
        this.capabilities = capabilities;
    }
}

// ✅ CORRECT: Registry pattern
@Service
public class CapabilityRegistry {
    private final Map<String, Capability> capabilities = new HashMap<>();
    
    @Autowired
    public CapabilityRegistry(List<Capability> available) {
        for (Capability c : available) {
            capabilities.put(c.type(), c);
        }
    }
    
    public Capability get(String type) {
        return Optional.ofNullable(capabilities.get(type))
            .orElseThrow(() -> new CapabilityUnavailableException(type));
    }
}
```

### 4. Conditional Capabilities

```java
// ✅ CORRECT: Conditional on implementation, not on consumer
@Component
@ConditionalOnProperty(prefix = "storage.s3", name = "enabled", havingValue = "true")
public class S3ObjectMaterializer implements ObjectMaterializer {
    // ...
}

// ✅ CORRECT: Consumer depends on interface list
@Service
public class StorageRuntimeService {
    private final List<ObjectMaterializer> materializers;
    
    @Autowired
    public StorageRuntimeService(List<ObjectMaterializer> materializers) {
        this.materializers = materializers;
    }
}

// ❌ WRONG: Consumer depends on conditional concrete bean
@Service
public class StorageRuntimeService {
    private final S3ObjectMaterializer s3Materializer;
    
    @Autowired
    public StorageRuntimeService(S3ObjectMaterializer s3Materializer) {
        this.s3Materializer = s3Materializer;
    }
}
```

---

## Preview Profile Rules

1. Preview should boot with minimal capabilities
2. Disabled optional provider/runner/storage must not break boot
3. Unavailable capability fails at invocation time, not boot time
4. Health endpoint must work without optional capabilities

---

## Forbidden Patterns

- Field injection (`@Autowired` on fields)
- `@Autowired(required=false)` on constructor parameters
- Direct dependency on conditional concrete beans
- Default constructors on required services
- Hiding missing dependencies with null without explicit design intent

---

## Allowed Exceptions

- Test classes
- Spring Boot test injection
- Configuration classes where annotation is idiomatic
- Framework integration edge cases
- Temporary bridge with documented cleanup task

---

*Document created by APPLICATION-DI.0*
