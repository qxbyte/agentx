import type { ChatMessage, MessageBlock, ToolCallInfo } from '../types'

/** 工具名 → 活动文案：流式过程中告诉用户"正在做什么"，而非通用转圈 */
const TOOL_ACTIVITY: Record<string, string> = {
  readFile: '正在读取文件',
  listDir: '正在浏览目录',
  findFiles: '正在查找文件',
  grepFiles: '正在搜索代码',
  writeFile: '正在写入代码',
  applyPatch: '正在修改代码',
  runShell: '正在执行命令',
  gitStatus: '正在检查变更',
  gitDiff: '正在检查变更',
  gitCommit: '正在提交变更',
  searchKnowledge: '正在检索知识库',
  webFetch: '正在访问网页',
  webSearch: '正在联网搜索',
}

function isFinished(c: ToolCallInfo): boolean {
  return c.done === true || c.result !== undefined
}

/** 从消息实时状态推导当前活动文案 */
export function activityLabel(message: ChatMessage): string {
  if (message.approvals?.some((a) => a.status === 'pending')) return '等待你的审批'
  const toolBlocks = (message.blocks ?? []).filter(
    (b): b is Extract<MessageBlock, { type: 'tool' }> => b.type === 'tool',
  )
  const running = toolBlocks.filter((c) => !isFinished(c))
  const last = running[running.length - 1]
  if (last) return TOOL_ACTIVITY[last.name] ?? `正在调用 ${last.name}`
  if (message.content) return '正在生成回复'
  if (message.blocks?.some((b) => b.type === 'reasoning')) return '正在思考'
  return '正在处理'
}

/**
 * 流式活动指示器：动态四角星 + 文字扫描微光。
 * 替代原来的闪烁黑方块光标——图标呼吸旋转，文案上有一道左→右的高光扫过。
 */
export default function ActivityIndicator({ label }: { label: string }) {
  return (
    <div className="ax-activity" aria-live="polite">
      <svg viewBox="0 0 24 24" className="ax-activity-icon" fill="currentColor" aria-hidden="true">
        <path d="M12 2c.6 5.4 4.6 9.4 10 10-5.4.6-9.4 4.6-10 10-.6-5.4-4.6-9.4-10-10 5.4-.6 9.4-4.6 10-10z" />
      </svg>
      <span className="ax-activity-label">{label}</span>
    </div>
  )
}
