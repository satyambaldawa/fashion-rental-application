import { useNavigate, useLocation } from 'react-router-dom'
import { Grid, Menu } from 'antd'
import { useAuth } from '../../hooks/useAuth'
import type { UserRole } from '../../types/auth'

const { useBreakpoint } = Grid

interface NavItem {
  key: string
  label: string
  roles: UserRole[]
}

const NAV_ITEMS: NavItem[] = [
  { key: '/checkout',  label: 'New Rental',     roles: ['OWNER', 'EXECUTIVE'] },
  { key: '/receipts',  label: 'Active Rentals',  roles: ['OWNER', 'EXECUTIVE'] },
  { key: '/customers', label: 'Customers',       roles: ['OWNER', 'EXECUTIVE'] },
  { key: '/inventory', label: 'Inventory',       roles: ['OWNER'] },
  { key: '/reports',   label: 'Reports',         roles: ['OWNER'] },
  { key: '/gallery',   label: 'Gallery',         roles: ['OWNER', 'EXECUTIVE'] },
  { key: '/reviews',   label: 'Reviews',         roles: ['OWNER', 'EXECUTIVE'] },
  { key: '/settings',  label: 'Settings',        roles: ['OWNER'] },
]

// Public (unauthenticated) nav — same visual language as the staff top-nav.
// Login lives in the header's right-hand corner (LoginButton), not in this menu.
const PUBLIC_NAV_ITEMS: { key: string; label: string; target: string }[] = [
  { key: '/gallery',   label: 'Gallery',     target: '/gallery' },
  { key: '/reviews',   label: 'Reviews',     target: '/reviews' },
]

// Active-tab underline indicator using petal color
const TOP_NAV_STYLE = `
  .top-nav.ant-menu-horizontal {
    border-bottom: none !important;
    line-height: 64px;
  }
  .top-nav.ant-menu-horizontal > .ant-menu-item {
    color: rgba(255,255,255,0.75) !important;
    font-family: 'Jost', system-ui, sans-serif;
    font-weight: 500;
    font-size: 13px;
    letter-spacing: 0.02em;
    border-bottom: 3px solid transparent !important;
    margin-bottom: 0 !important;
    padding-bottom: 0 !important;
    padding-left: 12px !important;
    padding-right: 12px !important;
  }
  .top-nav.ant-menu-horizontal > .ant-menu-item:hover {
    color: #fff !important;
    border-bottom-color: rgba(234,185,207,0.5) !important;
    background: transparent !important;
  }
  .top-nav.ant-menu-horizontal > .ant-menu-item-selected {
    color: #fff !important;
    border-bottom-color: #EAB9CF !important;
    background: transparent !important;
  }
  .top-nav.ant-menu-horizontal::after {
    display: none !important;
  }
  .mobile-nav-pill:focus-visible {
    outline: 2px solid #EAB9CF;
    outline-offset: 2px;
  }
`

function injectTopNavStylesOnce() {
  if (typeof document !== 'undefined' && !document.getElementById('top-nav-styles')) {
    const style = document.createElement('style')
    style.id = 'top-nav-styles'
    style.textContent = TOP_NAV_STYLE
    document.head.appendChild(style)
  }
}

interface PillNavItem {
  key: string
  label: string
}

// Mobile nav: every item visible at once as a wrapping row of pills — the
// same pattern CheckoutPage already uses for its category filter chips.
// A horizontally-scrolling strip (tried first) required swiping to even
// discover items past the edge; wrapping needs no gesture to see everything.
function MobileNavPills({
  items,
  selectedKey,
  onSelect,
}: {
  items: PillNavItem[]
  selectedKey: string | undefined
  onSelect: (key: string) => void
}) {
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, padding: '10px 16px' }}>
      {items.map(item => {
        const isActive = item.key === selectedKey
        return (
          <button
            key={item.key}
            className="mobile-nav-pill"
            onClick={() => onSelect(item.key)}
            style={{
              minHeight: 44,
              padding: '6px 16px',
              borderRadius: 999,
              border: `1px solid ${isActive ? '#EAB9CF' : 'rgba(255,255,255,0.3)'}`,
              background: isActive ? '#EAB9CF' : 'transparent',
              color: isActive ? '#6E0B37' : 'rgba(255,255,255,0.85)',
              fontFamily: '"Jost", system-ui, sans-serif',
              fontWeight: 500,
              fontSize: 13,
              letterSpacing: '0.02em',
              whiteSpace: 'nowrap',
              cursor: 'pointer',
            }}
          >
            {item.label}
          </button>
        )
      })}
    </div>
  )
}

export function TopNav() {
  const navigate = useNavigate()
  const location = useLocation()
  const { role } = useAuth()
  const screens = useBreakpoint()
  const isMobile = !screens.lg

  injectTopNavStylesOnce()

  const visibleItems = NAV_ITEMS
    .filter(item => item.roles.includes(role))
    .map(({ key, label }) => ({ key, label }))

  if (isMobile) {
    return (
      <MobileNavPills
        items={visibleItems}
        selectedKey={location.pathname}
        onSelect={navigate}
      />
    )
  }

  return (
    <Menu
      className="top-nav"
      mode="horizontal"
      theme="dark"
      selectedKeys={[location.pathname]}
      items={visibleItems}
      onClick={({ key }) => navigate(key)}
      style={{
        background: '#6E0B37',
        flex: 1,
        minWidth: 0,
        overflowX: 'auto',
        overflowY: 'hidden',
      }}
    />
  )
}

// Unauthenticated equivalent of TopNav — shown on public pages (e.g. /gallery)
// when there is no logged-in user. Gallery and Reviews are the real public
// destinations; Login sits separately in the header's right corner (LoginButton).
export function PublicNav() {
  const navigate = useNavigate()
  const location = useLocation()
  const screens = useBreakpoint()
  const isMobile = !screens.lg

  injectTopNavStylesOnce()

  const selectedKey = PUBLIC_NAV_ITEMS.find(item => item.key === location.pathname)?.key

  function goTo(key: string) {
    const item = PUBLIC_NAV_ITEMS.find(i => i.key === key)
    if (item) navigate(item.target)
  }

  if (isMobile) {
    return (
      <MobileNavPills
        items={PUBLIC_NAV_ITEMS.map(({ key, label }) => ({ key, label }))}
        selectedKey={selectedKey}
        onSelect={goTo}
      />
    )
  }

  return (
    <Menu
      className="top-nav"
      mode="horizontal"
      theme="dark"
      selectedKeys={selectedKey ? [selectedKey] : []}
      items={PUBLIC_NAV_ITEMS.map(({ key, label }) => ({ key, label }))}
      onClick={({ key }) => goTo(key)}
      style={{
        background: '#6E0B37',
        flex: 1,
        minWidth: 0,
        overflowX: 'auto',
        overflowY: 'hidden',
      }}
    />
  )
}

// Keep the old Sidebar export for backward compatibility (unused after AppLayout refactor)
interface SidebarProps {
  onNavigate?: () => void
}

export function Sidebar({ onNavigate }: SidebarProps) {
  const navigate = useNavigate()
  const location = useLocation()
  const { role } = useAuth()

  const visibleItems = NAV_ITEMS
    .filter(item => item.roles.includes(role))
    .map(({ key, label }) => ({ key, label }))

  return (
    <Menu
      mode="inline"
      selectedKeys={[location.pathname]}
      items={visibleItems}
      onClick={({ key }) => {
        navigate(key)
        onNavigate?.()
      }}
      style={{ height: '100%', borderRight: 0 }}
    />
  )
}
