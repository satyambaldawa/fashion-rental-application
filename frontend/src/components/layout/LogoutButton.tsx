import { Button } from 'antd'
import { LogoutOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '../../store/authStore'

export default function LogoutButton() {
  const clearToken = useAuthStore((s) => s.clearToken)
  const navigate = useNavigate()

  function handleLogout() {
    clearToken()
    navigate('/login', { replace: true })
  }

  return (
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
  )
}
