import { Divider, Form, Input, InputNumber } from 'antd'

/**
 * 知识库配置表单字段（新建/编辑弹窗与详情页「设置」Tab 共用）。
 * 外层负责提供 <Form> 与 initialValues / 提交按钮。
 */
export default function KbConfigFormItems() {
  return (
    <>
      <Form.Item
        name="name"
        label="名称"
        rules={[{ required: true, message: '请输入知识库名称' }]}
      >
        <Input placeholder="例如：产品手册" maxLength={60} />
      </Form.Item>
      <Form.Item name="description" label="描述">
        <Input.TextArea
          placeholder="这个知识库包含什么内容？（可选）"
          rows={2}
          maxLength={200}
        />
      </Form.Item>
      <Divider plain style={{ margin: '4px 0 16px', fontSize: 13, color: 'var(--ax-text-secondary)' }}>
        分段与检索参数
      </Divider>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', columnGap: 16 }}>
        <Form.Item
          name="chunkSize"
          label="分段大小（字符）"
          extra="每个分段的目标字符数，越小检索越精准"
          rules={[{ required: true, message: '必填' }]}
        >
          <InputNumber min={100} max={8000} step={100} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="chunkOverlap"
          label="分段重叠（字符）"
          extra="相邻分段的重叠字符数，避免语义被切断"
          rules={[{ required: true, message: '必填' }]}
        >
          <InputNumber min={0} max={2000} step={50} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="topK"
          label="召回条数 topK"
          extra="每次检索返回的分段数量"
          rules={[{ required: true, message: '必填' }]}
        >
          <InputNumber min={1} max={20} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="similarityThreshold"
          label="相似度阈值"
          extra="低于该相似度（0-1）的分段将被过滤"
          rules={[{ required: true, message: '必填' }]}
        >
          <InputNumber min={0} max={1} step={0.05} style={{ width: '100%' }} />
        </Form.Item>
      </div>
    </>
  )
}
