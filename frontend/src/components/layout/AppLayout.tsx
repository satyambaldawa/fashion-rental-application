import { Routes, Route, Navigate } from 'react-router-dom'
import { Layout } from 'antd'
import AppHeader from './AppHeader'
import LogoutButton from './LogoutButton'
import { TopNav } from './Sidebar'
import { useAuth } from '../../hooks/useAuth'
import SettingsPage from '../../pages/SettingsPage'
import InventoryPage from '../../pages/inventory/InventoryPage'
import AddItemPage from '../../pages/inventory/AddItemPage'
import CustomersPage from '../../pages/customers/CustomersPage'
import RegisterCustomerPage from '../../pages/customers/RegisterCustomerPage'
import EditCustomerPage from '../../pages/customers/EditCustomerPage'
import CustomerDetailPage from '../../pages/customers/CustomerDetailPage'
import CheckoutPage from '../../pages/checkout/CheckoutPage'
import ReceiptsPage from '../../pages/receipts/ReceiptsPage'
import ReceiptDetailPage from '../../pages/receipts/ReceiptDetailPage'
import ProcessReturnPage from '../../pages/receipts/ProcessReturnPage'
import InvoiceDetailPage from '../../pages/invoices/InvoiceDetailPage'
import ReportsPage from '../../pages/reports/ReportsPage'
import GalleryManagePage from '../../pages/gallery/GalleryManagePage'
import UnauthorizedPage from '../../pages/UnauthorizedPage'

const { Content } = Layout

function OwnerRoute({ children }: { children: React.ReactNode }) {
  const { role } = useAuth()
  return role === 'OWNER' ? <>{children}</> : <Navigate to="/unauthorized" replace />
}

export default function AppLayout() {
  return (
    <Layout style={{ minHeight: '100vh', background: '#FBF1F5' }}>
      <AppHeader nav={<TopNav />} right={<LogoutButton />} />

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
            <Route path="/customers/:id/edit" element={<EditCustomerPage />} />
            <Route path="/customers/:id" element={<CustomerDetailPage />} />
            <Route path="/checkout" element={<CheckoutPage />} />
            <Route path="/receipts" element={<ReceiptsPage />} />
            <Route path="/receipts/:id" element={<ReceiptDetailPage />} />
            <Route path="/receipts/:id/return" element={<ProcessReturnPage />} />
            <Route path="/invoices/:id" element={<InvoiceDetailPage />} />
            <Route path="/reports" element={<OwnerRoute><ReportsPage /></OwnerRoute>} />
            <Route path="/settings" element={<OwnerRoute><SettingsPage /></OwnerRoute>} />
            <Route path="/gallery/manage" element={<OwnerRoute><GalleryManagePage /></OwnerRoute>} />
            <Route path="/unauthorized" element={<UnauthorizedPage />} />
            <Route path="*" element={<Navigate to="/checkout" replace />} />
          </Routes>
        </div>
      </Content>
    </Layout>
  )
}
