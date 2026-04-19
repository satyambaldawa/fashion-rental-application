import { Spin } from 'antd'

export function LoadingSpinner() {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%', padding: '48px' }}>
      <Spin size="large" />
    </div>
  )
}
