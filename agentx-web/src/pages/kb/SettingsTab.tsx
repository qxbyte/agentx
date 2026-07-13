import { Loader2 } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { extractErrorMessage } from '../../api/http'
import * as kbApi from '../../api/kb'
import type { KnowledgeBase } from '../../types'
import KbConfigFormItems, {
  kbToFormState,
  validateKbForm,
  type KbFormErrors,
  type KbFormState,
} from './KbConfigFormItems'

interface SettingsTabProps {
  kb: KnowledgeBase
  onUpdated: (kb: KnowledgeBase) => void
}

export default function SettingsTab({ kb, onUpdated }: SettingsTabProps) {
  const [values, setValues] = useState<KbFormState>(() => kbToFormState(kb))
  const [errors, setErrors] = useState<KbFormErrors>({})
  const [saving, setSaving] = useState(false)

  const patch = (p: Partial<KbFormState>) => setValues((prev) => ({ ...prev, ...p }))

  const handleSubmit = async () => {
    const result = validateKbForm(values)
    if (!result.ok) {
      setErrors(result.errors)
      return
    }
    setSaving(true)
    try {
      const updated = await kbApi.updateKb(kb.id, result.payload)
      onUpdated(updated)
      toast.success('设置已保存')
    } catch (error) {
      toast.error(extractErrorMessage(error, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="max-w-[520px]">
      <KbConfigFormItems values={values} errors={errors} onChange={patch} />
      <div className="mt-5">
        <Button onClick={() => void handleSubmit()} disabled={saving}>
          {saving && <Loader2 className="size-4 animate-spin" />}
          保存设置
        </Button>
      </div>
    </div>
  )
}
