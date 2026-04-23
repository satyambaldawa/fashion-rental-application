import { useState } from 'react'
import { Modal, Tag, Space, Spin, Button, Typography, Tooltip } from 'antd'
import { LeftOutlined, RightOutlined, ShopOutlined, PlusOutlined, MinusOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { itemsApi } from '../../api/items'
import type { ItemSummary } from '../../types/inventory'
import { formatCurrency } from '../../utils/currency'

interface PhotoSlide {
  url: string
  label: string   // which item/component this photo belongs to
  isPackage: boolean
}

interface Props {
  item: ItemSummary | null
  onClose: () => void
  onAddToCart: (item: ItemSummary) => void
  onRemoveFromCart: (itemId: string) => void
  onUpdateQty: (itemId: string, qty: number) => void
  inCartQty: number
  maxQty: number
}

export default function ItemBrowseModal({
  item, onClose, onAddToCart, onRemoveFromCart, onUpdateQty, inCartQty, maxQty,
}: Props) {
  const [photoIndex, setPhotoIndex] = useState(0)
  const [syncedId, setSyncedId] = useState<string | null>(null)

  const { data: detail, isLoading } = useQuery({
    queryKey: ['item', item?.id],
    queryFn: () => itemsApi.get(item!.id),
    enabled: item != null,
  })

  // Reset photo index when a different item opens
  if (detail && detail.id !== syncedId) {
    setSyncedId(detail.id)
    setPhotoIndex(0)
  }

  // Build a flat list of all photos across package + components
  const slides: PhotoSlide[] = []
  if (detail) {
    detail.photos.forEach(p =>
      slides.push({ url: p.url, label: detail.name, isPackage: detail.itemType === 'PACKAGE' })
    )
    if (detail.itemType === 'PACKAGE' && detail.components) {
      detail.components.forEach(comp =>
        comp.componentItemPhotos.forEach(p =>
          slides.push({ url: p.url, label: comp.componentItemName, isPackage: false })
        )
      )
    }
  }

  const safeIndex = slides.length === 0 ? 0 : Math.min(photoIndex, slides.length - 1)
  const current = slides[safeIndex]

  function prev() { setPhotoIndex(i => (i === 0 ? slides.length - 1 : i - 1)) }
  function next() { setPhotoIndex(i => (i === slides.length - 1 ? 0 : i + 1)) }

  return (
    <Modal
      open={item != null}
      onCancel={onClose}
      footer={null}
      width={600}
      title={
        item ? (
          <Space>
            {item.itemType === 'PACKAGE'
              ? <Tag color="purple" style={{ margin: 0 }}>Combo</Tag>
              : <Tag style={{ margin: 0 }}>Individual</Tag>}
            <span>{item.name}</span>
          </Space>
        ) : null
      }
      style={{ top: 20 }}
      styles={{ body: { padding: 0, maxHeight: 'calc(100vh - 160px)', overflowY: 'auto' } }}
    >
      {item && (
        <>
          {/* ── Photo carousel ── */}
          <div style={{ position: 'relative', width: '100%', aspectRatio: '1 / 1', background: '#f5f5f5', overflow: 'hidden' }}>
            {isLoading && (
              <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Spin />
              </div>
            )}

            {!isLoading && slides.length === 0 && (
              <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <ShopOutlined style={{ fontSize: 64, color: '#ccc' }} />
              </div>
            )}

            {slides.length > 0 && (
              <img
                src={current.url}
                alt={current.label}
                style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
              />
            )}

            {/* Left / Right arrows */}
            {slides.length > 1 && (
              <>
                <Button
                  shape="circle"
                  icon={<LeftOutlined />}
                  onClick={prev}
                  style={{
                    position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)',
                    background: 'rgba(0,0,0,0.45)', border: 'none', color: '#fff',
                  }}
                />
                <Button
                  shape="circle"
                  icon={<RightOutlined />}
                  onClick={next}
                  style={{
                    position: 'absolute', right: 10, top: '50%', transform: 'translateY(-50%)',
                    background: 'rgba(0,0,0,0.45)', border: 'none', color: '#fff',
                  }}
                />
              </>
            )}

            {/* Photo label + counter */}
            {slides.length > 0 && (
              <div style={{
                position: 'absolute', bottom: 0, left: 0, right: 0,
                background: 'linear-gradient(transparent, rgba(0,0,0,0.55))',
                padding: '20px 12px 10px',
                display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end',
              }}>
                <span style={{ color: '#fff', fontSize: 13, fontWeight: 500 }}>
                  {current.isPackage && detail?.itemType === 'PACKAGE' && slides.filter(s => s.isPackage).length > 0
                    ? `${current.label} (combo)`
                    : current.label}
                </span>
                {slides.length > 1 && (
                  <span style={{ color: 'rgba(255,255,255,0.8)', fontSize: 12 }}>
                    {safeIndex + 1} / {slides.length}
                  </span>
                )}
              </div>
            )}

            {/* Dot strip for quick navigation */}
            {slides.length > 1 && slides.length <= 12 && (
              <div style={{
                position: 'absolute', bottom: 36, left: 0, right: 0,
                display: 'flex', justifyContent: 'center', gap: 5,
              }}>
                {slides.map((_, i) => (
                  <div
                    key={i}
                    onClick={() => setPhotoIndex(i)}
                    style={{
                      width: i === safeIndex ? 18 : 6,
                      height: 6, borderRadius: 3,
                      background: i === safeIndex ? '#fff' : 'rgba(255,255,255,0.5)',
                      cursor: 'pointer',
                      transition: 'width 0.2s',
                    }}
                  />
                ))}
              </div>
            )}
          </div>

          {/* ── Details ── */}
          <div style={{ padding: '16px 20px' }}>
            {/* Rate / deposit / availability */}
            <div style={{ display: 'flex', gap: 24, marginBottom: 12, flexWrap: 'wrap' }}>
              <div>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>Rate</Typography.Text>
                <div style={{ fontWeight: 600, fontSize: 18 }}>{formatCurrency(item.rate)}<span style={{ fontWeight: 400, fontSize: 13, color: '#888' }}>/day</span></div>
              </div>
              <div>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>Deposit</Typography.Text>
                <div style={{ fontWeight: 600, fontSize: 18 }}>{formatCurrency(item.deposit)}</div>
              </div>
              <div>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>Available</Typography.Text>
                <div style={{ marginTop: 2 }}>
                  <Tag color={item.availableQuantity > 0 ? 'success' : 'default'}>
                    {item.availableQuantity > 0 ? `${item.availableQuantity} available` : 'Unavailable'}
                  </Tag>
                </div>
              </div>
            </div>

            {/* ID + Tags row */}
            <div style={{ marginBottom: 8 }}>
              <Tooltip title={item.id}>
                <Typography.Text type="secondary" style={{ fontSize: 11, fontFamily: 'monospace', cursor: 'default' }}>
                  #{item.id.slice(0, 8)}
                </Typography.Text>
              </Tooltip>
            </div>
            <Space wrap style={{ marginBottom: 12 }}>
              <Tag color="blue">{item.category}</Tag>
              {item.size && <Tag>{item.size}</Tag>}
            </Space>

            {item.description && (
              <Typography.Paragraph style={{ fontSize: 13, marginBottom: 12 }}>
                <strong>Description: </strong>
                <Typography.Text type="secondary">{item.description}</Typography.Text>
              </Typography.Paragraph>
            )}

            {/* ── Combo components ── */}
            {detail?.itemType === 'PACKAGE' && detail.components && detail.components.length > 0 && (
              <div style={{ marginTop: 8, marginBottom: 16 }}>
                <Typography.Text strong style={{ display: 'block', marginBottom: 10, fontSize: 14 }}>
                  Includes ({detail.components.length} item{detail.components.length !== 1 ? 's' : ''})
                </Typography.Text>

                {detail.components.map(comp => (
                  <div
                    key={comp.componentItemId}
                    style={{ display: 'flex', gap: 12, paddingBottom: 12, marginBottom: 12, borderBottom: '1px solid #f0f0f0' }}
                  >
                    {/* Component thumbnail strip */}
                    <div style={{ display: 'flex', gap: 4, flexShrink: 0 }}>
                      {comp.componentItemPhotos.length === 0 ? (
                        <div style={{
                          width: 64, height: 64, background: '#f0f0f0', borderRadius: 6,
                          display: 'flex', alignItems: 'center', justifyContent: 'center',
                        }}>
                          <ShopOutlined style={{ fontSize: 20, color: '#bbb' }} />
                        </div>
                      ) : (
                        comp.componentItemPhotos.slice(0, 3).map((p, i) => (
                          <img
                            key={p.id}
                            src={p.thumbnailUrl || p.url}
                            alt={comp.componentItemName}
                            style={{
                              width: 64, height: 64, objectFit: 'cover', borderRadius: 6,
                              opacity: i > 0 ? 0.7 : 1,
                            }}
                          />
                        ))
                      )}
                    </div>

                    {/* Component info */}
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <Typography.Text strong style={{ fontSize: 13 }}>{comp.componentItemName}</Typography.Text>
                      <div style={{ marginTop: 4 }}>
                        <Space size={4} wrap>
                          <Tag color="blue" style={{ fontSize: 11 }}>{comp.componentItemCategory}</Tag>
                          {comp.componentItemSize && <Tag style={{ fontSize: 11 }}>{comp.componentItemSize}</Tag>}
                          <Tag style={{ fontSize: 11 }}>×{comp.quantity} per set</Tag>
                        </Space>
                      </div>
                      {comp.componentItemDescription && (
                        <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 4 }}>
                          {comp.componentItemDescription}
                        </Typography.Text>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* ── Cart controls ── */}
            {item.availableQuantity > 0 && (
              <div style={{ display: 'flex', justifyContent: 'center', marginTop: 16 }}>
                {inCartQty > 0 ? (
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <Button
                      icon={<MinusOutlined />}
                      onClick={() => inCartQty === 1 ? onRemoveFromCart(item.id) : onUpdateQty(item.id, inCartQty - 1)}
                    />
                    <Typography.Text strong style={{ minWidth: 32, textAlign: 'center', fontSize: 16 }}>
                      {inCartQty}
                    </Typography.Text>
                    <Button
                      icon={<PlusOutlined />}
                      disabled={inCartQty >= maxQty}
                      onClick={() => onUpdateQty(item.id, inCartQty + 1)}
                    />
                    <Typography.Text type="secondary" style={{ fontSize: 13 }}>in cart</Typography.Text>
                  </div>
                ) : (
                  <Button
                    type="primary"
                    size="large"
                    icon={<PlusOutlined />}
                    onClick={() => { onAddToCart(item); onClose() }}
                    style={{ minWidth: 180 }}
                  >
                    Add to Cart
                  </Button>
                )}
              </div>
            )}
          </div>
        </>
      )}
    </Modal>
  )
}
