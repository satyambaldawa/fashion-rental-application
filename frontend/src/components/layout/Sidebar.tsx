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
  { key: '/settings',  label: 'Settings',        roles: ['OWNER'] },
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

export function TopNav() {
  const navigate = useNavigate()
  const location = useLocation()
  const { role } = useAuth()

  // Inject scoped styles once
  if (typeof document !== 'undefined' && !document.getElementById('top-nav-styles')) {
    const style = document.createElement('style')
    style.id = 'top-nav-styles'
    style.textContent = TOP_NAV_STYLE
    document.head.appendChild(style)
  }

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
