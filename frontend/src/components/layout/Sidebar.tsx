import { useNavigate, useLocation } from 'react-router-dom'
import { Menu } from 'antd'

const NAV_ITEMS = [
  { key: '/inventory', label: 'Inventory' },
  { key: '/customers', label: 'Customers' },
  { key: '/checkout', label: 'New Rental' },
  { key: '/receipts', label: 'Active Rentals' },
  { key: '/reports', label: 'Reports' },
  { key: '/settings', label: 'Settings' },
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

  // Inject scoped styles once
  if (typeof document !== 'undefined' && !document.getElementById('top-nav-styles')) {
    const style = document.createElement('style')
    style.id = 'top-nav-styles'
    style.textContent = TOP_NAV_STYLE
    document.head.appendChild(style)
  }

  return (
    <Menu
      className="top-nav"
      mode="horizontal"
      theme="dark"
      selectedKeys={[location.pathname]}
      items={NAV_ITEMS}
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

  return (
    <Menu
      mode="inline"
      selectedKeys={[location.pathname]}
      items={NAV_ITEMS}
      onClick={({ key }) => {
        navigate(key)
        onNavigate?.()
      }}
      style={{ height: '100%', borderRight: 0 }}
    />
  )
}
