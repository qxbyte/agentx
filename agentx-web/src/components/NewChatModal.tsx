import { App as AntdApp, Modal, Select, Spin } from 'antd'
import { useEffect, useState } from 'react'
import * as agentsApi from '../api/agents'
import * as chatApi from '../api/chat'
import { extractErrorMessage } from '../api/http'
import * as kbApi from '../api/kb'
import type { AgentView, KnowledgeBase } from '../types'

interface NewChatModalProps {
  open: boolean
  onClose: () => void
  /** 会话创建成功后回调（调用方负责路由跳转与列表刷新） */
  onCreated: (conversationId: string) => void
}

/** 新建对话弹窗：可选绑定 Agent（单选）与知识库（多选） */
export default function NewChatModal({ open, onClose, onCreated }: NewChatModalProps) {
  const { message } = AntdApp.useApp()

  const [loading, setLoading] = useState(false)
  const [creating, setCreating] = useState(false)
  const [agents, setAgents] = useState<AgentView[]>([])
  const [kbs, setKbs] = useState<KnowledgeBase[]>([])
  const [agentId, setAgentId] = useState<string | undefined>(undefined)
  const [kbIds, setKbIds] = useState<string[]>([])

  useEffect(() => {
    if (!open) return
    setAgentId(undefined)
    setKbIds([])
    setLoading(true)
    void Promise.allSettled([agentsApi.listAgents(), kbApi.listKbs()]).then(
      ([agentsResult, kbsResult]) => {
        setAgents(
          agentsResult.status === 'fulfilled'
            ? agentsResult.value.filter((a) => a.enabled)
            : [],
        )
        setKbs(kbsResult.status === 'fulfilled' ? kbsResult.value : [])
        setLoading(false)
      },
    )
  }, [open])

  const handleOk = async () => {
    setCreating(true)
    try {
      const conversation = await chatApi.createConversation({
        ...(agentId ? { agentId } : {}),
        ...(kbIds.length > 0 ? { kbIds } : {}),
      })
      onClose()
      onCreated(conversation.id)
    } catch (error) {
      message.error(extractErrorMessage(error, '创建对话失败'))
    } finally {
      setCreating(false)
    }
  }

  return (
    <Modal
      title="新建对话"
      open={open}
      onCancel={onClose}
      onOk={() => void handleOk()}
      okText="创建"
      cancelText="取消"
      confirmLoading={creating}
      destroyOnHidden
    >
      {loading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 32 }}>
          <Spin />
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16, padding: '8px 0' }}>
          <div>
            <div className="ax-field-label">Agent（可选）</div>
            <Select
              style={{ width: '100%' }}
              placeholder="不选择则使用默认对话模式"
              allowClear
              value={agentId}
              onChange={(value: string | undefined) => setAgentId(value)}
              options={agents.map((a) => ({
                value: a.id,
                label: a.description ? `${a.name} — ${a.description}` : a.name,
              }))}
              notFoundContent="暂无可用 Agent"
            />
          </div>
          <div>
            <div className="ax-field-label">知识库（可选，支持多选）</div>
            <Select
              mode="multiple"
              style={{ width: '100%' }}
              placeholder="选择要引用的知识库"
              value={kbIds}
              onChange={(value: string[]) => setKbIds(value)}
              options={kbs.map((kb) => ({ value: kb.id, label: kb.name }))}
              notFoundContent="暂无知识库"
            />
          </div>
        </div>
      )}
    </Modal>
  )
}
