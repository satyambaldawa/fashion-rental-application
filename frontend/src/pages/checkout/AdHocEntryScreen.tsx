import { useState } from 'react'
import { Button, Card, Empty, List, Space, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import PageHeader from '../../components/common/PageHeader'
import AdHocItemModal from './AdHocItemModal'
import { lineRentOf } from './cartPricing'
import { formatCurrency } from '../../utils/currency'
import type { Cart, CartItem } from '../../types/receipt'

interface AdHocEntryScreenProps {
  cart: Cart | null
  rentalDays: number
  canAddCustomProducts: boolean
  onAddItem: (item: CartItem) => void
  onRemoveItem: (lineKey: string) => void
  onBrowse: () => void
  onReview: () => void
  onSetUpDates: () => void
}

export default function AdHocEntryScreen({
  cart, rentalDays, canAddCustomProducts, onAddItem, onRemoveItem, onBrowse, onReview, onSetUpDates,
}: AdHocEntryScreenProps) {
  const [showModal, setShowModal] = useState(false)

  if (!cart) {
    return (
      <>
        <PageHeader label="Quick Rental" title="Add" accent="Products" />
        <Card>
          <Empty description="Set the rental dates to begin" />
          <div style={{ textAlign: 'center', marginTop: 16 }}>
            <Button type="primary" onClick={onSetUpDates}>Set rental dates</Button>
          </div>
        </Card>
      </>
    )
  }

  return (
    <>
      <PageHeader label="Quick Rental" title="Add" accent="Products" />
      <Card>
        {cart.items.length === 0 ? (
          <Empty description="No products yet — type the first one in" />
        ) : (
          <List
            dataSource={cart.items}
            rowKey={(item) => item.lineKey}
            renderItem={(item) => (
              <List.Item
                actions={[
                  <Button key="remove" type="link" danger onClick={() => onRemoveItem(item.lineKey)}>
                    Remove
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  title={item.itemName}
                  description={[item.size, `Qty ${item.quantity}`].filter(Boolean).join(' · ')}
                />
                <Typography.Text strong>
                  {formatCurrency(lineRentOf(item, rentalDays))}
                </Typography.Text>
              </List.Item>
            )}
          />
        )}

        <Space direction="vertical" style={{ marginTop: 16 }}>
          <Space wrap>
            {canAddCustomProducts && (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setShowModal(true)}>
                Add product
              </Button>
            )}
            <Button onClick={onBrowse}>Browse inventory</Button>
            <Button type="primary" onClick={onReview} disabled={cart.items.length === 0}>
              Review
            </Button>
          </Space>
          {!canAddCustomProducts && (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              Only the owner can add products not in inventory — browse inventory instead.
            </Typography.Text>
          )}
        </Space>
      </Card>

      {canAddCustomProducts && (
        <AdHocItemModal
          open={showModal}
          rentalDays={rentalDays}
          onCancel={() => setShowModal(false)}
          onAdd={(item) => { onAddItem(item); setShowModal(false) }}
        />
      )}
    </>
  )
}
