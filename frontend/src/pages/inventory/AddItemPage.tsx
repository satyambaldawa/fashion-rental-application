import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { Form, Input, InputNumber, Select, Button, Space, Typography, Steps } from 'antd'
import { itemsApi } from '../../api/items'
import type { CreateItemRequest, ItemCategory, ItemDetail, ItemPhoto } from '../../types/inventory'
import PhotoManager from './components/PhotoManager'

const CATEGORY_OPTIONS: { label: string; value: ItemCategory }[] = [
  { label: 'Costume', value: 'COSTUME' },
  { label: 'Accessories', value: 'ACCESSORIES' },
  { label: 'Pagdi', value: 'PAGDI' },
  { label: 'Dress', value: 'DRESS' },
  { label: 'Ornaments', value: 'ORNAMENTS' },
]

export default function AddItemPage() {
  const navigate = useNavigate()
  const [form] = Form.useForm<CreateItemRequest>()
  const [createdItem, setCreatedItem] = useState<ItemDetail | null>(null)
  const [photos, setPhotos] = useState<ItemPhoto[]>([])

  const mutation = useMutation({
    mutationFn: (data: CreateItemRequest) => itemsApi.create(data),
    onSuccess: (item) => {
      setCreatedItem(item)
      setPhotos([])
    },
  })

  function handleSubmit(values: CreateItemRequest) {
    mutation.mutate(values)
  }

  const step = createdItem ? 1 : 0

  return (
    <div style={{ maxWidth: 600 }}>
      <Typography.Title level={4} style={{ marginBottom: 24 }}>Add New Item</Typography.Title>

      <Steps
        current={step}
        items={[{ title: 'Item Details' }, { title: 'Photos' }]}
        style={{ marginBottom: 32 }}
      />

      {step === 0 && (
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{ quantity: 1, deposit: 0 }}
        >
          <Form.Item
            name="name"
            label="Item Name"
            rules={[{ required: true, message: 'Name is required' }]}
          >
            <Input placeholder="e.g. Maharaja Costume" />
          </Form.Item>

          <Form.Item
            name="category"
            label="Category"
            rules={[{ required: true, message: 'Category is required' }]}
          >
            <Select options={CATEGORY_OPTIONS} placeholder="Select category" />
          </Form.Item>

          <Form.Item name="size" label="Size">
            <Input placeholder="e.g. M, L, XL, 32, Free Size" />
          </Form.Item>

          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} placeholder="Optional description" />
          </Form.Item>

          <Form.Item
            name="rate"
            label="Daily Rate (₹)"
            rules={[{ required: true, message: 'Daily rate is required' }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} placeholder="e.g. 500" />
          </Form.Item>

          <Form.Item
            name="deposit"
            label="Deposit (₹)"
            rules={[{ required: true, message: 'Deposit is required' }]}
          >
            <InputNumber min={0} style={{ width: '100%' }} placeholder="e.g. 1000" />
          </Form.Item>

          <Form.Item
            name="quantity"
            label="Quantity"
            rules={[{ required: true, message: 'Quantity is required' }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} placeholder="Internal notes" />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" loading={mutation.isPending}>
                Next: Add Photos
              </Button>
              <Button onClick={() => navigate('/inventory')}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      )}

      {step === 1 && createdItem && (
        <div>
          <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
            Add photos for <strong>{createdItem.name}</strong>. You can add up to 8 photos.
          </Typography.Text>

          <PhotoManager
            itemId={createdItem.id}
            photos={photos}
            onPhotosChange={setPhotos}
          />

          <div style={{ marginTop: 24 }}>
            <Space>
              <Button type="primary" onClick={() => navigate('/inventory')}>
                Done — Go to Inventory
              </Button>
              <Button
                onClick={() => {
                  setCreatedItem(null)
                  setPhotos([])
                  form.resetFields()
                }}
              >
                Add Another Item
              </Button>
            </Space>
          </div>
        </div>
      )}
    </div>
  )
}
