import * as React from 'react'
import * as SwitchPrimitive from '@radix-ui/react-switch'
import { cn } from '@/lib/utils'

/**
 * iOS 风格开关:全圆角轨道 + 白色圆球,位移用回弹贝塞尔(与输入框模式滑块同款),
 * 每次切换在内层球体上重放果冻挤压动画(外层负责 translate,内层负责 scale,互不覆盖)。
 */
const Switch = React.forwardRef<
  React.ElementRef<typeof SwitchPrimitive.Root>,
  React.ComponentPropsWithoutRef<typeof SwitchPrimitive.Root>
>(({ className, checked, ...props }, ref) => {
  const ballRef = React.useRef<HTMLSpanElement>(null)
  const mountedRef = React.useRef(false)

  React.useEffect(() => {
    // 首次挂载只定位不弹跳;之后每次外部 checked 变化重放果冻动画
    if (!mountedRef.current) {
      mountedRef.current = true
      return
    }
    const el = ballRef.current
    if (!el) return
    el.classList.remove('ax-jelly')
    void el.offsetWidth
    el.classList.add('ax-jelly')
  }, [checked])

  return (
    <SwitchPrimitive.Root
      ref={ref}
      checked={checked}
      className={cn(
        'peer inline-flex h-[22px] w-[38px] shrink-0 cursor-pointer items-center rounded-full p-[2px] transition-colors duration-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-50 data-[state=checked]:bg-[var(--switch-on)] data-[state=unchecked]:bg-input',
        className,
      )}
      {...props}
    >
      <SwitchPrimitive.Thumb className="ax-switch-thumb pointer-events-none block size-[18px] data-[state=checked]:translate-x-4 data-[state=unchecked]:translate-x-0">
        <span
          ref={ballRef}
          className="block size-full rounded-full bg-background shadow-[0_1px_3px_rgba(0,0,0,0.25),0_0_0_0.5px_rgba(0,0,0,0.06)]"
        />
      </SwitchPrimitive.Thumb>
    </SwitchPrimitive.Root>
  )
})
Switch.displayName = SwitchPrimitive.Root.displayName

export { Switch }
