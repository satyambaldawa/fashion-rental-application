import { useNavigate, useLocation } from 'react-router-dom'
import { Menu } from 'antd'
import {
  AppstoreOutlined,
  TeamOutlined,
  ShoppingCartOutlined,
  FileTextOutlined,
  BarChartOutlined,
  SettingOutlined,
} from '@ant-design/icons'

const NAV_ITEMS = [
  { key: '/inventory', label: 'Inventory', icon: <AppstoreOutlined /> },
  { key: '/customers', label: 'Customers', icon: <TeamOutlined /> },
  { key: '/checkout', label: 'New Rental', icon: <ShoppingCartOutlined /> },
  { key: '/receipts', label: 'Active Rentals', icon: <FileTextOutlined /> },
  { key: '/reports', label: 'Reports', icon: <BarChartOutlined /> },
  { key: '/settings', label: 'Settings', icon: <SettingOutlined /> },
]

export function Sidebar() {
  const navigate = useNavigate()
  const location = useLocation()

  return (
    <Menu
      mode="inline"
      selectedKeys={[location.pathname]}
      items={NAV_ITEMS}
      onClick={({ key }) => navigate(key)}
      style={{ height: '100%', borderRight: 0 }}
    />
  )
}
