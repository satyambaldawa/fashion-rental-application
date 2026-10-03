import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Row, Col, Image, Typography, Empty, Spin } from 'antd'
import { galleryApi } from '../../api/gallery'
import { CATEGORY_OPTIONS, CATEGORY_LABELS } from '../../constants/categories'
import type { GalleryImage } from '../../types/gallery'
import type { ItemCategory } from '../../types/inventory'

const FILTER_OPTIONS: { label: string; value: ItemCategory | 'ALL' }[] = [
  { label: 'All', value: 'ALL' },
  ...CATEGORY_OPTIONS,
]

const VALID_CATEGORIES = new Set<string>(CATEGORY_OPTIONS.map(opt => opt.value))

function isItemCategory(value: string): value is ItemCategory {
  return VALID_CATEGORIES.has(value)
}

function groupByCategory(images: GalleryImage[]): [ItemCategory, GalleryImage[]][] {
  const groups = new Map<ItemCategory, GalleryImage[]>()
  for (const image of images) {
    const bucket = groups.get(image.category)
    if (bucket) bucket.push(image)
    else groups.set(image.category, [image])
  }
  return Array.from(groups.entries())
}

function GalleryTile({ image }: { image: GalleryImage }) {
  return (
    <div>
      <div style={{ width: '100%', aspectRatio: '3/4', overflow: 'hidden', borderRadius: 8 }}>
        <Image
          src={image.thumbnailUrl}
          preview={{ src: image.imageUrl }}
          loading="lazy"
          width="100%"
          height="100%"
          style={{ objectFit: 'cover' }}
        />
      </div>
      {image.caption && (
        <Typography.Text
          type="secondary"
          style={{ fontSize: 12, display: 'block', marginTop: 6, textAlign: 'center' }}
        >
          {image.caption}
        </Typography.Text>
      )}
    </div>
  )
}

function GallerySection({ category, images, showTitle }: {
  category: ItemCategory
  images: GalleryImage[]
  showTitle: boolean
}) {
  return (
    <div style={{ marginBottom: 32 }}>
      {showTitle && (
        <Typography.Title level={5} style={{ marginBottom: 14 }}>
          {CATEGORY_LABELS.get(category) ?? category}
        </Typography.Title>
      )}
      <Image.PreviewGroup>
        <Row gutter={[16, 16]}>
          {images.map(image => (
            <Col key={image.id} xs={12} sm={12} md={8} lg={6} xl={6}>
              <GalleryTile image={image} />
            </Col>
          ))}
        </Row>
      </Image.PreviewGroup>
    </div>
  )
}

export default function GalleryPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  // An empty `?category=` is treated as "All", not as invalid — only a non-empty,
  // unrecognized value triggers the "Category not found" branch.
  const rawCategory = searchParams.get('category')
  const isUnknownCategory = !!rawCategory && !isItemCategory(rawCategory)
  const category: ItemCategory | undefined =
    rawCategory && isItemCategory(rawCategory) ? rawCategory : undefined

  const { data: images, isLoading } = useQuery({
    queryKey: ['public-gallery', category, isUnknownCategory ? 'invalid' : 'valid'],
    queryFn: () => galleryApi.list(category),
    enabled: !isUnknownCategory,
  })

  const activeCategory = isUnknownCategory ? null : (category ?? 'ALL')
  const sections = groupByCategory(images ?? [])

  function selectCategory(value: ItemCategory | 'ALL') {
    if (value === activeCategory) return
    setSearchParams(prev => {
      const next = new URLSearchParams(prev)
      if (value === 'ALL') {
        next.delete('category')
      } else {
        next.set('category', value)
      }
      return next
    }, { replace: true })
  }

  return (
    <div>
      {/* Category chips */}
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 24 }}>
        {FILTER_OPTIONS.map(opt => {
          const isActive = opt.value === activeCategory
          return (
            <button
              key={opt.value}
              aria-pressed={isActive}
              onClick={() => selectCategory(opt.value)}
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

      {isUnknownCategory && <Empty description="Category not found" />}

      {!isUnknownCategory && isLoading && (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}>
          <Spin size="large" />
        </div>
      )}

      {!isUnknownCategory && !isLoading && sections.length === 0 && (
        <Empty description="No images to show" />
      )}

      {!isUnknownCategory && !isLoading && sections.map(([sectionCategory, sectionImages]) => (
        <GallerySection
          key={sectionCategory}
          category={sectionCategory}
          images={sectionImages}
          showTitle={category === undefined}
        />
      ))}
    </div>
  )
}
