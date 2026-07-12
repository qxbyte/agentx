import { useEffect, useState } from 'react'

const QUERY = '(max-width: 768px)'

/** 窄屏检测：侧栏抽屉化的断点 */
export function useIsMobile(): boolean {
  const [isMobile, setIsMobile] = useState(() => window.matchMedia(QUERY).matches)

  useEffect(() => {
    const mql = window.matchMedia(QUERY)
    const onChange = (event: MediaQueryListEvent) => setIsMobile(event.matches)
    mql.addEventListener('change', onChange)
    return () => mql.removeEventListener('change', onChange)
  }, [])

  return isMobile
}
