import { Modal, Typography, Tag, Space, Spin, Carousel } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { ShopOutlined } from '@ant-design/icons'
import { itemsApi } from '../../api/items'
import { formatCurrency } from '../../utils/currency'
import type { ItemSummary } from '../../types/inventory'

interface Props {
  item: ItemSummary | null
  onClose: () => void
  onAddToCart: (item: ItemSummary) => void
  inCartQty: number
}

export default function PackageDetailModal({ item, onClose, onAddToCart, inCartQty }: Props) {
  const { data: detail, isLoading } = useQuery({
    queryKey: ['item', item?.id],
    queryFn: () => itemsApi.get(item!.id),
    enabled: item != null,
  })

  return (
    <Modal
      open={item != null}
      onCancel={onClose}
      footer={null}
      width={680}
      title={
        item ? (
          <Space>
            <Tag color="purple">Package</Tag>
            <span>{item.name}</span>
          </Space>
        ) : null
      }
      style={{ top: 20 }}
      styles={{ body: { maxHeight: 'calc(100vh - 160px)', overflowY: 'auto' } }}
    >
      {item && (
        <>
          {/* Package summary */}
          <div style={{ display: 'flex', gap: 16, marginBottom: 20, padding: 16, background: '#fafafa', borderRadius: 8 }}>
            <div style={{ flex: 1 }}>
              <Space wrap>
                <Tag color="blue">{item.category}</Tag>
              </Space>
              <div style={{ marginTop: 8 }}>
                <Typography.Text strong style={{ fontSize: 16 }}>{formatCurrency(item.rate)}</Typography.Text>
                <Typography.Text type="secondary">/day</Typography.Text>
                <Typography.Text type="secondary" style={{ marginLeft: 12 }}>Deposit: {formatCurrency(item.deposit)}</Typography.Text>
              </div>
              <div style={{ marginTop: 4 }}>
                <Tag color={item.availableQuantity > 0 ? 'success' : 'default'}>
                  {item.availableQuantity > 0 ? `${item.availableQuantity} set${item.availableQuantity !== 1 ? 's' : ''} available` : 'Unavailable'}
                </Tag>
                {inCartQty > 0 && <Tag color="orange" style={{ marginLeft: 4 }}>In cart ×{inCartQty}</Tag>}
              </div>
            </div>
          </div>

          {/* Components */}
          {isLoading && <div style={{ textAlign: 'center', padding: 24 }}><Spin /></div>}

          {detail?.components && (
            <>
              <Typography.Title level={5} style={{ marginBottom: 12 }}>
                Includes ({detail.components.length} item{detail.components.length !== 1 ? 's' : ''})
              </Typography.Title>

              {detail.components.map(comp => (
                <div
                  key={comp.componentItemId}
                  style={{ display: 'flex', gap: 14, padding: '14px 0', borderTop: '1px solid #f0f0f0' }}
                >
                  {/* Photos */}
                  <div style={{ width: 100, flexShrink: 0 }}>
                    {comp.componentItemPhotos.length > 0 ? (
                      comp.componentItemPhotos.length === 1 ? (
                        <img
                          src={comp.componentItemPhotos[0].url}
                          alt={comp.componentItemName}
                          style={{ width: 100, height: 100, objectFit: 'cover', borderRadius: 6 }}
                        />
                      ) : (
                        <Carousel dots={{ className: 'small-dots' }} style={{ width: 100 }}>
                          {comp.componentItemPhotos.map(p => (
                            <div key={p.id}>
                              <img
                                src={p.url}
                                alt={comp.componentItemName}
                                style={{ width: 100, height: 100, objectFit: 'cover', borderRadius: 6 }}
                              />
                            </div>
                          ))}
                        </Carousel>
                      )
                    ) : (
                      <div style={{ width: 100, height: 100, background: '#f0f0f0', borderRadius: 6, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <ShopOutlined style={{ fontSize: 28, color: '#bbb' }} />
                      </div>
                    )}
                  </div>

                  {/* Info */}
                  <div style={{ flex: 1 }}>
                    <Typography.Text strong style={{ fontSize: 14 }}>{comp.componentItemName}</Typography.Text>
                    <div style={{ fontSize: 12, color: '#888', fontFamily: 'monospace', marginBottom: 4 }}>
                      {comp.componentItemId.slice(0, 8)}…
                    </div>
                    <Space size={4} wrap>
                      <Tag color="blue">{comp.componentItemCategory}</Tag>
                      {comp.componentItemSize && <Tag>{comp.componentItemSize}</Tag>}
                      <Tag>×{comp.quantity} per set</Tag>
                    </Space>
                    {comp.componentItemDescription && (
                      <Typography.Text type="secondary" style={{ fontSize: 13, display: 'block', marginTop: 4 }}>
                        {comp.componentItemDescription}
                      </Typography.Text>
                    )}
                  </div>
                </div>
              ))}
            </>
          )}

          {/* Add to cart */}
          {item.availableQuantity > 0 && (
            <div style={{ marginTop: 20, textAlign: 'right' }}>
              <button
                onClick={() => { onAddToCart(item); onClose() }}
                style={{
                  background: '#1677ff', color: '#fff', border: 'none',
                  borderRadius: 6, padding: '8px 20px', fontSize: 14,
                  cursor: 'pointer', fontWeight: 500,
                }}
              >
                Add to Cart
              </button>
            </div>
          )}
        </>
      )}
    </Modal>
  )
}
