/**
 * unified diff 解析（纯函数，与渲染分离，便于单测）。
 * 支持多文件（diff --git / --- / +++）、多 hunk（@@ -a,b +c,d @@），
 * 逐行标注新增/删除/上下文并计算双列行号与 +N/-M 统计。
 */

export type DiffLineType = 'add' | 'del' | 'ctx' | 'hunk' | 'meta'

export interface DiffLine {
  type: DiffLineType
  text: string
  /** 旧文件行号（add 行为空） */
  oldNo?: number
  /** 新文件行号（del 行为空） */
  newNo?: number
}

export interface DiffFile {
  path: string
  added: number
  removed: number
  lines: DiffLine[]
}

const HUNK_RE = /^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@/

/** 从 +++ b/xxx 或 --- a/xxx 提取路径（去掉 a//b/ 前缀）。 */
function stripPrefix(p: string): string {
  return p.replace(/^[ab]\//, '').trim()
}

export function parseUnifiedDiff(diff: string): DiffFile[] {
  const files: DiffFile[] = []
  let current: DiffFile | null = null
  let oldNo = 0
  let newNo = 0

  const newFile = (path: string): DiffFile => {
    const f: DiffFile = { path, added: 0, removed: 0, lines: [] }
    files.push(f)
    return f
  }

  for (const raw of (diff ?? '').split('\n')) {
    if (raw.startsWith('diff --git')) {
      const m = /a\/(\S+) b\/(\S+)/.exec(raw)
      current = newFile(m && m[2] ? m[2] : '文件')
      continue
    }
    if (raw.startsWith('--- ')) {
      // 无 diff --git 头时用 +++ 建档；此处忽略 ---
      continue
    }
    if (raw.startsWith('+++ ')) {
      const path = stripPrefix(raw.slice(4))
      if (!current) current = newFile(path)
      else if (current.path === '文件') current.path = path
      continue
    }
    const hunk = HUNK_RE.exec(raw)
    if (hunk) {
      current = current ?? newFile('文件')
      oldNo = Number(hunk[1])
      newNo = Number(hunk[2])
      current.lines.push({ type: 'hunk', text: raw })
      continue
    }
    if (!current) continue
    const head = raw[0]
    if (head === '+') {
      current.lines.push({ type: 'add', text: raw.slice(1), newNo })
      current.added += 1
      newNo += 1
    } else if (head === '-') {
      current.lines.push({ type: 'del', text: raw.slice(1), oldNo })
      current.removed += 1
      oldNo += 1
    } else {
      // 上下文行（含前导空格）或空行
      current.lines.push({ type: 'ctx', text: raw.startsWith(' ') ? raw.slice(1) : raw, oldNo, newNo })
      oldNo += 1
      newNo += 1
    }
  }
  return files
}
