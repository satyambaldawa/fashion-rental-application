import { Button, ConfigProvider } from 'antd'
import type { ThemeConfig } from 'antd'
import { RightOutlined } from '@ant-design/icons'

const VIEW_BUTTON_THEME: ThemeConfig = {
  components: {
    Button: {
      defaultBg: '#A81259',
      defaultBorderColor: '#A81259',
      defaultColor: '#ffffff',
      defaultHoverBg: '#6E0B37',
      defaultHoverBorderColor: '#6E0B37',
      defaultHoverColor: '#ffffff',
      defaultActiveBg: '#33101F',
      defaultActiveBorderColor: '#33101F',
      defaultActiveColor: '#ffffff',
      fontWeight: 500,
      defaultShadow: 'none',
    },
  },
}

// Tablet tap target (#168).
export const VIEW_BUTTON_HEIGHT = 44

interface ViewButtonProps {
  onClick: () => void
  ariaLabel?: string
}

export default function ViewButton({ onClick, ariaLabel }: ViewButtonProps) {
  return (
    <ConfigProvider theme={VIEW_BUTTON_THEME}>
      <Button
        icon={<RightOutlined />}
        iconPosition="end"
        aria-label={ariaLabel}
        style={{ minHeight: VIEW_BUTTON_HEIGHT, minWidth: 104, fontFamily: '"Jost", system-ui, sans-serif', letterSpacing: '0.01em' }}
        onClick={onClick}
      >
        View
      </Button>
    </ConfigProvider>
  )
}
