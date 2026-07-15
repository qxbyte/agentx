import { Toaster as Sonner, type ToasterProps } from 'sonner'

/**
 * 顶部磨砂胶囊 toast（iPhone 消息栏观感）：半透磨砂白 + 强 blur + 分层投影 + 发丝边框，
 * 与背景拉开层次；视口居中与「从顶部弹簧弹出」动画见 index.css [data-sonner-*]。
 * 开 unstyled 完全接管样式——否则 sonner 默认的固定宽度/内边距/最小高度会撑成大方块。
 * 用法：import { toast } from 'sonner'；<Toaster /> 挂在根。
 */
function Toaster(props: ToasterProps) {
  return (
    <Sonner
      position="top-center"
      // 视觉/居中/动画统一在 index.css [data-sonner-*] 里控（含磨砂、分层阴影、弹簧入场）
      toastOptions={{
        unstyled: true,
        classNames: {
          toast:
            'ax-toast mx-auto flex w-auto max-w-[90vw] items-center gap-1.5 rounded-full py-1 pl-3 pr-3.5 text-[13px] leading-5 text-foreground',
          title: 'font-normal',
          icon: 'flex shrink-0 items-center [&>svg]:size-[15px]',
          content: 'flex items-center',
          description: 'text-muted-foreground',
          actionButton: 'ml-1 rounded-full bg-primary px-2 py-0.5 text-xs text-primary-foreground',
          cancelButton: 'ml-1 rounded-full bg-secondary px-2 py-0.5 text-xs',
        },
      }}
      {...props}
    />
  )
}

export { Toaster }
