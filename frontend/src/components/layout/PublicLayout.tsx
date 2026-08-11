import type { ReactNode } from 'react'
import { Layout } from 'antd'
import AppHeader from './AppHeader'
import LogoutButton from './LogoutButton'
import { TopNav, PublicNav } from './Sidebar'
import { useAuthStore } from '../../store/authStore'

const { Content } = Layout

interface PublicLayoutProps {
  children: ReactNode
}

/**
 * Chrome for publicly-reachable pages (e.g. /gallery) — same header, nav rail,
 * and content wrapper as the authenticated app, but auth-aware: a logged-out
 * visitor sees the public nav and no Logout control; a logged-in staff member
 * sees the normal staff nav plus Logout, exactly as in AppLayout.
 */
export default function PublicLayout({ children }: PublicLayoutProps) {
  const token = useAuthStore((s) => s.token)

  return (
    <Layout style={{ minHeight: '100vh', background: '#FBF1F5' }}>
      <AppHeader
        nav={token ? <TopNav /> : <PublicNav />}
        right={token ? <LogoutButton /> : undefined}
      />
      <Content style={{ background: '#FBF1F5' }}>
        <div
          style={{
            maxWidth: 1180,
            margin: '0 auto',
            padding: '24px',
          }}
        >
          {children}
        </div>
      </Content>
    </Layout>
  )
}
