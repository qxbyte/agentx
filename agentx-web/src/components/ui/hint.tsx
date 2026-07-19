import type { ReactElement, ReactNode } from 'react'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'

interface HintProps {
  /** 空值时不挂提示，原样渲染子元素（条件提示场景免三元包裹） */
  text?: ReactNode
  side?: 'top' | 'bottom' | 'left' | 'right'
  children: ReactElement
}

/**
 * 控件悬浮提示的统一入口：替代零散的原生 title=（浏览器灰框与系统 UI 不搭），
 * 统一为深色圆角气泡（样式集中在 TooltipContent 一处维护）。
 * 子元素需可接收 ref（原生 button/span 即可）。
 * 注意：不要包在 DropdownMenu/Popover 的触发器上——弹层关闭回焦会误弹提示，
 * 这类场景把说明放进弹层内部。
 */
export default function Hint({ text, side = 'top', children }: HintProps) {
  if (!text) return children
  return (
    <Tooltip>
      <TooltipTrigger asChild>{children}</TooltipTrigger>
      <TooltipContent side={side} className="max-w-[280px]">
        {text}
      </TooltipContent>
    </Tooltip>
  )
}
