import * as React from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'

const badgeVariants = cva(
  'inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium transition-colors',
  {
    variants: {
      variant: {
        default: 'border-transparent bg-secondary text-secondary-foreground',
        outline: 'border-border text-muted-foreground',
        success: 'border-transparent bg-[var(--ax-ok-bg)] text-[var(--ax-ok-text)]',
        warning: 'border-transparent bg-[var(--ax-warn-bg)] text-warning',
        destructive: 'border-transparent bg-[var(--ax-danger-bg)] text-destructive',
        info: 'border-transparent bg-[var(--ax-info-bg)] text-[var(--ax-info-text)]',
        violet: 'border-transparent bg-[var(--ax-violet-bg)] text-[var(--ax-violet-text)]',
      },
    },
    defaultVariants: {
      variant: 'default',
    },
  },
)

export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badgeVariants> {}

/** forwardRef：作为 TooltipTrigger/PopoverTrigger 的 asChild 子元素时必须可接收 ref，
    否则提示静默失效（无报错、纯不显示） */
const Badge = React.forwardRef<HTMLSpanElement, BadgeProps>(
  ({ className, variant, ...props }, ref) => (
    <span ref={ref} className={cn(badgeVariants({ variant }), className)} {...props} />
  ),
)
Badge.displayName = 'Badge'

export { Badge, badgeVariants }
