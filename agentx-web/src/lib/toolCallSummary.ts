import type { ToolCallInfo } from '../types'

/**
 * 工具调用批次的归类摘要：把一批调用按语义分类计数，生成「做了什么」的
 * 人话标签（如「搜索 ×3 · 读取 ×4 · 修改 ×2」），零模型调用。
 * 独立单元：消息内工具组、会话回顾等任何需要概括工具批次的场景可复用。
 */

/** 工具名 → 语义类别（未知工具回落到自身名称） */
const CATEGORY_OF: Record<string, string> = {
  readFile: '读取',
  readAttachment: '读取',
  readSkillFile: '读取',
  listDir: '浏览目录',
  grepFiles: '搜索',
  findFiles: '搜索',
  gitStatus: '查看改动',
  gitDiff: '查看改动',
  webSearch: '联网检索',
  webFetch: '联网检索',
  writeFile: '修改文件',
  applyPatch: '修改文件',
  runShell: '执行命令',
  gitCommit: '提交代码',
  generateDocument: '生成文件',
  generateSpreadsheet: '生成文件',
  saveMemory: '写入记忆',
  dispatchAgent: '派遣子代理',
}

/** 分类标签：按首次出现顺序聚合计数（保留批次的叙事顺序） */
export function summarizeToolCalls(calls: ToolCallInfo[]): string {
  const counts = new Map<string, number>()
  for (const c of calls) {
    const category = CATEGORY_OF[c.name] ?? c.name
    counts.set(category, (counts.get(category) ?? 0) + 1)
  }
  return [...counts.entries()]
    .map(([label, n]) => (n > 1 ? `${label} ×${n}` : label))
    .join(' · ')
}

/** 各工具的「主参数」字段：具体对象如路径/命令等；purpose 归组头 */
const HINT_FIELDS = ['description', 'path', 'filename', 'command', 'query', 'pattern', 'url', 'name', 'content']
const MAX_HINT_CHARS = 48

/** 正在执行的调用的动作自述（purpose 参数）：批次头部「正在：xxx」实时状态用 */
export function runningPurpose(calls: ToolCallInfo[]): string | null {
  for (let i = calls.length - 1; i >= 0; i--) {
    const c = calls[i]
    if (!c || c.done === true || c.result !== undefined) continue
    const hint = argHint(c)
    // 仅当提示来自 purpose 语义（非路径/命令原文）时作为动作陈述展示
    let args = c.args
    if (typeof args === 'string') {
      try {
        args = JSON.parse(args)
      } catch {
        return null
      }
    }
    const purpose = args && typeof args === 'object'
      ? (args as Record<string, unknown>)['purpose'] ?? (args as Record<string, unknown>)['description']
      : null
    return typeof purpose === 'string' && purpose.trim() !== '' ? purpose.trim() : hint
  }
  return null
}

/** 从调用参数提取主对象提示（文件路径/命令/关键词…）；解析失败返回 null */
export function argHint(call: ToolCallInfo): string | null {
  let args = call.args
  if (typeof args === 'string') {
    try {
      args = JSON.parse(args)
    } catch {
      return null
    }
  }
  if (!args || typeof args !== 'object') return null
  const record = args as Record<string, unknown>
  for (const field of HINT_FIELDS) {
    const value = record[field]
    if (typeof value === 'string' && value.trim() !== '') {
      const flat = value.replace(/\s+/g, ' ').trim()
      return flat.length > MAX_HINT_CHARS ? flat.slice(0, MAX_HINT_CHARS) + '…' : flat
    }
  }
  return null
}

/** 组头语义介绍：取组内首个带 purpose 参数的调用的自述（模型说明这批操作在做什么） */
export function groupIntro(calls: ToolCallInfo[]): string | null {
  for (const c of calls) {
    let args = c.args
    if (typeof args === 'string') {
      try {
        args = JSON.parse(args)
      } catch {
        continue
      }
    }
    if (!args || typeof args !== 'object') continue
    const purpose = (args as Record<string, unknown>)['purpose']
    if (typeof purpose === 'string' && purpose.trim() !== '') {
      const flat = purpose.replace(/\s+/g, ' ').trim()
      return flat.length > 24 ? flat.slice(0, 24) + '…' : flat
    }
  }
  return null
}
