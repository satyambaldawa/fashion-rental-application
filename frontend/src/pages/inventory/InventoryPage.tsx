import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Row, Col, Button, Input, Select, Space, Pagination, Typography, Spin, Empty } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { useDebounce } from '../../hooks/useDebounce'
import { itemsApi } from '../../api/items'
import type { ItemCategory } from '../../types/inventory'
import ItemCard from './components/ItemCard'
import ItemDetailDrawer from './components/ItemDetailDrawer'

const CATEGORY_OPTIONS: { label: string; value: ItemCategory }[] = [
  { label: 'Costume', value: 'COSTUME' },
  { label: 'Accessories', value: 'ACCESSORIES' },
  { label: 'Pagdi', value: 'PAGDI' },
  { label: 'Dress', value: 'DRESS' },
  { label: 'Ornaments', value: 'ORNAMENTS' },
]

export default function InventoryPage() {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [searchInput, setSearchInput] = useState('')
  const [category, setCategory] = useState<ItemCategory | undefined>(undefined)
  const [itemSize, setItemSize] = useState('')
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null)

  const search = useDebounce(searchInput, 400)

  const { data, isLoading } = useQuery({
    queryKey: ['items', page, search, category, itemSize],
    queryFn: () => itemsApi.list({
      page,
      size: 20,
      search: search || undefined,
      category: category || undefined,
      itemSize: itemSize || undefined,
    }),
  })

  function handleSearchChange(value: string) {
    setSearchInput(value)
    setPage(0)
  }

  function handleCategoryChange(value: ItemCategory | undefined) {
    setCategory(value)
    setPage(0)
  }

  function handleItemSizeChange(value: string) {
    setItemSize(value)
    setPage(0)
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          Inventory{data ? ` (${data.totalElements} items)` : ''}
        </Typography.Title>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => navigate('/inventory/add')}
        >
          Add Item
        </Button>
      </div>

      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          placeholder="Search by name..."
          value={searchInput}
          onChange={e => handleSearchChange(e.target.value)}
          style={{ width: 240 }}
          allowClear
        />
        <Select
          placeholder="Category"
          options={CATEGORY_OPTIONS}
          value={category}
          onChange={handleCategoryChange}
          allowClear
          style={{ width: 160 }}
        />
        <Input
          placeholder="Size"
          value={itemSize}
          onChange={e => handleItemSizeChange(e.target.value)}
          style={{ width: 120 }}
          allowClear
        />
      </Space>

      {isLoading && (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}>
          <Spin size="large" />
        </div>
      )}

      {!isLoading && data?.content.length === 0 && (
        <Empty description="No items found" />
      )}

      {!isLoading && data && data.content.length > 0 && (
        <>
          <Row gutter={[16, 16]}>
            {data.content.map(item => (
              <Col key={item.id} xs={24} sm={12} lg={8}>
                <ItemCard item={item} onClick={() => setSelectedItemId(item.id)} />
              </Col>
            ))}
          </Row>

          <div style={{ display: 'flex', justifyContent: 'center', marginTop: 24 }}>
            <Pagination
              current={page + 1}
              total={data.totalElements}
              pageSize={20}
              onChange={p => setPage(p - 1)}
              showSizeChanger={false}
            />
          </div>
        </>
      )}

      <ItemDetailDrawer
        itemId={selectedItemId}
        onClose={() => setSelectedItemId(null)}
      />
    </div>
  )
}
