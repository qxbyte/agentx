import { AlertCircle, RotateCw } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface ErrorStateProps {
  /** 友好错误说明（通常来自 extractErrorMessage） */
  message?: string
  onRetry: () => void
}

/** 数据视图加载失败的统一错误态：图标 + 说明 + 重试按钮 */
export default function ErrorState({ message, onRetry }: ErrorStateProps) {
  return (
    <div className="ax-error-state" role="alert">
      <AlertCircle className="ax-error-state-icon size-7" />
      <p className="ax-error-state-text">{message || '加载失败，请稍后重试'}</p>
      <Button variant="outline" onClick={onRetry}>
        <RotateCw className="size-4" />
        重试
      </Button>
    </div>
  )
}
