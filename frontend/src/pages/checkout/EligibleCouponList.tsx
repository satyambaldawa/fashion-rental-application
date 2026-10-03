import { Button, Space, Spin, Tag, Typography } from 'antd'
import { formatCurrency } from '../../utils/currency'
import type { EligibleCoupon } from '../../types/receipt'

interface EligibleCouponListProps {
  coupons: EligibleCoupon[] | undefined
  isLoading: boolean
  isError: boolean
  disabled: boolean
  pendingCode: string | null
  onSelect: (code: string) => void
}

// Purely presentational — discount amounts are never computed here, only displayed from
// what the server already returned.
export default function EligibleCouponList({
  coupons, isLoading, isError, disabled, pendingCode, onSelect,
}: EligibleCouponListProps) {
  return (
    <div style={{ marginBottom: 12 }}>
      <Typography.Text strong style={{ fontSize: 13 }}>Available coupons</Typography.Text>
      <div style={{ marginTop: 8 }}>
        {isLoading && <Spin size="small" />}

        {!isLoading && isError && (
          <Typography.Text type="secondary">
            Couldn't load available coupons — you can still enter a code below.
          </Typography.Text>
        )}

        {!isLoading && !isError && coupons && coupons.length === 0 && (
          <Typography.Text type="secondary">No coupons available for this order.</Typography.Text>
        )}

        {!isLoading && !isError && coupons && coupons.length > 0 && (
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            {coupons.map(coupon => (
              <div
                key={coupon.code}
                style={{
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                  gap: 8, flexWrap: 'wrap',
                }}
              >
                <Space size={8} wrap>
                  <Tag color="green">{coupon.code}</Tag>
                  <Typography.Text type="secondary">
                    {coupon.discountType === 'PERCENT'
                      ? `${coupon.value}% off`
                      : `${formatCurrency(coupon.value)} off`}
                  </Typography.Text>
                  <Typography.Text type="secondary">
                    Save {formatCurrency(coupon.discountAmount)}
                  </Typography.Text>
                </Space>
                <Button
                  size="small"
                  aria-label={`Apply coupon ${coupon.code}`}
                  loading={pendingCode === coupon.code}
                  disabled={disabled}
                  onClick={() => onSelect(coupon.code)}
                >
                  Use
                </Button>
              </div>
            ))}
          </Space>
        )}
      </div>
    </div>
  )
}
