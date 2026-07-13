import { App as AntdApp, Button, Form } from 'antd'
import { useState } from 'react'
import { extractErrorMessage } from '../../api/http'
import * as kbApi from '../../api/kb'
import type { KbPayload, KnowledgeBase } from '../../types'
import KbConfigFormItems from './KbConfigFormItems'

interface SettingsTabProps {
  kb: KnowledgeBase
  onUpdated: (kb: KnowledgeBase) => void
}

export default function SettingsTab({ kb, onUpdated }: SettingsTabProps) {
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<KbPayload>()
  const [saving, setSaving] = useState(false)

  const handleFinish = async (values: KbPayload) => {
    setSaving(true)
    try {
      const updated = await kbApi.updateKb(kb.id, values)
      onUpdated(updated)
      message.success('设置已保存')
    } catch (error) {
      message.error(extractErrorMessage(error, '保存失败'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Form
      form={form}
      layout="vertical"
      style={{ maxWidth: 520 }}
      initialValues={{
        name: kb.name,
        description: kb.description ?? undefined,
        chunkSize: kb.chunkSize,
        chunkOverlap: kb.chunkOverlap,
        topK: kb.topK,
        similarityThreshold: kb.similarityThreshold,
      }}
      onFinish={(values) => void handleFinish(values)}
    >
      <KbConfigFormItems />
      <Form.Item style={{ marginBottom: 0 }}>
        <Button type="primary" htmlType="submit" loading={saving}>
          保存设置
        </Button>
      </Form.Item>
    </Form>
  )
}
