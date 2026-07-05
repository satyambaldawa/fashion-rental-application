import { useState } from 'react'
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom'
import { Layout, Button, Drawer, Grid } from 'antd'
import { LogoutOutlined, MenuOutlined } from '@ant-design/icons'
import { Sidebar } from './Sidebar'
import { useAuthStore } from '../../store/authStore'
import SettingsPage from '../../pages/SettingsPage'
import InventoryPage from '../../pages/inventory/InventoryPage'
import AddItemPage from '../../pages/inventory/AddItemPage'
import CustomersPage from '../../pages/customers/CustomersPage'
import RegisterCustomerPage from '../../pages/customers/RegisterCustomerPage'
import CheckoutPage from '../../pages/checkout/CheckoutPage'
import ReceiptsPage from '../../pages/receipts/ReceiptsPage'
import ReceiptDetailPage from '../../pages/receipts/ReceiptDetailPage'
import ProcessReturnPage from '../../pages/receipts/ProcessReturnPage'
import InvoiceDetailPage from '../../pages/invoices/InvoiceDetailPage'
import ReportsPage from '../../pages/reports/ReportsPage'

const { Sider, Content, Header } = Layout
const { useBreakpoint } = Grid

const SIDER_WIDTH = 220

export default function AppLayout() {
  const clearToken = useAuthStore((s) => s.clearToken)
  const navigate = useNavigate()
  const screens = useBreakpoint()
  const [drawerOpen, setDrawerOpen] = useState(false)

  const isMobile = !screens.lg

  function handleLogout() {
    clearToken()
    navigate('/login', { replace: true })
  }

  function handleNavClick() {
    if (isMobile) setDrawerOpen(false)
  }

  const siderContent = (
    <>
      <div style={{ padding: '16px', textAlign: 'center' }}>
        <img src="/logo.png" alt="Manisha's Drapery" style={{ width: '100%', maxWidth: 180 }} />
      </div>
      <Sidebar onNavigate={handleNavClick} />
    </>
  )

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {!isMobile && (
        <Sider width={SIDER_WIDTH} theme="light" style={{ position: 'fixed', left: 0, top: 0, bottom: 0, zIndex: 10 }}>
          {siderContent}
        </Sider>
      )}

      {isMobile && (
        <Drawer
          placement="left"
          onClose={() => setDrawerOpen(false)}
          open={drawerOpen}
          width={SIDER_WIDTH}
          styles={{ body: { padding: 0 } }}
        >
          {siderContent}
        </Drawer>
      )}

      <Layout style={{ marginLeft: isMobile ? 0 : SIDER_WIDTH }}>
        <Header style={{
          background: '#fff',
          display: 'flex',
          justifyContent: isMobile ? 'space-between' : 'flex-end',
          alignItems: 'center',
          padding: '0 16px',
          position: 'sticky',
          top: 0,
          zIndex: 9,
          borderBottom: '1px solid #f0f0f0',
        }}>
          {isMobile && (
            <Button
              type="text"
              icon={<MenuOutlined />}
              onClick={() => setDrawerOpen(true)}
              style={{ fontSize: 18 }}
            />
          )}
          <Button icon={<LogoutOutlined />} onClick={handleLogout}>
            Logout
          </Button>
        </Header>
        <Content style={{
          margin: isMobile ? '12px' : '24px',
          background: '#fff',
          borderRadius: 8,
          padding: isMobile ? '12px' : '24px',
        }}>
          <Routes>
            <Route path="/" element={<Navigate to="/checkout" replace />} />
            <Route path="/inventory" element={<InventoryPage />} />
            <Route path="/inventory/add" element={<AddItemPage />} />
            <Route path="/customers" element={<CustomersPage />} />
            <Route path="/customers/register" element={<RegisterCustomerPage />} />
            <Route path="/checkout" element={<CheckoutPage />} />
            <Route path="/receipts" element={<ReceiptsPage />} />
            <Route path="/receipts/:id" element={<ReceiptDetailPage />} />
            <Route path="/receipts/:id/return" element={<ProcessReturnPage />} />
            <Route path="/invoices/:id" element={<InvoiceDetailPage />} />
            <Route path="/reports" element={<ReportsPage />} />
            <Route path="/settings" element={<SettingsPage />} />
            <Route path="*" element={<Navigate to="/checkout" replace />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  )
}
