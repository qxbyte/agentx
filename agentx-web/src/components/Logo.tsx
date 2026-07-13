interface LogoProps {
  size?: number
}

/** AgentX 品牌标识：单色黑圆角方块 + 四芒星火花（极简线条风） */
export default function Logo({ size = 32 }: LogoProps) {
  return (
    <span className="ax-logo" style={{ width: size, height: size }} aria-hidden="true">
      <svg
        width={Math.round(size * 0.62)}
        height={Math.round(size * 0.62)}
        viewBox="0 0 24 24"
        fill="currentColor"
      >
        <path d="M12 1.6l2.5 6.4 6.4 2.5-6.4 2.5L12 19.4 9.5 13 3.1 10.5 9.5 8 12 1.6z" />
        <circle cx="19" cy="18.6" r="2.1" />
      </svg>
    </span>
  )
}
