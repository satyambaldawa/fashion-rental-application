import type { ReactNode } from 'react'
import { Layout } from 'antd'

const { Header } = Layout

interface AppHeaderProps {
  nav: ReactNode
  right?: ReactNode
}

/**
 * The single sticky maroon header used by every screen — staff app and public
 * pages alike. Callers decide what goes in the nav slot (staff TopNav vs.
 * public nav) and whether a right-hand control (e.g. Logout) is shown.
 */
export default function AppHeader({ nav, right }: AppHeaderProps) {
  return (
    <Header
      style={{
        background: '#6E0B37',
        display: 'flex',
        alignItems: 'center',
        padding: '0 24px',
        position: 'sticky',
        top: 0,
        zIndex: 100,
        height: 64,
        boxShadow: '0 2px 8px rgba(110,11,55,0.25)',
        gap: 16,
      }}
    >
      {/* Logo + brand label */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexShrink: 0 }}>
        <img src="/logo.png" alt="Manisha's Drapery" style={{ height: 40 }} />
        <span
          style={{
            fontFamily: '"Jost", system-ui, sans-serif',
            fontWeight: 600,
            fontSize: 10,
            letterSpacing: '0.18em',
            textTransform: 'uppercase',
            color: 'rgba(255,255,255,0.6)',
            whiteSpace: 'nowrap',
          }}
        >
          Manisha's Drapery
        </span>
      </div>

      {/* Nav — fills remaining space */}
      <div style={{ flex: 1, minWidth: 0, overflow: 'hidden' }}>
        {nav}
      </div>

      {right}
    </Header>
  )
}
