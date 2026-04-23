import { Routes, Route, Navigate, useNavigate } from 'react-router-dom'
import { Layout, Button } from 'antd'
import { LogoutOutlined } from '@ant-design/icons'
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
          </Routes>
        </Content>
      </Layout>
    </Layout>
  )
}
