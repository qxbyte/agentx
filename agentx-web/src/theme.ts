import type { ThemeConfig } from 'antd'

/**
 * 设计 token 单一出口。
 * 深浅模式：当前仅浅色；新增暗色时在此导出第二套 ThemeConfig，
 * 并在 index.css 的 [data-theme='dark'] 块补齐 CSS 变量。
 */
export const brand = {
  primary: '#4f46e5',
  primaryStrong: '#4338ca',
  violet: '#7c3aed',
  gradient: 'linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%)',
} as const

export const fontFamily = [
  '-apple-system',
  'BlinkMacSystemFont',
  '"Segoe UI"',
  'Roboto',
  '"Helvetica Neue"',
  'Arial',
  '"PingFang SC"',
  '"Hiragino Sans GB"',
  '"Microsoft YaHei"',
  '"Noto Sans SC"',
  'sans-serif',
].join(', ')

export const monoFontFamily = [
  'ui-monospace',
  'SFMono-Regular',
  '"SF Mono"',
  'Menlo',
  'Consolas',
  '"Liberation Mono"',
  'monospace',
].join(', ')

export const lightTheme: ThemeConfig = {
  token: {
    colorPrimary: brand.primary,
    colorInfo: brand.primary,
    colorLink: brand.primary,
    colorBgLayout: '#f7f7f8',
    colorText: '#26262e',
    colorTextSecondary: '#6f6f7a',
    colorBorder: '#dededd',
    colorBorderSecondary: '#ebebef',
    borderRadius: 10,
    fontFamily,
    fontSize: 14,
  },
  components: {
    Button: {
      borderRadius: 8,
      fontWeight: 500,
      primaryShadow: 'none',
    },
    Input: {
      borderRadius: 8,
    },
    Modal: {
      borderRadiusLG: 14,
    },
    Dropdown: {
      borderRadiusLG: 12,
    },
    Popover: {
      borderRadiusLG: 12,
    },
  },
}
