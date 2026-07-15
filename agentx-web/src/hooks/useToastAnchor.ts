import { useEffect } from 'react'
import type { RefObject } from 'react'

/**
 * 让全局 toast 在「右侧内容区（.ax-main）」水平居中。
 * <p>
 * 做法：不改 sonner 自带的视口居中（left:50% + translateX(-50%)），只写一个位移量
 * --ax-toast-shift = 内容区中心 − 视口中心，由 CSS 给 toaster 加 margin-left 平移过去。
 * 收放侧边栏时 .ax-main 宽度变化，ResizeObserver 自动更新位移，始终跟随内容区中心。
 */
export function useToastAnchor(ref: RefObject<HTMLElement | null>): void {
  useEffect(() => {
    const el = ref.current
    if (!el) return
    const update = () => {
      const r = el.getBoundingClientRect()
      const shift = Math.round(r.left + r.width / 2 - window.innerWidth / 2)
      document.documentElement.style.setProperty('--ax-toast-shift', `${shift}px`)
    }
    update()
    const ro = new ResizeObserver(update)
    ro.observe(el)
    window.addEventListener('resize', update)
    return () => {
      ro.disconnect()
      window.removeEventListener('resize', update)
      document.documentElement.style.removeProperty('--ax-toast-shift')
    }
  }, [ref])
}
