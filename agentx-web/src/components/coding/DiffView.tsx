import { useState } from 'react'
import { cn } from '@/lib/utils'
import { parseUnifiedDiff, type DiffFile } from '@/lib/parseDiff'

/** unified diff 渲染（对标 Codex 桌面版）：多文件分标签、逐行绿/红/灰、双列行号、+N/-M 统计。 */
export default function DiffView({ diff }: { diff: string }) {
  const files = parseUnifiedDiff(diff)
  const [active, setActive] = useState(0)

  if (files.length === 0) {
    return <pre className="ax-toolcall-pre ax-scroll">{diff}</pre>
  }

  const file = files[Math.min(active, files.length - 1)] as DiffFile

  return (
    <div className="overflow-hidden rounded-xl border border-border">
      {/* 多文件标签页 */}
      {files.length > 1 && (
        <div className="flex flex-wrap gap-1 border-b border-border bg-muted/50 px-2 py-1.5">
          {files.map((f, i) => (
            <button
              key={f.path + i}
              type="button"
              onClick={() => setActive(i)}
              className={cn(
                'max-w-[220px] truncate rounded-md px-2 py-0.5 font-mono text-xs transition-colors',
                i === active ? 'bg-background text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground',
              )}
            >
              {f.path}
            </button>
          ))}
        </div>
      )}

      {/* 文件头：路径 + 统计徽标 */}
      <div className="flex items-center gap-2 border-b border-border bg-muted/30 px-3 py-1.5">
        <span className="truncate font-mono text-xs text-foreground">{file.path}</span>
        <span className="ml-auto flex shrink-0 items-center gap-2 text-xs">
          <span className="text-[var(--ax-ok-text)]">+{file.added}</span>
          <span className="text-destructive">-{file.removed}</span>
        </span>
      </div>

      {/* 行 */}
      <div className="ax-scroll overflow-x-auto">
        <table className="w-full border-collapse font-mono text-xs leading-relaxed">
          <tbody>
            {file.lines.map((line, i) => {
              if (line.type === 'hunk') {
                return (
                  <tr key={i} className="bg-[var(--ax-info-bg)] text-[var(--ax-info-text)]">
                    <td className="select-none px-2 text-right opacity-60" colSpan={2} />
                    <td className="px-2 py-0.5 whitespace-pre">{line.text}</td>
                  </tr>
                )
              }
              const bg =
                line.type === 'add' ? 'bg-[var(--ax-diff-add)]' : line.type === 'del' ? 'bg-[var(--ax-diff-del)]' : ''
              const sign = line.type === 'add' ? '+' : line.type === 'del' ? '-' : ' '
              return (
                <tr key={i} className={bg}>
                  <td className="w-10 select-none border-r border-border/60 px-2 text-right text-muted-foreground">
                    {line.oldNo ?? ''}
                  </td>
                  <td className="w-10 select-none border-r border-border/60 px-2 text-right text-muted-foreground">
                    {line.newNo ?? ''}
                  </td>
                  <td className="whitespace-pre px-2 py-0.5">
                    <span className="select-none text-muted-foreground">{sign} </span>
                    {line.text}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
