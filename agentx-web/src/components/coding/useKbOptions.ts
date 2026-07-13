import { useEffect, useState } from 'react'
import { listEnabledExternalKbs } from '../../api/externalKb'
import * as kbApi from '../../api/kb'

export interface KbOption {
  id: string
  name: string
  external: boolean
}

/** 知识库选项统一数据源：本地库 + 启用中的外部库（项目表单/输入框选择器共用）。 */
export function useKbOptions(enabled = true): KbOption[] {
  const [options, setOptions] = useState<KbOption[]>([])

  useEffect(() => {
    if (!enabled) return
    void Promise.allSettled([kbApi.listKbs(), listEnabledExternalKbs()]).then(([locals, exts]) => {
      const opts: KbOption[] = []
      if (locals.status === 'fulfilled') {
        opts.push(...locals.value.map((k) => ({ id: k.id, name: k.name, external: false })))
      }
      if (exts.status === 'fulfilled') {
        opts.push(...exts.value.map((k) => ({ id: k.id, name: k.name, external: true })))
      }
      setOptions(opts)
    })
  }, [enabled])

  return options
}
