import { Result } from 'antd'

export default function UnauthorizedPage() {
  return (
    <div style={{ textAlign: 'center', paddingTop: 80 }}>
      <Result
        status="403"
        title="Access Denied"
        subTitle="You don't have permission to view this page."
      />
    </div>
  )
}
