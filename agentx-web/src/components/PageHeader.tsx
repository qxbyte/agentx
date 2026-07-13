import type { ReactNode } from 'react'

interface PageHeaderProps {
  title: string
  /** 一句话说明该页做什么，弱化显示在标题下方 */
  description?: string
  /** 右侧主操作区（如「新建」按钮） */
  extra?: ReactNode
}

/**
 * 内容页统一页头：标题 + 描述 + 主操作。
 * 管理后台各页与知识库详情等二期页面共用，保证视觉层级一致。
 */
export default function PageHeader({ title, description, extra }: PageHeaderProps) {
  return (
    <div className="ax-page-header">
      <div className="ax-page-header-text">
        <h2 className="ax-page-header-title">{title}</h2>
        {description && <p className="ax-page-header-desc">{description}</p>}
      </div>
      {extra && <div className="ax-page-header-extra">{extra}</div>}
    </div>
  )
}
