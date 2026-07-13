import type { ThemeConfig } from 'antd'

/**
 * 设计 token 单一出口。
 * 深浅模式：当前仅浅色；新增暗色时在此导出第二套 ThemeConfig，
 * 并在 index.css 的 [data-theme='dark'] 块补齐 CSS 变量。
 */
export const brand = {
  /** 主操作黑（OpenAI 式简洁线条风） */
  primary: '#0d0d0d',
  primaryStrong: '#2d2d2d',
  /** 点缀色：仅链接 / 选中态细节使用 */
  accent: '#3b82f6',
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
    colorInfo: brand.accent,
    colorLink: brand.accent,
    colorSuccess: '#10a37f',
    colorWarning: '#b45309',
    colorError: '#c14444',
    colorBgLayout: '#ffffff',
    colorText: '#0d0d0d',
    colorTextSecondary: '#6e6e80',
    colorTextTertiary: '#8e8e93',
    colorTextQuaternary: '#b4b4b4',
    colorBorder: '#d5d5d5',
    colorBorderSecondary: '#e5e5e5',
    borderRadius: 10,
    fontFamily,
    fontSize: 14,
  },
  components: {
    Button: {
      borderRadius: 10,
      fontWeight: 500,
      primaryShadow: 'none',
      defaultShadow: 'none',
      dangerShadow: 'none',
    },
    Input: {
      borderRadius: 10,
    },
    Modal: {
      borderRadiusLG: 16,
    },
    Menu: {
      // 选中态用浅灰而非跟随黑色主色（OpenAI 式：灰阶层级，不用色块强调）
      itemSelectedBg: '#ececec',
      itemSelectedColor: '#0d0d0d',
      itemHoverBg: '#f4f4f4',
      itemActiveBg: '#ececec',
      itemBorderRadius: 10,
      itemMarginInline: 8,
      activeBarBorderWidth: 0,
    },
    Dropdown: {
      borderRadiusLG: 14,
    },
    Popover: {
      borderRadiusLG: 14,
    },
  },
}
