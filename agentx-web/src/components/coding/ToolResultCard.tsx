import { CheckCircle2, ChevronDown, ChevronRight, Loader2 } from 'lucide-react'
import { useState } from 'react'
import type { ToolCallInfo } from '../../types'
import DiffView from './DiffView'
import ShellOutput from './ShellOutput'

/** 从 args（对象或 JSON 字符串）安全取字符串字段 */
function argStr(args: unknown, key: string): string | undefined {
  let obj: Record<string, unknown> | undefined
  if (typeof args === 'string') {
    try {
      obj = JSON.parse(args) as Record<string, unknown>
    } catch {
      return undefined
    }
  } else if (args && typeof args === 'object') {
    obj = args as Record<string, unknown>
  }
  const v = obj?.[key]
  return typeof v === 'string' ? v : undefined
}

function resultStr(result: unknown): string {
  if (result == null) return ''
  return typeof result === 'string' ? result : JSON.stringify(result, null, 2)
}

const KIND_LABEL: Record<string, string> = {
  patch: '应用补丁',
  shell: '执行命令',
  write: '写入文件',
  commit: '提交变更',
  read: '读取文件',
  grep: '检索代码',
  find: '查找文件',
  list: '列出目录',
  git: 'Git',
}

/** 编码工具调用卡：按 kind 分发到 DiffView / ShellOutput / 代码块。默认折叠。 */
export default function ToolResultCard({ call }: { call: ToolCallInfo }) {
  const [open, setOpen] = useState(call.kind === 'patch' || call.kind === 'shell')
  const finished = call.done === true || call.result !== undefined
  const kind = call.kind ?? 'generic'

  return (
    <div className="ax-toolcall">
      <button type="button" className="ax-toolcall-header" onClick={() => setOpen((v) => !v)} aria-expanded={open}>
        <span className="ax-toolcall-name">{KIND_LABEL[kind] ?? call.name}</span>
        <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-[11px] text-muted-foreground">
          {call.name}
        </code>
        <span className="ax-toolcall-status">
          {finished ? (
            <>
              <CheckCircle2 className="size-3.5 text-[var(--ax-success)]" /> 已完成
            </>
          ) : (
            <>
              <Loader2 className="size-3.5 animate-spin" /> 运行中
            </>
          )}
          {open ? <ChevronDown className="size-3.5" /> : <ChevronRight className="size-3.5" />}
        </span>
      </button>
      {open && (
        <div className="ax-toolcall-body">
          <ToolCallBody call={call} kind={kind} />
        </div>
      )}
    </div>
  )
}

/** 工具调用详情体（按 kind 分发 diff/终端/文本）——大卡与集合紧凑行共用。 */
export function ToolCallBody({ call, kind }: { call: ToolCallInfo; kind: string }) {
  const preview = call.preview
  switch (kind) {
    case 'patch': {
      const diff = preview?.diff ?? argStr(call.args, 'unifiedDiff') ?? resultStr(call.result)
      return <DiffView diff={diff} />
    }
    case 'shell': {
      const command = preview?.command ?? argStr(call.args, 'command')
      return <ShellOutput command={command} output={resultStr(call.result)} />
    }
    default: {
      const path = preview?.path ?? argStr(call.args, 'path')
      const out = resultStr(call.result)
      return (
        <>
          {path && <div className="ax-toolcall-label">{path}</div>}
          {out ? (
            <pre className="ax-toolcall-pre ax-scroll">{out}</pre>
          ) : (
            <div className="ax-toolcall-label">运行中…</div>
          )}
        </>
      )
    }
  }
}
