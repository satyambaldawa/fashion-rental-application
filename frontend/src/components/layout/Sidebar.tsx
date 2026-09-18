import { useNavigate, useLocation } from 'react-router-dom'
import { Menu } from 'antd'
import { useAuth } from '../../hooks/useAuth'
import type { UserRole } from '../../types/auth'

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
  { key: '/coupons',   label: 'Coupons',         roles: ['OWNER'] },
  { key: '/settings',  label: 'Settings',        roles: ['OWNER'] },
  { key: '/gallery',   label: 'Gallery',         roles: ['OWNER', 'EXECUTIVE'] },
  { key: '/gallery/manage', label: 'Manage Gallery', roles: ['OWNER'] },
]

// Public (unauthenticated) nav — same visual language as the staff top-nav,
// but Login and New Rental both just point an anonymous visitor at /login.
const PUBLIC_NAV_ITEMS: { key: string; label: string; target: string }[] = [
  { key: '/gallery',   label: 'Gallery',     target: '/gallery' },
  { key: 'login',      label: 'Login',       target: '/login' },
  { key: 'new-rental', label: 'New Rental',  target: '/login' },
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
`

function injectTopNavStylesOnce() {
  if (typeof document !== 'undefined' && !document.getElementById('top-nav-styles')) {
    const style = document.createElement('style')
    style.id = 'top-nav-styles'
    style.textContent = TOP_NAV_STYLE
    document.head.appendChild(style)
  }
}

export function TopNav() {
  const navigate = useNavigate()
  const location = useLocation()
  const { role } = useAuth()

  injectTopNavStylesOnce()

  const visibleItems = NAV_ITEMS
    .filter(item => item.roles.includes(role))
    .map(({ key, label }) => ({ key, label }))

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
// when there is no logged-in user. "Login" and "New Rental" both route an
// anonymous visitor to /login; only Gallery is a real public destination.
export function PublicNav() {
  const navigate = useNavigate()
  const location = useLocation()

  injectTopNavStylesOnce()

  const selectedKey = PUBLIC_NAV_ITEMS.find(item => item.key === location.pathname)?.key

  return (
    <Menu
      className="top-nav"
      mode="horizontal"
      theme="dark"
      selectedKeys={selectedKey ? [selectedKey] : []}
      items={PUBLIC_NAV_ITEMS.map(({ key, label }) => ({ key, label }))}
      onClick={({ key }) => {
        const item = PUBLIC_NAV_ITEMS.find(i => i.key === key)
        if (item) navigate(item.target)
      }}
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
