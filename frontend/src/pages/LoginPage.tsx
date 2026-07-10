import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Form, Input, Button, Card, message } from 'antd'
import { UserOutlined, LockOutlined } from '@ant-design/icons'
import { login } from '../api/auth'
import { useAuthStore } from '../store/authStore'
import type { LoginRequest } from '../types/auth'

export default function LoginPage() {
  const [loading, setLoading] = useState(false)
  const setAuth = useAuthStore((s) => s.setAuth)
  const navigate = useNavigate()

  async function handleSubmit(values: LoginRequest) {
    setLoading(true)
    try {
      const { token, role } = await login(values)
      setAuth(token, role)
      navigate('/checkout', { replace: true })
    } catch {
      message.error('Invalid credentials')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      minHeight: '100vh',
      background: '#FBF1F5',
      padding: '16px',
    }}>
      <div style={{ width: '100%', maxWidth: 400 }}>
        {/* Brand header above the card */}
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <img src="/logo.png" alt="Manisha's Drapery" style={{ height: 80, marginBottom: 12 }} />
          <h1 style={{
            fontFamily: '"Cormorant Garamond", Georgia, serif',
            fontWeight: 500,
            fontSize: 32,
            color: '#33101F',
            margin: 0,
            lineHeight: 1.1,
          }}>
            Welcome{' '}
            <em style={{ color: '#A81259', fontStyle: 'italic' }}>Back</em>
          </h1>
          <p style={{
            fontFamily: '"Jost", system-ui, sans-serif',
            fontSize: 12,
            fontWeight: 600,
            letterSpacing: '0.16em',
            textTransform: 'uppercase',
            color: '#7a5361',
            margin: '8px 0 0',
          }}>
            Manisha's Drapery
          </p>
        </div>

        <Card
          style={{
            border: '1px solid #eed6e0',
            borderRadius: 14,
            boxShadow: '0 10px 30px -18px rgba(110,11,55,0.4)',
            background: '#fff',
          }}
          styles={{ body: { padding: '28px 32px' } }}
        >
          <Form layout="vertical" onFinish={handleSubmit} autoComplete="off">
            <Form.Item
              name="username"
              rules={[{ required: true, message: 'Please enter your username' }]}
            >
              <Input
                prefix={<UserOutlined style={{ color: '#7a5361' }} />}
                placeholder="Username"
                size="large"
                style={{ borderColor: '#eed6e0', borderRadius: 8 }}
              />
            </Form.Item>
            <Form.Item
              name="password"
              rules={[{ required: true, message: 'Please enter your password' }]}
            >
              <Input.Password
                prefix={<LockOutlined style={{ color: '#7a5361' }} />}
                placeholder="Password"
                size="large"
                style={{ borderColor: '#eed6e0', borderRadius: 8 }}
              />
            </Form.Item>
            <Form.Item style={{ marginBottom: 0 }}>
              <Button
                type="primary"
                htmlType="submit"
                loading={loading}
                block
                size="large"
                style={{
                  background: '#A81259',
                  borderColor: '#A81259',
                  borderRadius: 8,
                  fontFamily: '"Jost", system-ui, sans-serif',
                  fontWeight: 600,
                  letterSpacing: '0.04em',
                  height: 46,
                }}
              >
                Sign In
              </Button>
            </Form.Item>
          </Form>
        </Card>
      </div>
    </div>
  )
}
