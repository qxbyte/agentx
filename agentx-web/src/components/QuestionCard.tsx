import { Check, CircleHelp } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { cn } from '@/lib/utils'
import { extractErrorMessage } from '../api/http'
import { useChatStore } from '../stores/chat'
import type { QuestionAnswer, QuestionItem } from '../types'

interface QuestionCardProps {
  item: QuestionItem
}

/** 单问的作答草稿 */
interface Draft {
  selected: string[]
  otherText: string
  skipped: boolean
}

/**
 * askUserQuestion 提问卡（对标 Claude Code AskUserQuestion UI）：
 * 单选/多选选项 + Other 自由输入，多问分步（Back/Skip/Next/Submit），
 * 提交后由 question-result 权威帧翻转终态。
 */
export default function QuestionCard({ item }: QuestionCardProps) {
  const resolveQuestion = useChatStore((s) => s.resolveQuestion)
  const [step, setStep] = useState(0)
  const [drafts, setDrafts] = useState<Draft[]>(
    item.questions.map(() => ({ selected: [], otherText: '', skipped: false })),
  )
  const [submitting, setSubmitting] = useState(false)

  const total = item.questions.length
  const spec = item.questions[Math.min(step, total - 1)]
  const draft = drafts[Math.min(step, total - 1)]

  /* ---------- 终态展示 ---------- */
  if (item.status !== 'pending') {
    return (
      <div className="ax-question-card rounded-2xl border border-[var(--ax-border)] bg-[var(--ax-surface)] p-4">
        <div className="flex items-center gap-2 text-sm text-[var(--ax-text-secondary)]">
          <CircleHelp className="size-4 shrink-0" />
          {item.status === 'expired' ? (
            <span>提问已过期（未作答）</span>
          ) : (
            <span className="font-medium text-foreground">已作答</span>
          )}
        </div>
        {item.status === 'answered' && (
          <div className="mt-2 flex flex-col gap-1.5">
            {item.questions.map((q, i) => {
              const a = item.answers?.[i]
              const chosen = a?.skipped
                ? '（已跳过）'
                : [...(a?.selected ?? []), ...(a?.otherText ? [a.otherText] : [])].join('、') ||
                  '（未选择）'
              return (
                <div key={i} className="text-[13px]">
                  <span className="text-[var(--ax-text-secondary)]">{q.header || q.question}</span>
                  <span className="mx-1.5 text-[var(--ax-text-faint)]">→</span>
                  <span className="font-medium text-foreground">{chosen}</span>
                </div>
              )
            })}
          </div>
        )}
      </div>
    )
  }

  if (!spec || !draft) return null

  /* ---------- 作答交互 ---------- */
  const patchDraft = (p: Partial<Draft>) => {
    setDrafts((prev) => prev.map((d, i) => (i === step ? { ...d, ...p, skipped: false } : d)))
  }

  const toggleOption = (label: string) => {
    if (spec.multiSelect) {
      patchDraft({
        selected: draft.selected.includes(label)
          ? draft.selected.filter((l) => l !== label)
          : [...draft.selected, label],
      })
    } else {
      // 单选:点击仅选中(高亮),提交由显式按钮触发——避免误触直接提交
      patchDraft({ selected: [label], otherText: '' })
    }
  }

  const hasAnswer = draft.selected.length > 0 || draft.otherText.trim() !== ''
  const isLast = step === total - 1

  /* 预览型选择器(对标 Claude Code):单选且任一选项带 preview 时,
     切换为「左选项列表 + 右预览面板」并排布局,预览随选中项切换 */
  const hasPreview = !spec.multiSelect && spec.options.some((o) => o.preview)
  const previewText = hasPreview
    ? (spec.options.find((o) => draft.selected.includes(o.label))?.preview ??
      spec.options.find((o) => o.preview)?.preview ??
      '')
    : ''

  const buildAnswers = (all: Draft[]): QuestionAnswer[] =>
    item.questions.map((q, i) => {
      const d = all[i]!
      const other = d.otherText.trim()
      if (d.skipped || (d.selected.length === 0 && other === '')) {
        return { question: q.question, selected: [], skipped: true }
      }
      return {
        question: q.question,
        selected: d.selected,
        ...(other !== '' ? { otherText: other } : {}),
      }
    })

  const submit = async (all: Draft[]) => {
    setSubmitting(true)
    try {
      await resolveQuestion(item.questionId, buildAnswers(all))
    } catch (error) {
      // 404 = 提问已随流终止被取消(卡片已翻过期);其余错误回滚 pending 可重试
      toast.error(extractErrorMessage(error, '提交回答失败，请重试'))
    } finally {
      setSubmitting(false)
    }
  }

  const advance = (skipCurrent: boolean) => {
    const next = drafts.map((d, i) => (i === step && skipCurrent ? { ...d, skipped: true } : d))
    setDrafts(next)
    if (isLast) {
      void submit(next)
    } else {
      setStep(step + 1)
    }
  }

  return (
    <div className="ax-question-card rounded-2xl border border-[var(--ax-border)] bg-[var(--ax-surface)] p-4 shadow-sm">
      {/* 头部：进度 chip + 问题 */}
      <div className="mb-3 flex items-start gap-2">
        {total > 1 && (
          <span className="mt-0.5 shrink-0 rounded-md bg-[var(--ax-warn-bg,#f5edd8)] px-1.5 py-0.5 text-xs font-semibold text-[var(--ax-warn-text,#8a6d1d)]">
            {step + 1}/{total}
          </span>
        )}
        {spec.header && total === 1 && (
          <span className="mt-0.5 shrink-0 rounded-md bg-[var(--ax-warn-bg,#f5edd8)] px-1.5 py-0.5 text-xs font-semibold text-[var(--ax-warn-text,#8a6d1d)]">
            {spec.header}
          </span>
        )}
        <span className="text-[15px] font-semibold text-foreground">{spec.question}</span>
      </div>

      {/* 选项（预览型选择器时左右并排） */}
      <div className={cn(hasPreview && 'flex items-start gap-3')}>
      <div className={cn('flex flex-col gap-2', hasPreview && 'min-w-0 flex-[1.1]')}>
        {spec.options.map((opt, i) => {
          const checked = draft.selected.includes(opt.label)
          return (
            <button
              key={opt.label}
              type="button"
              onClick={() => toggleOption(opt.label)}
              className={cn(
                'flex w-full items-center gap-3 rounded-xl px-4 py-2.5 text-left transition-colors',
                checked
                  ? 'bg-[var(--ax-hover)] ring-1 ring-[var(--ax-border-strong)]'
                  : 'bg-[var(--ax-chip-bg,var(--ax-hover-weak))] hover:bg-[var(--ax-hover)]',
              )}
            >
              {/* 序号在前;选中态在尾部画一个裸勾(无框无底),单选时勾随新选择移动 */}
              {!spec.multiSelect && (
                <span className="w-4 shrink-0 text-sm text-[var(--ax-text-faint)]">{i + 1}</span>
              )}
              <span className="min-w-0 flex-1">
                <span className="block text-[14px] font-medium text-foreground">{opt.label}</span>
                {opt.description && (
                  <span className="mt-0.5 block text-[13px] text-[var(--ax-text-secondary)]">
                    {opt.description}
                  </span>
                )}
              </span>
              <Check
                strokeWidth={2.5}
                className={cn(
                  'size-4 shrink-0 text-foreground transition-opacity',
                  checked ? 'opacity-100' : 'opacity-0',
                )}
              />
            </button>
          )
        })}

        {/* Other 自由输入 */}
        <div
          className={cn(
            'rounded-xl px-4 py-2.5 transition-colors',
            draft.otherText.trim() !== ''
              ? 'bg-[var(--ax-hover)] ring-1 ring-[var(--ax-border-strong)]'
              : 'bg-[var(--ax-chip-bg,var(--ax-hover-weak))]',
          )}
        >
          <span className="block text-[14px] font-medium text-foreground">其他</span>
          <input
            type="text"
            value={draft.otherText}
            onChange={(e) => {
              const value = e.target.value
              setDrafts((prev) =>
                prev.map((d, i) =>
                  i === step
                    ? {
                        ...d,
                        otherText: value,
                        // 单选下自由输入与选项互斥
                        selected: spec.multiSelect ? d.selected : [],
                        skipped: false,
                      }
                    : d,
                ),
              )
            }}
            placeholder="输入你的回答…"
            className="mt-1.5 w-full rounded-lg border border-[var(--ax-border)] bg-[var(--ax-surface)] px-3 py-1.5 text-[13px] text-foreground outline-none placeholder:text-[var(--ax-text-faint)] focus:border-[var(--ax-border-strong)]"
          />
        </div>
      </div>
      {/* 右侧预览面板：展示选中项（未选中则首个带预览项）的多行预览内容 */}
      {hasPreview && (
        <pre className="ax-scroll max-h-[280px] min-w-0 flex-1 overflow-auto whitespace-pre-wrap rounded-xl border border-[var(--ax-border)] bg-[var(--ax-chip-bg,var(--ax-hover-weak))] p-3 font-mono text-[12px] leading-relaxed text-[var(--ax-text-secondary)]">
          {previewText}
        </pre>
      )}
      </div>

      {/* 底部操作 */}
      <div className="mt-3 flex items-center gap-2">
        {step > 0 && (
          <button
            type="button"
            className="rounded-full border border-[var(--ax-border)] px-3.5 py-1.5 text-[13px] text-foreground transition-colors hover:bg-[var(--ax-hover)]"
            onClick={() => setStep(step - 1)}
          >
            上一题
          </button>
        )}
        <div className="ml-auto flex items-center gap-2">
          <button
            type="button"
            className="rounded-full border border-[var(--ax-border)] px-3.5 py-1.5 text-[13px] text-[var(--ax-text-secondary)] transition-colors hover:bg-[var(--ax-hover)]"
            disabled={submitting}
            onClick={() => advance(true)}
          >
            跳过
          </button>
          <button
            type="button"
            className="rounded-full bg-primary px-4 py-1.5 text-[13px] font-medium text-primary-foreground transition-opacity disabled:opacity-40"
            disabled={!hasAnswer || submitting}
            onClick={() => advance(false)}
          >
            {isLast ? '提交' : '下一题'}
          </button>
        </div>
      </div>
    </div>
  )
}
