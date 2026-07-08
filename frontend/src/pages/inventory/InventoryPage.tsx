import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Row, Col, Button, Input, Space, Pagination, Spin, Empty } from 'antd'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons'
import { useDebounce } from '../../hooks/useDebounce'
import { itemsApi } from '../../api/items'
import type { ItemCategory } from '../../types/inventory'
import ItemCard from './components/ItemCard'
import ItemDetailDrawer from './components/ItemDetailDrawer'
import PageHeader from '../../components/common/PageHeader'

const CATEGORY_OPTIONS: { label: string; value: ItemCategory | 'ALL' }[] = [
  { label: 'All', value: 'ALL' },
  { label: 'Costume', value: 'COSTUME' },
  { label: 'Accessories', value: 'ACCESSORIES' },
  { label: 'Pagdi', value: 'PAGDI' },
  { label: 'Dress', value: 'DRESS' },
  { label: 'Ornaments', value: 'ORNAMENTS' },
  { label: 'Traditional', value: 'TRADITIONAL' },
  { label: 'Mythological', value: 'MYTHOLOGICAL' },
  { label: 'Freedom Fighter', value: 'FREEDOM_FIGHTER' },
  { label: 'Professions', value: 'PROFESSIONS' },
  { label: 'Fancy Dress', value: 'FANCY_DRESS' },
  { label: 'Seasonal', value: 'SEASONAL' },
  { label: 'Other', value: 'OTHER' },
]

export default function InventoryPage() {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [searchInput, setSearchInput] = useState('')
  const [category, setCategory] = useState<ItemCategory | undefined>(undefined)
  const [itemSize, setItemSize] = useState('')
  const [itemType, setItemType] = useState<'INDIVIDUAL' | 'PACKAGE' | undefined>(undefined)
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null)

  const search = useDebounce(searchInput, 400)

  const { data, isLoading } = useQuery({
    queryKey: ['items', page, search, category, itemSize, itemType],
    queryFn: () => itemsApi.list({
      page,
      size: 20,
      search: search || undefined,
      category: category || undefined,
      itemSize: itemSize || undefined,
      itemType: itemType || undefined,
    }),
  })

  function handleSearchChange(value: string) {
    setSearchInput(value)
    setPage(0)
  }

  function handleCategoryChange(value: ItemCategory | 'ALL') {
    setCategory(value === 'ALL' ? undefined : value)
    setPage(0)
  }

  function handleItemSizeChange(value: string) {
    setItemSize(value)
    setPage(0)
  }

  function handleItemTypeChange(value: 'INDIVIDUAL' | 'PACKAGE' | undefined) {
    setItemType(value)
    setPage(0)
  }

  const activeCategory = category ?? 'ALL'

  return (
    <div>
      <PageHeader
        label="Inventory"
        title="Our"
        accent="Collection"
        count={data?.totalElements}
        action={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => navigate('/inventory/add')}
            style={{ background: '#A81259', borderColor: '#A81259', fontFamily: '"Jost", system-ui, sans-serif', fontWeight: 500 }}
          >
            Add Item
          </Button>
        }
      />

      {/* Filters row */}
      <div style={{ marginBottom: 24 }}>
        {/* Category chips */}
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 14 }}>
          {CATEGORY_OPTIONS.map(opt => {
            const isActive = opt.value === activeCategory
            return (
              <button
                key={opt.value}
                onClick={() => handleCategoryChange(opt.value)}
                style={{
                  padding: '5px 16px',
                  borderRadius: 999,
                  border: `1px solid ${isActive ? '#A81259' : '#eed6e0'}`,
                  background: isActive ? '#6E0B37' : '#fff',
                  color: isActive ? '#fff' : '#7a5361',
                  fontFamily: '"Jost", system-ui, sans-serif',
                  fontWeight: 500,
                  fontSize: 13,
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                  letterSpacing: '0.01em',
                  lineHeight: '22px',
                }}
              >
                {opt.label}
              </button>
            )
          })}
        </div>

        {/* Search + size + type filters */}
        <Space wrap>
          <Input
            prefix={<SearchOutlined style={{ color: '#7a5361' }} />}
            placeholder="Search by name..."
            value={searchInput}
            onChange={e => handleSearchChange(e.target.value)}
            style={{ width: 240, borderColor: '#eed6e0' }}
            allowClear
          />
          <Input
            placeholder="Size"
            value={itemSize}
            onChange={e => handleItemSizeChange(e.target.value)}
            style={{ width: 120, borderColor: '#eed6e0' }}
            allowClear
          />
          {([
            { label: 'All Types', value: undefined },
            { label: 'Individual', value: 'INDIVIDUAL' as const },
            { label: 'Combo', value: 'PACKAGE' as const },
          ]).map(opt => {
            const isActive = opt.value === itemType
            return (
              <button
                key={opt.label}
                onClick={() => handleItemTypeChange(opt.value)}
                style={{
                  padding: '5px 16px',
                  borderRadius: 999,
                  border: `1px solid ${isActive ? '#A81259' : '#eed6e0'}`,
                  background: isActive ? '#6E0B37' : '#fff',
                  color: isActive ? '#fff' : '#7a5361',
                  fontFamily: '"Jost", system-ui, sans-serif',
                  fontWeight: 500,
                  fontSize: 13,
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                  letterSpacing: '0.01em',
                  lineHeight: '22px',
                }}
              >
                {opt.label}
              </button>
            )
          })}
        </Space>
      </div>

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
