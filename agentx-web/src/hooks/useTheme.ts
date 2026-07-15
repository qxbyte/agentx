import { useSyncExternalStore } from 'react'

/**
 * 主题偏好：浅色 / 深色 / 跟随系统。
 * 生效方式是切 html.dark 类（index.css 的 --ax-* 与 theme.css 的 shadcn token 同时覆盖）；
 * 首屏防闪由 index.html 的内联脚本按同一 localStorage key 提前打类。
 */
export type ThemePref = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'ax-theme'
const media = window.matchMedia('(prefers-color-scheme: dark)')
const listeners = new Set<() => void>()

function readPref(): ThemePref {
  const v = localStorage.getItem(STORAGE_KEY)
  return v === 'light' || v === 'dark' ? v : 'system'
}

let pref: ThemePref = readPref()

function resolve(p: ThemePref): 'light' | 'dark' {
  return p === 'system' ? (media.matches ? 'dark' : 'light') : p
}

function apply(): void {
  document.documentElement.classList.toggle('dark', resolve(pref) === 'dark')
}

// 跟随系统模式下响应系统切换
media.addEventListener('change', () => {
  if (pref === 'system') {
    apply()
    listeners.forEach((l) => l())
  }
})

export function setTheme(next: ThemePref): void {
  pref = next
  localStorage.setItem(STORAGE_KEY, next)
  apply()
  listeners.forEach((l) => l())
}

export function useTheme(): ThemePref {
  return useSyncExternalStore(
    (cb) => {
      listeners.add(cb)
      return () => listeners.delete(cb)
    },
    () => pref,
  )
}
