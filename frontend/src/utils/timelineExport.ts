/**
 * 编辑器时间线 → 提交/AI 编辑用的 JSON 字符串。
 * 后端 AiTimelineEditService 会将 2.x 编辑器结构规范化为 Internal Timeline 1.0。
 */
export function buildEditorTimelineJson(
  editorState: Record<string, unknown>,
  clips: unknown[],
  editedOverride?: string | null
): string {
  if (editedOverride?.trim()) {
    return editedOverride
  }
  return JSON.stringify({
    ...editorState,
    clips,
    schemaVersion: '2.0.0',
    exportHint: 'editor-v2',
  })
}
