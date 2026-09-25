import { Button } from 'antd'
import { LoginOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'

// Right-hand-corner equivalent of LogoutButton, shown to logged-out visitors
// on public pages instead of Login being just another item in the nav menu.
export default function LoginButton() {
  const navigate = useNavigate()

  return (
    <Button
      onClick={() => navigate('/login')}
      style={{
        background: 'transparent',
        borderColor: 'rgba(234,185,207,0.5)',
        color: 'rgba(255,255,255,0.85)',
        fontFamily: '"Jost", system-ui, sans-serif',
        fontWeight: 500,
        borderRadius: 8,
        flexShrink: 0,
      }}
      icon={<LoginOutlined />}
    >
      Login
    </Button>
  )
}
