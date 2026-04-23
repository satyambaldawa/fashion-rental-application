import { useState } from 'react'
import { Card, Tag, Space, Button } from 'antd'
import { ShopOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons'
import type { ItemSummary } from '../../../types/inventory'
import { formatCurrency } from '../../../utils/currency'

interface Props {
  item: ItemSummary
  onClick: () => void
}

export default function ItemCard({ item, onClick }: Props) {
  const [activeIndex, setActiveIndex] = useState(0)
  const photos = item.photoUrls ?? []
  const safeIndex = photos.length === 0 ? 0 : Math.min(activeIndex, photos.length - 1)

  function prev(e: React.MouseEvent) {
    e.stopPropagation()
    setActiveIndex(i => (i === 0 ? photos.length - 1 : i - 1))
  }

  function next(e: React.MouseEvent) {
    e.stopPropagation()
    setActiveIndex(i => (i === photos.length - 1 ? 0 : i + 1))
  }

  const cover = (
    <div style={{ position: 'relative', width: '100%', aspectRatio: '1 / 1', background: '#f0f0f0', overflow: 'hidden' }}>
      {photos.length > 0 ? (
        <img
          src={photos[safeIndex]}
          alt={item.name}
          style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
        />
      ) : (
        <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <ShopOutlined style={{ fontSize: 48, color: '#bbb' }} />
        </div>
      )}

      {photos.length > 1 && (
        <>
          <Button
            shape="circle"
            icon={<LeftOutlined />}
            onClick={prev}
            size="small"
            style={{
              position: 'absolute',
              left: 6,
              top: '50%',
              transform: 'translateY(-50%)',
              background: 'rgba(0,0,0,0.45)',
              border: 'none',
              color: '#fff',
            }}
          />
          <Button
            shape="circle"
            icon={<RightOutlined />}
            onClick={next}
            size="small"
            style={{
              position: 'absolute',
              right: 6,
              top: '50%',
              transform: 'translateY(-50%)',
              background: 'rgba(0,0,0,0.45)',
              border: 'none',
              color: '#fff',
            }}
          />
          <div
            style={{
              position: 'absolute',
              bottom: 6,
              right: 8,
              background: 'rgba(0,0,0,0.45)',
              color: '#fff',
              fontSize: 11,
              padding: '1px 7px',
              borderRadius: 10,
            }}
          >
            {safeIndex + 1} / {photos.length}
          </div>
        </>
      )}
    </div>
  )

  return (
    <Card
      hoverable
      onClick={onClick}
      style={{ opacity: item.isAvailable ? 1 : 0.6 }}
      cover={cover}
    >
      <Card.Meta
        title={item.name}
        description={
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            <div style={{ fontSize: 11, fontFamily: 'monospace', color: '#999' }}>#{item.id.slice(0, 8)}</div>
            <Space>
              <Tag color="blue">{item.category}</Tag>
              {item.itemType === 'PACKAGE'
                ? <Tag color="purple">Combo</Tag>
                : <Tag>Individual</Tag>}
              {item.size && <Tag>{item.size}</Tag>}
            </Space>
            <div>{formatCurrency(item.rate)}/day</div>
            <div style={{ color: '#666' }}>Deposit: {formatCurrency(item.deposit)}</div>
            {item.isAvailable ? (
              <Tag color="success">{item.availableQuantity} available</Tag>
            ) : (
              <Tag color="default">Unavailable</Tag>
            )}
          </Space>
        }
      />
    </Card>
  )
}
