import { Toaster as Sonner, type ToasterProps } from 'sonner'

/**
 * 顶部紧凑胶囊 toast（对齐原 antd message：白底细边、轻浮影、极小上下高度）。
 * 开 unstyled 完全接管样式——否则 sonner 默认的固定宽度/内边距/最小高度会撑成大方块。
 * 用法：import { toast } from 'sonner'；<Toaster /> 挂在根。
 */
function Toaster(props: ToasterProps) {
  return (
    <Sonner
      position="top-center"
      // 宽度修正见 index.css [data-sonner-*]：容器自动宽、toast 随内容不换行，真居中
      toastOptions={{
        unstyled: true,
        classNames: {
          toast:
            'mx-auto flex w-auto max-w-[90vw] items-center gap-1.5 rounded-full border border-[var(--ax-border-subtle)] bg-white/95 py-1 pl-3 pr-3.5 text-[13px] leading-5 text-foreground shadow-[0_2px_8px_rgba(0,0,0,0.06),0_8px_24px_rgba(0,0,0,0.06)] backdrop-blur',
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
