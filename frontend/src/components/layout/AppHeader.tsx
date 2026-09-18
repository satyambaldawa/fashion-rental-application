import type { ReactNode } from 'react'
import { Grid, Layout } from 'antd'

const { Header } = Layout
const { useBreakpoint } = Grid

const MOBILE_BRAND_ROW_HEIGHT = 56

interface AppHeaderProps {
  nav: ReactNode
  right?: ReactNode
}

function BrandMark() {
  return (
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
  )
}

/**
 * The single sticky maroon header used by every screen — staff app and public
 * pages alike. Callers decide what goes in the nav slot (staff TopNav vs.
 * public nav) and whether a right-hand control (e.g. Logout) is shown.
 *
 * Below the `lg` breakpoint the brand row and nav row split onto two stacked
 * rows — a handheld tablet doesn't have the width to fit logo + full nav +
 * Logout in one 64px bar. The nav row itself wraps onto as many lines as it
 * needs (see MobileNavPills) rather than confining it to a fixed height.
 */
export default function AppHeader({ nav, right }: AppHeaderProps) {
  const screens = useBreakpoint()
  const isMobile = !screens.lg

  if (isMobile) {
    return (
      <Header
        style={{
          background: '#6E0B37',
          position: 'sticky',
          top: 0,
          zIndex: 100,
          height: 'auto',
          padding: 0,
          lineHeight: 'normal',
          boxShadow: '0 2px 8px rgba(110,11,55,0.25)',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
            height: MOBILE_BRAND_ROW_HEIGHT,
            padding: '0 16px',
          }}
        >
          <BrandMark />
          {right}
        </div>
        <div style={{ borderTop: '1px solid rgba(255,255,255,0.12)' }}>
          {nav}
        </div>
      </Header>
    )
  }

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
      <BrandMark />

      {/* Nav — fills remaining space */}
      <div style={{ flex: 1, minWidth: 0, overflow: 'hidden' }}>
        {nav}
      </div>

      {right}
    </Header>
  )
}
