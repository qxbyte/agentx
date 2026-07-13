import { ExclamationCircleOutlined, ReloadOutlined } from '@ant-design/icons'
import { Button } from 'antd'

interface ErrorStateProps {
  /** 友好错误说明（通常来自 extractErrorMessage） */
  message?: string
  onRetry: () => void
}

/** 数据视图加载失败的统一错误态：图标 + 说明 + 重试按钮 */
export default function ErrorState({ message, onRetry }: ErrorStateProps) {
  return (
    <div className="ax-error-state" role="alert">
      <ExclamationCircleOutlined className="ax-error-state-icon" />
      <p className="ax-error-state-text">{message || '加载失败，请稍后重试'}</p>
      <Button icon={<ReloadOutlined />} onClick={onRetry}>
        重试
      </Button>
    </div>
  )
}
