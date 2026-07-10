import { Routes, Route, Navigate, useNavigate } from 'react-router-dom'
import { Layout, Button } from 'antd'
import { LogoutOutlined } from '@ant-design/icons'
import { TopNav } from './Sidebar'
import { useAuthStore } from '../../store/authStore'
import { useAuth } from '../../hooks/useAuth'
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
import UnauthorizedPage from '../../pages/UnauthorizedPage'

const { Header, Content } = Layout

function OwnerRoute({ children }: { children: React.ReactNode }) {
  const { role } = useAuth()
  return role === 'OWNER' ? <>{children}</> : <Navigate to="/unauthorized" replace />
}

export default function AppLayout() {
  const clearToken = useAuthStore((s) => s.clearToken)
  const navigate = useNavigate()

  function handleLogout() {
    clearToken()
    navigate('/login', { replace: true })
  }

  return (
    <Layout style={{ minHeight: '100vh', background: '#FBF1F5' }}>
      {/* Single sticky header: logo · nav · logout */}
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
          <TopNav />
        </div>

        {/* Logout */}
        <Button
          onClick={handleLogout}
          style={{
            background: 'transparent',
            borderColor: 'rgba(234,185,207,0.5)',
            color: 'rgba(255,255,255,0.85)',
            fontFamily: '"Jost", system-ui, sans-serif',
            fontWeight: 500,
            borderRadius: 8,
            flexShrink: 0,
          }}
          icon={<LogoutOutlined />}
        >
          Logout
        </Button>
      </Header>

      {/* Page content */}
      <Content style={{ background: '#FBF1F5' }}>
        <div
          style={{
            maxWidth: 1180,
            margin: '0 auto',
            padding: '24px',
          }}
        >
          <Routes>
            <Route path="/" element={<Navigate to="/checkout" replace />} />
            <Route path="/inventory" element={<OwnerRoute><InventoryPage /></OwnerRoute>} />
            <Route path="/inventory/add" element={<OwnerRoute><AddItemPage key="add" /></OwnerRoute>} />
            <Route path="/inventory/:id/edit" element={<OwnerRoute><AddItemPage key="edit" /></OwnerRoute>} />
            <Route path="/customers" element={<CustomersPage />} />
            <Route path="/customers/register" element={<RegisterCustomerPage />} />
            <Route path="/checkout" element={<CheckoutPage />} />
            <Route path="/receipts" element={<ReceiptsPage />} />
            <Route path="/receipts/:id" element={<ReceiptDetailPage />} />
            <Route path="/receipts/:id/return" element={<ProcessReturnPage />} />
            <Route path="/invoices/:id" element={<InvoiceDetailPage />} />
            <Route path="/reports" element={<OwnerRoute><ReportsPage /></OwnerRoute>} />
            <Route path="/settings" element={<OwnerRoute><SettingsPage /></OwnerRoute>} />
            <Route path="/unauthorized" element={<UnauthorizedPage />} />
            <Route path="*" element={<Navigate to="/checkout" replace />} />
          </Routes>
        </div>
      </Content>
    </Layout>
  )
}
