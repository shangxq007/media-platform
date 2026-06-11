# 调度规则

## Provider Eligibility 判断逻辑

调度器必须判断以下条件：

1. **status**：Provider 状态
2. **priority**：Provider 优先级
3. **providerType**：Provider 类型
4. **enabledCapabilities**：当前允许调度的能力
5. **autoDispatch**：是否允许自动调度
6. **runtime**：运行环境
7. **cost**：成本
8. **quality**：质量
9. **mode**：调度模式

## 调度模式

| 模式 | 说明 |
|------|------|
| production | 生产模式，只允许 Production 和明确灰度的 POC provider |
| experiment | 实验模式，允许 POC 和 Spike provider |
| manual | 手动模式，允许所有非 Deprecated provider |

## 调度规则

### 1. Deprecated 永远不可调度

无论任何模式，Deprecated provider 都不能被调度。

### 2. Hold 在 production 模式不可调度

Hold provider 只能在 experiment 或 manual 模式下调度。

### 3. Spike 在 production 模式不可调度

Spike provider 只能在 manual 模式下调度。

### 4. autoDispatch=false 在 production 模式不可自动调度

即使状态允许调度，autoDispatch=false 的 provider 也不允许被自动调度。

### 5. POC 在 production 模式默认不可自动调度

除非明确灰度配置允许。

### 6. requiredCapabilities 必须全部命中 enabledCapabilities

任务的所有 requiredCapabilities 必须全部在 provider 的 enabledCapabilities 中。

### 7. notFor 中包含任务能力时，不能选择该 provider

如果任务的 requiredCapabilities 中有 provider 的 notFor 列表中的能力，不能选择该 provider。

### 8. declaredCapabilities 不能用于实际调度

declaredCapabilities 只用于文档和规划，不能用于实际调度。

### 9. enabledCapabilities 必须作为最终调度判断

调度器只能使用 enabledCapabilities 进行匹配。

### 10. Production 优先于 POC

当多个 provider 匹配时，Production 状态的 provider 优先。

### 11. 优先级排序

P0 > P1 > P2 > P3

### 12. 多 provider 匹配时的排序规则

1. 状态：Production > POC > Optional > Spike > Hold
2. 优先级：P0 > P1 > P2 > P3
3. 成本：低成本优先
4. 质量：高质量优先
5. 运行时：server > external
6. Feature flag：feature flag 匹配优先

### 13. 每次调度必须记录命中原因

记录为什么选择了这个 provider，包括：
- 匹配的 capabilities
- 状态和优先级
- 是否 fallback
- fallback 原因

## Fallback 规则

1. fallback 必须受 job.allowDegrade 控制
2. 不能静默降级导致输出效果不一致
3. fallback 时必须记录 fallback 原因
4. fallback 链中的 provider 也必须满足 eligibility 规则

## allowDegrade 规则

- allowDegrade=true：允许 fallback 到更低优先级的 provider
- allowDegrade=false：不允许 fallback，如果首选 provider 不可用则失败

## LiteFlow 调度规则

### Production Chain

- Deprecated provider 不能进入任何 chain
- Hold provider 默认不进入 production chain
- Spike provider 只能进入 manual / experiment chain
- BMF 只能进入 bmf_spike_test 等手动实验 chain
- Production chain 不允许隐式调用 Spike / Hold / Deprecated provider

### Experiment/Manual Chain

- 可以包含 Spike / Hold provider
- BMF 只能出现在 manual / experiment chain
- 必须明确标记为实验/手动模式

## 代码实现

```java
public class ProviderEligibility {

    public static boolean isEligible(ProviderMetadata metadata, RenderJob job) {
        if (metadata.isDeprecated()) return false;
        if (metadata.isHold() && !"experiment".equals(job.mode()) && !"manual".equals(job.mode())) return false;
        if (metadata.isSpike() && !"experiment".equals(job.mode()) && !"manual".equals(job.mode())) return false;
        if (!metadata.participatesInAutoRouting() && !"manual".equals(job.mode())) return false;
        for (String notFor : metadata.notFor()) {
            if (job.requiredCapabilities().contains(notFor)) return false;
        }
        for (String blocked : job.blockedProviders()) {
            if (metadata.name().equals(blocked)) return false;
        }
        for (String required : job.requiredCapabilities()) {
            if (!metadata.canHandleCapability(required)) return false;
        }
        return true;
    }

    public static int scoreProvider(ProviderMetadata metadata, RenderJob job) {
        int score = 0;
        if (metadata.isProduction()) score += 0;
        else if (metadata.isPoc()) score += 100;
        else if (metadata.isOptional()) score += 200;
        if ("P0".equals(metadata.priority())) score += 0;
        else if ("P1".equals(metadata.priority())) score += 10;
        else if ("P2".equals(metadata.priority())) score += 20;
        else if ("P3".equals(metadata.priority())) score += 30;
        for (String preferred : job.preferredProviders()) {
            if (metadata.name().equals(preferred)) score -= 50;
        }
        return score;
    }
}
```
