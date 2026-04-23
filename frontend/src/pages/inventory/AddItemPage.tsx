import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Radio,
  Select,
  Space,
  Steps,
  Table,
  Tag,
  Typography,
} from 'antd'
import { DeleteOutlined, PlusOutlined, ShopOutlined } from '@ant-design/icons'
import { itemsApi } from '../../api/items'
import type {
  CreateItemRequest,
  ItemCategory,
  ItemDetail,
  ItemPhoto,
  ItemSummary,
  ItemType,
  PackageComponentRequest,
} from '../../types/inventory'
import PhotoManager from './components/PhotoManager'

const CATEGORY_OPTIONS: { label: string; value: ItemCategory }[] = [
  { label: 'Costume', value: 'COSTUME' },
  { label: 'Accessories', value: 'ACCESSORIES' },
  { label: 'Pagdi', value: 'PAGDI' },
  { label: 'Dress', value: 'DRESS' },
  { label: 'Ornaments', value: 'ORNAMENTS' },
]

interface ComponentDraft {
  componentItemId: string
  componentItemName: string
  quantity: number
}

export default function AddItemPage() {
  const navigate = useNavigate()
  const [form] = Form.useForm()
  const [itemType, setItemType] = useState<ItemType>('INDIVIDUAL')
  const [createdItem, setCreatedItem] = useState<ItemDetail | null>(null)
  const [photos, setPhotos] = useState<ItemPhoto[]>([])

  // Package component state
  const [componentSearch, setComponentSearch] = useState('')
  const [components, setComponents] = useState<ComponentDraft[]>([])
  const [componentError, setComponentError] = useState<string | null>(null)

  // Search individual items for component picker
  const { data: searchResults } = useQuery({
    queryKey: ['items-individual', componentSearch],
    queryFn: () => itemsApi.list({ search: componentSearch, size: 8 }),
    enabled: componentSearch.length >= 1,
  })

  const individualResults: ItemSummary[] = (searchResults?.content ?? []).filter(
    (i: ItemSummary) => i.itemType === 'INDIVIDUAL' && !components.some(c => c.componentItemId === i.id)
  )

  const mutation = useMutation({
    mutationFn: (data: CreateItemRequest) => itemsApi.create(data),
    onSuccess: (item) => {
      setCreatedItem(item)
      setPhotos([])
    },
  })

  function handleAddComponent(item: ItemSummary) {
    setComponents(prev => [...prev, { componentItemId: item.id, componentItemName: item.name, quantity: 1 }])
    setComponentSearch('')
    setComponentError(null)
  }

  function handleRemoveComponent(id: string) {
    setComponents(prev => prev.filter(c => c.componentItemId !== id))
  }

  function handleComponentQtyChange(id: string, qty: number) {
    setComponents(prev => prev.map(c => c.componentItemId === id ? { ...c, quantity: qty } : c))
  }

  function handleSubmit(values: Record<string, unknown>) {
    if (itemType === 'PACKAGE' && components.length === 0) {
      setComponentError('A package must have at least one component item.')
      return
    }

    const componentRequests: PackageComponentRequest[] = components.map(c => ({
      componentItemId: c.componentItemId,
      quantity: c.quantity,
    }))

    const request: CreateItemRequest = {
      name: values.name as string,
      category: values.category as ItemCategory,
      itemType,
      size: itemType === 'INDIVIDUAL' ? (values.size as string | undefined) : undefined,
      description: values.description as string | undefined,
      rate: values.rate as number,
      deposit: values.deposit as number,
      quantity: values.quantity as number,
      notes: values.notes as string | undefined,
      purchaseRate: values.purchaseRate as number | undefined,
      vendorName: values.vendorName as string | undefined,
      components: itemType === 'PACKAGE' ? componentRequests : undefined,
    }

    mutation.mutate(request)
  }

  const step = createdItem ? 1 : 0

  return (
    <div style={{ maxWidth: 640 }}>
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
          {/* Item type toggle */}
          <Form.Item label="Item Type">
            <Radio.Group
              value={itemType}
              onChange={e => { setItemType(e.target.value); setComponents([]); setComponentError(null) }}
            >
              <Radio value="INDIVIDUAL">Individual item</Radio>
              <Radio value="PACKAGE">Package / Group offering</Radio>
            </Radio.Group>
          </Form.Item>

          <Form.Item
            name="name"
            label={itemType === 'PACKAGE' ? 'Package Name' : 'Item Name'}
            rules={[{ required: true, message: 'Name is required' }]}
          >
            <Input placeholder={itemType === 'PACKAGE' ? 'e.g. Freedom Fighter Set' : 'e.g. Maharaja Costume'} />
          </Form.Item>

          <Form.Item
            name="category"
            label="Category"
            rules={[{ required: true, message: 'Category is required' }]}
          >
            <Select options={CATEGORY_OPTIONS} placeholder="Select category" />
          </Form.Item>

          {itemType === 'INDIVIDUAL' && (
            <Form.Item name="size" label="Size">
              <Input placeholder="e.g. M, L, XL, 32, Free Size" />
            </Form.Item>
          )}

          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} placeholder="Optional description" />
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
            label={itemType === 'PACKAGE' ? 'Quantity (number of complete sets)' : 'Quantity'}
            rules={[{ required: true, message: 'Quantity is required' }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} placeholder="Internal notes" />
          </Form.Item>

          {/* Package component picker */}
          {itemType === 'PACKAGE' && (
            <Form.Item label="Package Components" required>
              <Card size="small" style={{ marginBottom: 8 }}>
                <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 8 }}>
                  Search and add individual items that make up this package.
                </Typography.Text>

                <Input
                  placeholder="Search individual items..."
                  value={componentSearch}
                  onChange={e => setComponentSearch(e.target.value)}
                  style={{ marginBottom: 8 }}
                />

                {individualResults.length > 0 && (
                  <div style={{ border: '1px solid #d9d9d9', borderRadius: 6, marginBottom: 8 }}>
                    {individualResults.map(item => (
                      <div
                        key={item.id}
                        style={{
                          padding: '8px 12px',
                          display: 'flex',
                          alignItems: 'center',
                          gap: 10,
                          cursor: 'pointer',
                          borderBottom: '1px solid #f0f0f0',
                        }}
                        onClick={() => handleAddComponent(item)}
                      >
                        {/* Thumbnail */}
                        <div style={{
                          width: 48, height: 48, flexShrink: 0,
                          background: '#f0f0f0', borderRadius: 6, overflow: 'hidden',
                          display: 'flex', alignItems: 'center', justifyContent: 'center',
                        }}>
                          {item.thumbnailUrl ? (
                            <img src={item.thumbnailUrl} alt={item.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                          ) : (
                            <ShopOutlined style={{ color: '#bbb', fontSize: 20 }} />
                          )}
                        </div>
                        {/* Info */}
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ fontWeight: 500, fontSize: 13 }}>{item.name}</div>
                          <div style={{ fontSize: 11, color: '#888', fontFamily: 'monospace' }}>
                            {item.id.slice(0, 8)}…
                          </div>
                          <Space size={4} style={{ marginTop: 2 }}>
                            <Tag color="blue" style={{ fontSize: 11, margin: 0 }}>{item.category}</Tag>
                            {item.size && <Tag style={{ fontSize: 11, margin: 0 }}>{item.size}</Tag>}
                          </Space>
                          {item.description && (
                            <div style={{ fontSize: 12, color: '#666', marginTop: 2, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                              {item.description}
                            </div>
                          )}
                        </div>
                        <Button size="small" type="link" icon={<PlusOutlined />} />
                      </div>
                    ))}
                  </div>
                )}

                {components.length > 0 && (
                  <Table
                    dataSource={components}
                    rowKey="componentItemId"
                    size="small"
                    pagination={false}
                    columns={[
                      { title: 'Item', dataIndex: 'componentItemName', key: 'name' },
                      {
                        title: 'Qty per set',
                        key: 'qty',
                        width: 120,
                        render: (_: unknown, row: ComponentDraft) => (
                          <InputNumber
                            min={1}
                            value={row.quantity}
                            onChange={v => handleComponentQtyChange(row.componentItemId, v ?? 1)}
                            size="small"
                            style={{ width: 80 }}
                          />
                        ),
                      },
                      {
                        title: '',
                        key: 'remove',
                        width: 40,
                        render: (_: unknown, row: ComponentDraft) => (
                          <Button
                            size="small"
                            type="text"
                            danger
                            icon={<DeleteOutlined />}
                            onClick={() => handleRemoveComponent(row.componentItemId)}
                          />
                        ),
                      },
                    ]}
                  />
                )}
              </Card>
              {componentError && <Alert type="error" message={componentError} style={{ marginTop: 4 }} />}
            </Form.Item>
          )}

          <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 8, marginTop: 8, fontSize: 12 }}>
            Purchase details below are for shop records only and are never shown to customers.
          </Typography.Text>

          <Form.Item name="purchaseRate" label="Purchase Rate (₹)">
            <InputNumber min={0} style={{ width: '100%' }} placeholder="e.g. 3000" />
          </Form.Item>

          <Form.Item name="vendorName" label="Vendor / Supplier Name">
            <Input placeholder="e.g. Rajesh Textiles" />
          </Form.Item>

          {mutation.isError && (
            <Alert type="error" message="Failed to create item. Please try again." style={{ marginBottom: 12 }} />
          )}

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
                  setItemType('INDIVIDUAL')
                  setComponents([])
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
