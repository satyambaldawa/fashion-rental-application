import { useState, useEffect } from 'react'
import { Modal, Descriptions, Tag, Space, DatePicker, Button, Typography, Divider, Spin } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import type { Dayjs } from 'dayjs'
import type { ItemDetail, AvailabilityResult, ItemPhoto } from '../../../types/inventory'
import { itemsApi } from '../../../api/items'
import { formatCurrency } from '../../../utils/currency'
import PhotoManager from './PhotoManager'

const { RangePicker } = DatePicker

interface Props {
  itemId: string | null
  onClose: () => void
}

export default function ItemDetailDrawer({ itemId, onClose }: Props) {
  const navigate = useNavigate()
  const [availability, setAvailability] = useState<AvailabilityResult | null>(null)
  const [checkingAvailability, setCheckingAvailability] = useState(false)
  const [photos, setPhotos] = useState<ItemPhoto[]>([])

  const { data: item, isLoading } = useQuery<ItemDetail>({
    queryKey: ['item', itemId],
    queryFn: () => itemsApi.get(itemId!),
    enabled: itemId != null,
  })

  useEffect(() => {
    if (item) setPhotos(item.photos)
  }, [item])

  async function handleDateRangeChange(dates: [Dayjs | null, Dayjs | null] | null) {
    if (!dates || !dates[0] || !dates[1] || !itemId) {
      setAvailability(null)
      return
    }

    const [start, end] = dates as [Dayjs, Dayjs]
    setCheckingAvailability(true)
    try {
      const result = await itemsApi.checkAvailability(
        itemId,
        start.toISOString(),
        end.toISOString()
      )
      setAvailability(result)
    } catch {
      setAvailability(null)
    } finally {
      setCheckingAvailability(false)
    }
  }

  return (
    <Modal
      open={itemId != null}
      onCancel={onClose}
      footer={null}
      width={560}
      title={item?.name ?? 'Item Details'}
      style={{ top: 24 }}
      styles={{ body: { maxHeight: 'calc(100vh - 160px)', overflowY: 'auto' } }}
    >
      {isLoading && <div style={{ textAlign: 'center', padding: 32 }}><Spin /></div>}

      {item && (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          {/* Photos */}
          <PhotoManager
            itemId={item.id}
            photos={photos}
            onPhotosChange={setPhotos}
          />

          <Divider />

          {/* Details */}
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="Product ID">
              <Typography.Text code copyable style={{ fontSize: 12 }}>{item.id}</Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label="Category">
              <Tag color="blue">{item.category}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Type">{item.itemType}</Descriptions.Item>
            {item.size && <Descriptions.Item label="Size">{item.size}</Descriptions.Item>}
            <Descriptions.Item label="Daily Rate">{formatCurrency(item.rate)}</Descriptions.Item>
            <Descriptions.Item label="Deposit">{formatCurrency(item.deposit)}</Descriptions.Item>
            <Descriptions.Item label="Total Quantity">{item.quantity}</Descriptions.Item>
            {item.description && (
              <Descriptions.Item label="Description">{item.description}</Descriptions.Item>
            )}
            {item.notes && (
              <Descriptions.Item label="Notes">{item.notes}</Descriptions.Item>
            )}
          </Descriptions>

          <Divider />

          {/* Availability check */}
          <div>
            <Typography.Text strong>Check Availability</Typography.Text>
            <div style={{ marginTop: 8 }}>
              <RangePicker
                showTime
                format="DD MMM YYYY HH:mm"
                onChange={handleDateRangeChange}
                style={{ width: '100%' }}
              />
            </div>

            {checkingAvailability && <Spin size="small" style={{ marginTop: 8 }} />}

            {availability && !checkingAvailability && (
              <div style={{ marginTop: 8 }}>
                {availability.isAvailable ? (
                  <Tag color="success">{availability.availableQuantity} unit(s) available</Tag>
                ) : (
                  <Tag color="error">Not available for selected dates</Tag>
                )}
              </div>
            )}
          </div>

          <div style={{ textAlign: 'right' }}>
            <Button onClick={() => navigate(`/inventory/${item.id}/edit`)}>Edit Item</Button>
          </div>
        </Space>
      )}
    </Modal>
  )
}
