interface ShellOutputProps {
  command?: string
  /** 工具返回的原始输出（可能含 exitCode/截断提示，纯文本） */
  output?: string
}

/** 终端风格输出块：命令 + 深色等宽输出区。 */
export default function ShellOutput({ command, output }: ShellOutputProps) {
  return (
    <div className="overflow-hidden rounded-xl border border-[#2b2b30]">
      {command && (
        <div className="flex items-center gap-2 bg-[#2b2b30] px-3 py-1.5 font-mono text-xs text-[#d4d4d8]">
          <span className="select-none text-[#7dd3a8]">$</span>
          <span className="truncate">{command}</span>
        </div>
      )}
      {output != null && output !== '' && (
        <pre className="ax-scroll m-0 max-h-[320px] overflow-auto bg-[#1e1e20] px-3 py-2.5 font-mono text-xs leading-relaxed text-[#d4d4d8]">
          {output}
        </pre>
      )}
    </div>
  )
}
