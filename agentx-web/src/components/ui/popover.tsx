import * as React from 'react'
import * as PopoverPrimitive from '@radix-ui/react-popover'
import { cn } from '@/lib/utils'

const Popover = PopoverPrimitive.Root
const PopoverTrigger = PopoverPrimitive.Trigger
const PopoverAnchor = PopoverPrimitive.Anchor

const PopoverContent = React.forwardRef<
  React.ElementRef<typeof PopoverPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof PopoverPrimitive.Content> & {
    /** Dialog 内使用时设为 false：portal 到 body 会被 Dialog 的滚动锁
        （react-remove-scroll）拦截滚轮/触控板事件，就地渲染则不受影响 */
    portalled?: boolean
  }
>(({ className, align = 'center', sideOffset = 4, portalled = true, ...props }, ref) => {
  const content = (
    <PopoverPrimitive.Content
      ref={ref}
      align={align}
      sideOffset={sideOffset}
      className={cn(
        'ax-pop ax-pop--popover z-50 w-72 rounded-xl border border-border bg-popover p-4 text-popover-foreground shadow-md outline-none',
        className,
      )}
      {...props}
    />
  )
  return portalled ? <PopoverPrimitive.Portal>{content}</PopoverPrimitive.Portal> : content
})
PopoverContent.displayName = PopoverPrimitive.Content.displayName

export { Popover, PopoverTrigger, PopoverContent, PopoverAnchor }
