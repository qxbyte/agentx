interface LogoProps {
  size?: number
}

/**
 * AgentX 品牌标识：轨道结环 X —— 两条 ±45° 的轨道椭圆交织成结，
 * 在四个交点处上下交替穿插（N/S 交点 A 环在上，E/W 交点 B 环在上）。
 * 独立标识无容器底，跟随 currentColor 自动适配明暗主题；favicon.svg 同源。
 */
export default function Logo({ size = 32 }: LogoProps) {
  return (
    <span className="ax-logo" style={{ width: size, height: size }} aria-hidden="true">
      <svg width={size} height={size} viewBox="0 0 32 32" fill="none" stroke="currentColor">
        <defs>
          {/* 交替穿插：N/S 交点处重绘 A 环盖在 B 环之上 */}
          <clipPath id="ax-knot-over">
            <circle cx="16" cy="9.17" r="3.4" />
            <circle cx="16" cy="22.83" r="3.4" />
          </clipPath>
        </defs>
        <g strokeWidth="2.7">
          <ellipse cx="16" cy="16" rx="13" ry="5.2" transform="rotate(45 16 16)" />
          <ellipse cx="16" cy="16" rx="13" ry="5.2" transform="rotate(-45 16 16)" />
          <g clipPath="url(#ax-knot-over)">
            <ellipse cx="16" cy="16" rx="13" ry="5.2" transform="rotate(45 16 16)" />
          </g>
        </g>
      </svg>
    </span>
  )
}
