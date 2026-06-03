# 导出流程

> **模块：** `frontend/src/components/export/`
> **最后更新：** 2026-05-18

## 导出面板

导出面板允许用户配置渲染设置并提交渲染任务。

```mermaid
graph TB
    USER["用户点击导出"] --> PANEL["导出面板"]
    PANEL -->|"选择"| PRESET["预设选择"]
    PRESET -->|"显示"| BUDGET["预算估算"]
    BUDGET -->|"校验"| VALID["导出校验"]
    VALID -->|"权益检查"| ENT["权益检查"]
    VALID -->|"配额检查"| QUOTA["配额检查"]
    VALID -->|"预算检查"| BUDGET_CHECK["预算检查"]
    ENT -->|"通过"| SUBMIT["提交渲染任务"]
    QUOTA -->|"通过"| SUBMIT
    BUDGET_CHECK -->|"通过"| SUBMIT
    SUBMIT -->|"POST /api/v1/render/jobs/submit"| BACKEND["后端"]
    BACKEND -->|"jobId"| POLL["轮询任务状态"]
    POLL -->|"已完成"| ARTIFACT["展示成品"]
    ARTIFACT -->|"预览"| PREVIEW["成品预览"]
    ARTIFACT -->|"下载"| DOWNLOAD["下载文件"]
```

## 导出预设

| 预设 | 分辨率 | 格式 | 套餐 |
|------|--------|------|------|
| `free_720p_watermarked` | 720p | MP4 | 免费版 |
| `default_720p` | 720p | MP4 | 免费+ |
| `default_1080p` | 1080p | MP4 | 专业版+ |
| `social_1080p` | 1080p | MP4 | 专业版+ |
| `social_720p` | 720p | MP4 | 专业版+ |
| `mobile_480p` | 480p | MP4 | 免费+ |
| `4k_2160p` | 4K | MP4 | 团队版+ |
| `pro_1080p` | 1080p | MP4 | 专业版+ |
| `team_4k` | 4K | MP4 | 团队版+ |

## 校验链

```mermaid
graph LR
    A["时间轴校验"] --> B["权益校验"]
    B --> C["配额校验"]
    C --> D["预算校验"]
    D --> E["异常检查"]
    E --> F["准备提交"]
```

## 渲染任务轮询

```typescript
// 轮询流程
const pollJobStatus = async (jobId: string) => {
  const response = await fetch(`/api/v1/render/jobs/${jobId}`);
  const job = await response.json();

  if (job.status === 'COMPLETED') {
    showArtifact(job.artifact);
  } else if (job.status === 'FAILED') {
    showError(job.errorCode, job.errorMessage);
  } else {
    setTimeout(() => pollJobStatus(jobId), 2000);
  }
};
```

## 错误处理

| 错误代码 | 描述 | 界面操作 |
|----------|------|----------|
| `RENDER-409-001` | 配额已用完 | 显示升级建议 |
| `ENTITLEMENT-403-001` | 功能不可用 | 显示套餐升级 |
| `RENDER-500-001` | 渲染失败 | 显示重试按钮 |
| `AI-500-001` | AI 生成失败 | 显示重试按钮 |
