import { Routes, Route, Navigate, useNavigate } from 'react-router-dom'
import { Layout, Button } from 'antd'
import { LogoutOutlined } from '@ant-design/icons'
import { Sidebar } from './Sidebar'
import { useAuthStore } from '../../store/authStore'

const { Sider, Content, Header } = Layout

export default function AppLayout() {
  const clearToken = useAuthStore((s) => s.clearToken)
  const navigate = useNavigate()

  function handleLogout() {
    clearToken()
    navigate('/login', { replace: true })
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider width={220} theme="light">
        <div style={{ padding: '16px', fontWeight: 700, fontSize: 16 }}>Fashion Rental</div>
        <Sidebar />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', display: 'flex', justifyContent: 'flex-end', alignItems: 'center', padding: '0 24px' }}>
          <Button icon={<LogoutOutlined />} onClick={handleLogout}>
            Logout
          </Button>
        </Header>
        <Content style={{ margin: '24px', background: '#fff', borderRadius: 8, padding: '24px' }}>
          <Routes>
            <Route path="/" element={<Navigate to="/inventory" replace />} />
            <Route path="/inventory" element={<div>Inventory (coming soon)</div>} />
            <Route path="/customers" element={<div>Customers (coming soon)</div>} />
            <Route path="/checkout" element={<div>New Rental (coming soon)</div>} />
            <Route path="/receipts" element={<div>Active Rentals (coming soon)</div>} />
            <Route path="/reports" element={<div>Reports (coming soon)</div>} />
            <Route path="/settings" element={<div>Settings (coming soon)</div>} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  )
}
