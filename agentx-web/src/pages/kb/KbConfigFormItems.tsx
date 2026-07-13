import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import type { KbPayload } from '../../types'

/** 知识库配置表单状态（新建/编辑弹窗与详情页「设置」Tab 共用）。 */
export interface KbFormState {
  name: string
  description: string
  chunkSize: number
  chunkOverlap: number
  topK: number
  similarityThreshold: number
}

export const KB_FORM_DEFAULTS: KbFormState = {
  name: '',
  description: '',
  chunkSize: 1000,
  chunkOverlap: 200,
  topK: 5,
  similarityThreshold: 0.2,
}

export type KbFormErrors = Partial<Record<keyof KbFormState, string>>

/** 从后端实体填充表单初值。 */
export function kbToFormState(kb: {
  name: string
  description?: string | null
  chunkSize: number
  chunkOverlap: number
  topK: number
  similarityThreshold: number
}): KbFormState {
  return {
    name: kb.name,
    description: kb.description ?? '',
    chunkSize: kb.chunkSize,
    chunkOverlap: kb.chunkOverlap,
    topK: kb.topK,
    similarityThreshold: kb.similarityThreshold,
  }
}

/** 校验并归一化为提交 payload；失败时返回字段级错误。 */
export function validateKbForm(
  values: KbFormState,
): { ok: true; payload: KbPayload } | { ok: false; errors: KbFormErrors } {
  const errors: KbFormErrors = {}
  if (!values.name.trim()) errors.name = '请输入知识库名称'
  if (!Number.isFinite(values.chunkSize)) errors.chunkSize = '必填'
  if (!Number.isFinite(values.chunkOverlap)) errors.chunkOverlap = '必填'
  if (!Number.isFinite(values.topK)) errors.topK = '必填'
  if (!Number.isFinite(values.similarityThreshold)) errors.similarityThreshold = '必填'
  if (Object.keys(errors).length > 0) return { ok: false, errors }
  return {
    ok: true,
    payload: {
      name: values.name.trim(),
      description: values.description.trim() || undefined,
      chunkSize: values.chunkSize,
      chunkOverlap: values.chunkOverlap,
      topK: values.topK,
      similarityThreshold: values.similarityThreshold,
    },
  }
}

interface NumberFieldProps {
  label: string
  hint: string
  value: number
  error?: string
  min?: number
  max?: number
  step?: number
  onChange: (value: number) => void
}

function NumberField({ label, hint, value, error, min, max, step, onChange }: NumberFieldProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label>{label}</Label>
      <Input
        type="number"
        className="rounded-lg"
        value={Number.isFinite(value) ? value : ''}
        min={min}
        max={max}
        step={step}
        onChange={(e) => onChange(e.target.value === '' ? NaN : Number(e.target.value))}
      />
      <p className="text-xs text-muted-foreground">{hint}</p>
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  )
}

interface KbConfigFormItemsProps {
  values: KbFormState
  errors: KbFormErrors
  onChange: (patch: Partial<KbFormState>) => void
}

/**
 * 知识库配置表单字段（受控）。外层负责持有 state、校验（validateKbForm）与提交。
 */
export default function KbConfigFormItems({ values, errors, onChange }: KbConfigFormItemsProps) {
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="kb-name">名称</Label>
        <Input
          id="kb-name"
          placeholder="例如：产品手册"
          maxLength={60}
          value={values.name}
          onChange={(e) => onChange({ name: e.target.value })}
        />
        {errors.name && <p className="text-xs text-destructive">{errors.name}</p>}
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="kb-desc">描述</Label>
        <Textarea
          id="kb-desc"
          placeholder="这个知识库包含什么内容？（可选）"
          rows={2}
          maxLength={200}
          value={values.description}
          onChange={(e) => onChange({ description: e.target.value })}
        />
      </div>

      <div className="flex items-center gap-3 py-1 text-xs text-muted-foreground">
        <span className="h-px flex-1 bg-border" />
        分段与检索参数
        <span className="h-px flex-1 bg-border" />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <NumberField
          label="分段大小（字符）"
          hint="每个分段的目标字符数，越小检索越精准"
          value={values.chunkSize}
          error={errors.chunkSize}
          min={100}
          max={8000}
          step={100}
          onChange={(v) => onChange({ chunkSize: v })}
        />
        <NumberField
          label="分段重叠（字符）"
          hint="相邻分段的重叠字符数，避免语义被切断"
          value={values.chunkOverlap}
          error={errors.chunkOverlap}
          min={0}
          max={2000}
          step={50}
          onChange={(v) => onChange({ chunkOverlap: v })}
        />
        <NumberField
          label="召回条数 topK"
          hint="每次检索返回的分段数量"
          value={values.topK}
          error={errors.topK}
          min={1}
          max={20}
          onChange={(v) => onChange({ topK: v })}
        />
        <NumberField
          label="相似度阈值"
          hint="低于该相似度（0-1）的分段将被过滤"
          value={values.similarityThreshold}
          error={errors.similarityThreshold}
          min={0}
          max={1}
          step={0.05}
          onChange={(v) => onChange({ similarityThreshold: v })}
        />
      </div>
    </div>
  )
}
