import { Check, Copy } from 'lucide-react'
import { isValidElement, memo, useRef, useState } from 'react'
import type { HTMLAttributes, ReactNode } from 'react'
import ReactMarkdown, { type ExtraProps } from 'react-markdown'
import rehypeHighlight from 'rehype-highlight'
import remarkGfm from 'remark-gfm'
// 代码高亮配色不走 highlight.js 自带 CSS（仅浅色）——index.css 自绘 .hljs-* 读 --ax-hl-* 随主题切换

function extractLanguage(children: ReactNode): string {
  if (isValidElement(children)) {
    const className = (children.props as { className?: string }).className ?? ''
    const match = /language-([\w+-]+)/.exec(className)
    if (match?.[1]) return match[1]
  }
  return ''
}

type PreProps = HTMLAttributes<HTMLPreElement> & ExtraProps

/** 代码块：语言标签 + 一键复制头部 */
function CodeBlock(props: PreProps) {
  const { children, node: _node, ...rest } = props
  const preRef = useRef<HTMLPreElement>(null)
  const [copied, setCopied] = useState(false)
  const language = extractLanguage(children)

  const handleCopy = async () => {
    const text = preRef.current?.innerText ?? ''
    if (!text) return
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1600)
    } catch {
      // 剪贴板不可用（非安全上下文等），静默忽略
    }
  }

  return (
    <div className="ax-codeblock">
      <div className="ax-codeblock-header">
        <span className="ax-codeblock-lang">{language || 'code'}</span>
        <button type="button" className="ax-codeblock-copy" onClick={() => void handleCopy()}>
          {copied ? <Check className="size-3.5" /> : <Copy className="size-3.5" />}
          {copied ? '已复制' : '复制'}
        </button>
      </div>
      <pre ref={preRef} {...rest}>
        {children}
      </pre>
    </div>
  )
}

interface MarkdownRendererProps {
  content: string
}

function MarkdownRenderer({ content }: MarkdownRendererProps) {
  return (
    <div className="ax-markdown">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[[rehypeHighlight, { detect: false }]]}
        components={{ pre: CodeBlock }}
      >
        {content}
      </ReactMarkdown>
    </div>
  )
}

export default memo(MarkdownRenderer)
