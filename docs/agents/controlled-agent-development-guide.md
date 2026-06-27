# Controlled Agent Development Guide

本文档描述当前 `media-platform` 项目的 agent 开发环境、工具分工、LLM 配置方式、工作流、安全边界和当前使用策略。

当前状态：**多 agent 并行开发暂缓**。
当前策略：**先使用单 agent / 单 worktree / 人工 gate 的受控开发流程**，完成 P0 hardening 后再逐步开放多 agent。

---

## 1. 当前目标

本阶段目标不是立即进入完全自动开发，而是建立一个安全、可回滚、可审查的 agent 开发闭环。

目标流程：

```text
Issue / Task
  ↓
创建独立 git worktree
  ↓
单个 Coding Agent 执行
  ↓
本地测试
  ↓
人工 diff review
  ↓
CrewAI / Kiro 辅助审查
  ↓
人工决定 commit / push / merge
```

当前禁止：

```text
多个 agent 同时修改同一个 worktree
agent 自动 merge
agent 自动部署生产
agent 使用生产 secrets
agent 直接修改主仓库
agent 大范围重构
```

---

## 2. 目录结构

推荐本机目录结构：

```text
~/workspace/
  projects/
    media-platform/                         # 主仓库
  worktrees/
    media-platform/
      issue-001-jwt-secret-hardening/
      issue-002-stripe-verify-payment/
      issue-003a-modularity-test-investigation/
  agents/
    llm/
      llm-defaults.env                      # 各工具默认 LLM profile
    openhands/
      openhands.env                         # OpenHands Docker runtime 配置
      state/                                # OpenHands 状态目录
  secrets/
    llm/
      providers.env                         # LLM provider 密钥和 base_url
  orchestrator/
    crewai-lab/
      delivery_review_crew/                 # CrewAI review/demo crew
    langgraph-control-plane/                # 后续 LangGraph 控制面
  reports/
    media-platform/                         # 临时报告 / diff / review 输入输出
  bin/
    llm-use
    opencode-default
    aider-default
    openhands-default
    openhands-up
    openhands-down
    openhands-logs
    openhands-status
  archives/
```

---

## 3. LLM 配置控制面

当前采用统一 LLM profile 控制面。

### 3.1 `providers.env`

路径：

```text
~/workspace/secrets/llm/providers.env
```

职责：

```text
只保存 provider 事实：
- API key
- base_url
- 原始模型名
```

不保存：

```text
某个工具默认用哪个模型
Docker 镜像
端口
容器名
项目策略
```

示例结构：

```env
MIMO_API_KEY="..."
MIMO_BASE_URL="https://token-plan-cn.xiaomimimo.com/v1"
MIMO_MODEL_FAST="mimo-v2-pro"
MIMO_MODEL_PRO="mimo-v2-pro"
MIMO_MODEL_CODER="mimo-v2-pro"

DEEPSEEK_API_KEY="..."
DEEPSEEK_BASE_URL="https://api.deepseek.com"
DEEPSEEK_MODEL_FAST="deepseek-v4-flash"
DEEPSEEK_MODEL_PRO="deepseek-v4-pro"

SILICONFLOW_API_KEY="..."
SILICONFLOW_BASE_URL="https://api.siliconflow.cn/v1"
SILICONFLOW_MODEL_CODER="Qwen/Qwen3-Coder-30B-A3B-Instruct"

NVIDIA_API_KEY="..."
NVIDIA_BASE_URL="https://integrate.api.nvidia.com/v1"
NVIDIA_MODEL_REVIEW="nvidia/llama-3.3-nemotron-super-49b-v1.5"
NVIDIA_MODEL_CODER="qwen/qwen3-coder-480b-a35b-instruct"

LONGCAT_API_KEY="..."
LONGCAT_BASE_URL="https://api.longcat.chat/openai/v1"
LONGCAT_MODEL_PRO="LongCat-2.0-Preview"

HF_API_KEY="..."
HF_BASE_URL="https://router.huggingface.co/v1"
HF_MODEL_CODER="..."
```

权限要求：

```bash
chmod 600 ~/workspace/secrets/llm/providers.env
```

---

### 3.2 `llm-defaults.env`

路径：

```text
~/workspace/agents/llm/llm-defaults.env
```

职责：

```text
保存每个工具默认使用哪个 profile。
不保存密钥。
```

当前推荐内容：

```env
# Global LLM defaults for local agent tools.
# This file should not contain API keys.

LLM_DEFAULT_PROFILE=mimo-pro

# CLI / scriptable tools
LLM_CREWAI_PROFILE=mimo-pro
LLM_OPENCODE_PROFILE=mimo-coder
LLM_AIDER_PROFILE=mimo-coder
LLM_OPENHANDS_PROFILE=mimo-coder
LLM_LANGGRAPH_PROFILE=mimo-pro

# IDE / GUI tools
LLM_KILO_PROFILE=mimo-coder
LLM_KIRO_PROFILE=kiro-native

# Review / fallback profiles
LLM_FALLBACK_PROFILE=deepseek-pro
LLM_REVIEW_PROFILE=nvidia-review
```

修改默认模型时，只改这里，例如：

```env
LLM_OPENCODE_PROFILE=deepseek-pro
LLM_AIDER_PROFILE=siliconflow-coder
LLM_CREWAI_PROFILE=mimo-pro
```

---

### 3.3 `llm-use`

路径：

```text
~/workspace/bin/llm-use
```

职责：

```text
profile -> tool-specific env/config
```

支持目标：

```text
llm-use show [profile]
llm-use env [profile]
llm-use crewai <project-dir> [profile]
llm-use opencode [profile]
llm-use openhands-env [profile]
llm-use kilo-show [profile]
llm-use kiro-show
```

示例：

```bash
llm-use show mimo-pro
llm-use crewai ~/workspace/orchestrator/crewai-lab/delivery_review_crew
llm-use opencode mimo-coder
eval "$(llm-use env mimo-coder)"
eval "$(llm-use openhands-env mimo-coder)"
```

注意：不同工具的模型格式不同，`llm-use` 负责适配。

```text
Raw model:
  mimo-v2-pro

CrewAI:
  MODEL=mimo-v2-pro
  OPENAI_MODEL_NAME=mimo-v2-pro
  CREWAI_MODEL=mimo-v2-pro

OpenCode:
  model=mimo/mimo-v2-pro

Aider:
  --model openai/mimo-v2-pro

OpenHands:
  LLM_MODEL=openai/mimo-v2-pro
```

---

## 4. 工具分工

### 4.1 Kiro

当前定位：

```text
需求分析
代码审查
架构调查
单 issue 修复
生成 spec / report
```

当前不纳入 `llm-use`，使用 Kiro 原生账号/模型配置。

适合任务：

```text
P0 hardening 单项修复
架构评估
ModularityTest 调查
支付流程调查
文档与实现一致性审查
```

当前已用于：

```text
issue-001 JWT secret fail-fast
issue-002 Stripe verifyPayment real verification
issue-003a ModularityTest investigation
```

使用原则：

```text
一次只给一个问题
要求先调查再修改
要求写报告到 docs/review/
禁止扩大范围
禁止自动提交
```

---

### 4.2 OpenCode

当前定位：

```text
主力 coding agent
适合小到中等代码改动
适合在 worktree 中执行明确任务
```

启动方式：

```bash
cd ~/workspace/worktrees/media-platform/<issue-worktree>
opencode-default
```

临时切 profile：

```bash
opencode-default deepseek-pro
```

OpenCode 配置由以下命令生成：

```bash
llm-use opencode
```

生成文件：

```text
~/.config/opencode/opencode.jsonc
```

OpenCode 不直接读取 CrewAI `.env`，必须通过 `opencode-default` 注入 LLM 环境变量。

---

### 4.3 Aider

当前定位：

```text
小范围 patch
review 后修补
精修测试
配合人工 pair programming
```

启动方式：

```bash
cd ~/workspace/worktrees/media-platform/<issue-worktree>
aider-default
```

临时切 profile：

```bash
aider-default deepseek-pro
```

指定文件：

```bash
aider-default mimo-coder README.md platform-app/src/main/java/...
```

使用原则：

```text
只给明确文件
只做小修
不做大范围自动探索
```

---

### 4.4 CrewAI

当前定位：

```text
review crew
报告生成
diff 审查
合规性检查
```

当前不作为 coding agent。

CrewAI 项目路径：

```text
~/workspace/orchestrator/crewai-lab/delivery_review_crew
```

生成 `.env`：

```bash
llm-use crewai ~/workspace/orchestrator/crewai-lab/delivery_review_crew
```

运行：

```bash
cd ~/workspace/orchestrator/crewai-lab/delivery_review_crew
crewai run
```

注意：

```text
CrewAI 默认 demo 任务会生成泛化报告，不适合直接用于项目审查。
需要将 tasks.yaml 改为读取具体 diff / review input。
```

---

### 4.5 OpenHands

当前定位：

```text
后续 issue-to-worker 自动开发工具
当前先作为可启动服务，不立即作为 P0 主力
```

安装方式：

```text
Docker / Podman
```

OpenHands LLM 由：

```bash
llm-use openhands-env
```

输出环境变量。

OpenHands runtime 配置文件：

```text
~/workspace/agents/openhands/openhands.env
```

示例：

```env
OPENHANDS_PORT=3010
OPENHANDS_IMAGE=ghcr.io/all-hands-ai/openhands:latest
OPENHANDS_CONTAINER_NAME=openhands-local
OPENHANDS_WORKSPACE_BASE=$HOME/workspace/worktrees
OPENHANDS_STATE_DIR=$HOME/workspace/agents/openhands/state
```

前台启动：

```bash
openhands-default
```

后台启动：

```bash
openhands-up
```

查看状态：

```bash
openhands-status
```

查看日志：

```bash
openhands-logs
```

停止：

```bash
openhands-down
```

访问：

```text
http://localhost:3010
```

或 Tailscale：

```text
http://<tailscale-ip>:3010
```

当前建议：

```text
OpenHands 暂时不处理 P0。
等 JWT、Stripe、ModularityTest 基线稳定后，再让 OpenHands 处理低风险 P1 或文档类 issue。
```

---

### 4.6 Kilo Code

当前定位：

```text
VSCode 内交互式 coding assistant
人工辅助开发
```

当前不自动写配置文件。

查看应填配置：

```bash
llm-use kilo-show
```

手工配置：

```text
Provider: OpenAI Compatible / Custom OpenAI
Base URL: https://token-plan-cn.xiaomimimo.com/v1
Model: mimo-v2-pro
API Key: providers.env 中对应 key
```

---

### 4.7 LangGraph

当前定位：

```text
后续 agent workflow 状态机
当前暂不接管开发流程
```

未来职责：

```text
create_worktree
run_coding_agent
run_tests
collect_diff
run_review
wait_for_human_gate
```

当前不建议立即上 LangGraph。先用手工流程跑通 3-5 个单 agent issue。

---

## 5. 当前开发策略

### 5.1 当前阶段

当前处于：

```text
P0 hardening + 单 agent 验证阶段
```

不是：

```text
多 agent 并行开发阶段
```

原因：

```text
项目仍有 P0 readiness 问题
ModularityTest 仍未恢复
文档与实现存在不一致
多 agent 过早介入会放大边界风险
```

---

### 5.2 P0 Hardening 队列

当前优先级：

```text
P0-1 JWT secret fail-fast
P0-2 Stripe verifyPayment real verification
P0-3 ModularityTest investigation / re-enable
```

已完成或进行中：

```text
issue-001-jwt-secret-hardening
  - JwtAuthFilter 构造 fail-fast
  - ProductionSafetyValidatorTest 增强
  - JwtAuthFilterTest 增强
  - 报告：docs/review/issue-001-jwt-secret-hardening.md

issue-002-stripe-verify-payment
  - StripeHttpPaymentProvider.verifyPayment 改为真实 Checkout Session 查询
  - fail closed
  - 新增 StripeHttpPaymentProviderTest
  - 报告：docs/review/issue-002-stripe-verify-payment.md

issue-003a-modularity-test-investigation
  - 调查 ModularityTest disabled 原因
  - 暂不修复生产代码
  - 目标报告：docs/review/issue-003a-modularity-test-investigation.md
```

---

## 6. 标准工作流

### 6.1 创建 worktree

```bash
cd ~/workspace/projects/media-platform
git fetch --all --prune

git worktree add \
  ~/workspace/worktrees/media-platform/issue-XXX-short-name \
  -b agent/issue-XXX-short-name
```

进入：

```bash
cd ~/workspace/worktrees/media-platform/issue-XXX-short-name
```

确认：

```bash
git status --short
git branch --show-current
pwd
```

---

### 6.2 写任务文件

每个 worktree 建议放：

```text
AGENT_TASK.md
```

模板：

```markdown
# Task: issue-XXX-short-name

## Goal

Describe one focused goal.

## Scope

Describe exact files / modules to inspect.

## Hard Rules

- Work only in this worktree.
- Keep the diff small.
- Do not modify Flyway V1 baseline.
- Do not introduce H2.
- Do not enable Spring AI runtime.
- Do not add spring-modulith-starter-insight.
- Do not weaken ProductionSafetyValidator.
- Do not disable security globally.
- Do not modify production deployment configuration.
- Do not commit secrets.
- Do not auto-merge.
- Do not deploy.

## Required Tests

List targeted tests.

## Deliverable

Write report to:

docs/review/issue-XXX-short-name.md
```

---

### 6.3 执行 agent

Kiro：

```text
打开 Kiro，粘贴完整提示词。
```

OpenCode：

```bash
opencode-default
```

Aider：

```bash
aider-default
```

OpenHands：

```bash
openhands-up
```

当前原则：

```text
一个 issue 只用一个主 coding agent。
不要多个 agent 同时改同一个 worktree。
```

---

### 6.4 人工检查

agent 完成后：

```bash
git status --short
git diff --stat
git diff
```

检查：

```text
是否只改了目标文件
是否有无关大改
是否有 secret
是否碰了禁止区域
是否报告已写入 docs/review/
```

secret 检查：

```bash
git diff | grep -Ei 'sk_live|sk_test|whsec|api[_-]?key|secret' || true
```

---

### 6.5 测试

优先跑 targeted tests。

示例：

```bash
./gradlew :platform-app:test \
  --tests 'com.example.platform.security.JwtAuthFilterTest' \
  --tests 'com.example.platform.production.ProductionSafetyValidatorTest'
```

Payment 示例：

```bash
./gradlew :payment-module:test
```

如果不确定任务名：

```bash
./gradlew tasks --all | grep -E 'test|platform|payment'
```

---

### 6.6 报告

每个 issue 都应有项目内报告：

```text
docs/review/issue-XXX-short-name.md
```

报告结构：

```markdown
# Issue XXX Report

## Executive Summary
## Root Cause / Investigation Findings
## Files Changed
## Behavior Changed
## Tests Added
## Commands Run
## Test Results
## Remaining Risks
## Follow-ups
```

调查类任务可使用：

```markdown
# Issue XXX Investigation Report

## Executive Summary
## Test Location / Code Location
## Commands Run
## Findings
## Risk Classification
## Recommended Follow-up Issues
## Recommended Next Step
```

---

### 6.7 提交

确认 diff 和测试通过后：

```bash
git add <changed-files>
git commit -m "<type>(<scope>): <summary>"
```

示例：

```bash
git commit -m "fix(security): fail fast on insecure JWT secret"
git commit -m "fix(payment): verify Stripe checkout session status"
git commit -m "test(architecture): investigate modularity boundary failures"
```

不要自动 push / merge，除非人工确认。

---

## 7. 禁止事项

所有 agent 都必须遵守：

```text
禁止修改 Flyway V1 baseline
禁止引入 H2
禁止启用 Spring AI runtime
禁止添加 spring-modulith-starter-insight
禁止弱化 ProductionSafetyValidator
禁止关闭生产安全校验
禁止提交 secrets
禁止自动 merge
禁止自动生产部署
禁止多个 agent 改同一个 worktree
禁止无边界大重构
```

---

## 8. 多 agent 介入条件

多 agent 正式介入前，至少满足：

```text
P0-1 JWT secret fail-fast 已合入
P0-2 Stripe verifyPayment 已合入
P0-3 ModularityTest 已完成调查，并有明确 re-enable 计划
工具配置控制面稳定
worktree 流程稳定
报告路径统一为 docs/review/
人工 review gate 明确
```

建议 gate：

```text
安全启动不假安全
支付验证不假成功
模块边界问题已知且可控
agent 不直接接触生产 secret
agent 不自动 merge/deploy
```

---

## 9. 多 agent 后续结构

未来开放多 agent 后，推荐角色：

```text
Plan Agent
  - 分解 issue
  - 生成任务边界
  - 禁止直接改代码

Coding Agent
  - 在独立 worktree 中实现
  - 一次只处理一个 issue

Test Agent
  - 跑测试
  - 汇总失败
  - 不自行大改代码

Review Agent
  - 审 diff
  - 检查 hard rules
  - 输出 accept / revise / revert

Deploy Agent
  - 仅 staging
  - 不触碰 production
  - 必须人工 gate
```

早期不要启用 Deploy Agent。

---

## 10. 当前推荐路线

近期顺序：

```text
1. 合入 issue-001 JWT secret fail-fast
2. 合入 issue-002 Stripe verifyPayment
3. 完成 issue-003a ModularityTest investigation
4. 执行 issue-003b ModularityTest re-enable
5. 做 docs/review/current-architecture-facts.md
6. 再开始多 agent 低风险 P1
```

P1 练兵任务：

```text
RemoteRenderDispatcher 与 DB RenderWorkerRegistryService 双轨收敛
PolicyGovernanceService 持久化调查
NLQ 内存/DB 双轨一致性修复
OutboxHealthIndicator 实现
```

P2 后置：

```text
OTel SDK / distributed tracing
完整文档体系整理
OpenHands issue worker 自动化
LangGraph 控制面
```

---

## 11. 文档与实现一致性

当前已发现项目文档与实现存在不一致：

```text
H2 测试数据库描述过时，代码已使用 PostgreSQL Testcontainers
安全默认关闭描述不准确，默认 security.enabled=true
支付全 Stub 描述不准确，Stripe/Hyperswitch 有真实 HTTP provider
健康检查缺失描述不准确，已有 readiness/flyway/temporal/egress checks
OutboxHealthIndicator 文档声称存在但代码缺失
Worker registry 同时存在内存版与 DB 版，需要明确主路径
```

建议单独创建：

```text
docs/review/current-architecture-facts.md
```

或者 issue：

```text
issue-004-docs-architecture-alignment
```

目标：

```text
让后续 humans 和 agents 不再被过时文档误导。
```

文档一致性应单独提交，不要混入 P0 代码修复。

---

## 12. 当前状态摘要

当前环境已具备：

```text
LLM profile 控制面
CrewAI 可运行
OpenCode 可运行
Aider 可运行
OpenHands Docker 可运行
Kiro 可用于单 agent 修复/调查
worktree 流程已明确
```

当前还不建议：

```text
完全自动多 agent 并行开发
自动 merge
自动部署
多个 coding agents 同时修改
```

当前最安全策略：

```text
继续使用 Kiro / OpenCode 做单 issue 修复。
每次只一个 worktree。
每次都写 docs/review 报告。
每次都人工 diff review。
P0 完成后再开放多 agent。
```

