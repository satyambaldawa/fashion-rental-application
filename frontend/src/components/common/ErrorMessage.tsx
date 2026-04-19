import { Alert } from 'antd'

interface Props {
  message: string
}

export function ErrorMessage({ message }: Props) {
  return <Alert type="error" message={message} showIcon />
}
