import { ReactNode } from 'react'

interface Props {
  label: string          // small uppercase eyebrow, e.g. "Inventory"
  title: string          // plain part of heading, e.g. "Our"
  accent: string         // italic wine-colored part, e.g. "Collection"
  count?: number         // optional item count shown next to title
  action?: ReactNode     // optional button on the right
}

export default function PageHeader({ label, title, accent, count, action }: Props) {
  return (
    <div style={{ marginBottom: 28 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <p style={{
            fontFamily: '"Jost", system-ui, sans-serif',
            fontWeight: 600,
            fontSize: 11,
            letterSpacing: '0.18em',
            textTransform: 'uppercase',
            color: '#A81259',
            margin: '0 0 4px',
          }}>
            {label}
          </p>
          <h1 style={{
            fontFamily: '"Cormorant Garamond", Georgia, serif',
            fontWeight: 500,
            fontSize: 36,
            lineHeight: 1.1,
            color: '#33101F',
            margin: 0,
          }}>
            {title}{' '}
            <em style={{ color: '#A81259', fontStyle: 'italic' }}>{accent}</em>
            {count !== undefined && (
              <span style={{
                fontFamily: '"Mulish", system-ui, sans-serif',
                fontWeight: 400,
                fontSize: 14,
                color: '#7a5361',
                marginLeft: 12,
                verticalAlign: 'middle',
              }}>
                {count} items
              </span>
            )}
          </h1>
        </div>
        {action && <div style={{ marginTop: 8 }}>{action}</div>}
      </div>
    </div>
  )
}
