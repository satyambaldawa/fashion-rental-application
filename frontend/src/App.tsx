import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import LoginPage from './pages/LoginPage'
import AppLayout from './components/layout/AppLayout'
import PublicReceiptPage from './pages/public/PublicReceiptPage'
import PublicInvoicePage from './pages/public/PublicInvoicePage'
import { useAuthStore } from './store/authStore'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 30_000 } }
})

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = useAuthStore((s) => s.token)
  return token ? <>{children}</> : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider
        theme={{
          token: {
            colorPrimary: '#A81259',
            colorBgBase: '#FBF1F5',
            borderRadius: 8,
            fontFamily: '"Mulish", system-ui, sans-serif',
          },
          components: {
            Layout: { siderBg: '#6E0B37', headerBg: '#6E0B37' },
            Menu: {
              darkItemBg: '#6E0B37',
              darkItemSelectedBg: '#A81259',
              darkItemColor: 'rgba(255,255,255,0.75)',
              darkItemSelectedColor: '#fff',
            },
            Card: { borderRadiusLG: 14 },
            Button: { borderRadius: 8 },
            Table: { borderRadius: 14 },
          },
        }}
      >
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/public/receipts/:shareToken" element={<PublicReceiptPage />} />
            <Route path="/public/invoices/:shareToken" element={<PublicInvoicePage />} />
            <Route path="/*" element={
              <ProtectedRoute>
                <AppLayout />
              </ProtectedRoute>
            } />
          </Routes>
        </BrowserRouter>
      </ConfigProvider>
    </QueryClientProvider>
  )
}
