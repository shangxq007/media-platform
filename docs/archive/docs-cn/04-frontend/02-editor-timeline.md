# 时间轴交互

> **模块：** `frontend/src/components/timeline/`
> **最后更新：** 2026-05-18

## 时间轴结构

时间轴是视频项目的多轨道视图。它沿时间轴显示片段、字幕和特效。

```
时间 →  00:00    00:30    01:00    01:30    02:00
        ├────────┼────────┼────────┼────────┤
轨道 1  │[====片段 A====][==片段 B==]       │
        ├────────┼────────┼────────┼────────┤
轨道 2  │[======片段 C======]               │
        ├────────┼────────┼────────┼────────┤
字幕    │[__字幕 1__][__字幕 2__][__字幕 3] │
        ├────────┼────────┼────────┼────────┤
        ▲                                  ▲
     播放头                              总时长
```

## 片段操作

| 操作 | 描述 | 状态 |
|------|------|------|
| 插入片段 | 从素材库添加片段到时间轴 | ✅ |
| 移动片段 | 拖动片段到新位置 | ✅ |
| 调整大小 | 修剪片段时长 | ✅ |
| 删除片段 | 从时间轴移除片段 | ✅ |
| 分割片段 | 在播放头位置分割片段 | ✅ |
| 复制/粘贴 | 复制片段 | ✅ |
| 撤销/重做 | 还原/恢复操作 | ✅ |

## 时间轴状态

```typescript
interface TimelineState {
  tracks: Track[];
  clips: Clip[];
  subtitleTracks: SubtitleTrack[];
  playheadPosition: number;
  selectedClipId: string | null;
  zoom: number;
  duration: number;
}
```

## 片段数据模型

```typescript
interface Clip {
  id: string;
  trackId: string;
  startTime: number;    // 秒
  duration: number;     // 秒
  sourceOffset: number; // 源文件裁剪起点
  sourceDuration: number;
  name: string;
  thumbnailUrl?: string;
  effects: Effect[];
}
```

## 字幕轨道

```typescript
interface SubtitleCue {
  id: string;
  startTime: number;
  endTime: number;
  text: string;
  style?: SubtitleStyle;
}
```

## 播放头控制

- 点击时间轴进行定位
- 拖动播放头进行拖拽预览
- 空格键播放/暂停
- 方向键逐帧移动

## 缩放

- 鼠标滚轮缩放
- 缩放范围：10% – 1000%
- 适应视图按钮
