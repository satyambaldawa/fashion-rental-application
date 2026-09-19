import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Select, Upload, Button, Row, Col, Card, Image, Input, Switch,
  Popconfirm, Empty, Spin, Typography, message, Tag,
} from 'antd'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons'
import { galleryAdminApi } from '../../api/gallery'
import { CATEGORY_OPTIONS, CATEGORY_LABELS } from '../../constants/categories'
import type { GalleryImage } from '../../types/gallery'
import type { ItemCategory } from '../../types/inventory'

const DEFAULT_CATEGORY: ItemCategory = 'COSTUME'

function ImageCard({ image }: { image: GalleryImage }) {
  const queryClient = useQueryClient()
  const [caption, setCaption] = useState(image.caption ?? '')

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin-gallery'] })

  const updateCaption = useMutation({
    mutationFn: () => galleryAdminApi.update(image.id, { caption }),
    onSuccess: () => { message.success('Caption saved'); invalidate() },
    onError: () => message.error('Failed to save caption'),
  })

  const toggleActive = useMutation({
    mutationFn: (isActive: boolean) => galleryAdminApi.update(image.id, { isActive }),
    onSuccess: () => invalidate(),
    onError: () => message.error('Failed to update visibility'),
  })

  const remove = useMutation({
    mutationFn: () => galleryAdminApi.remove(image.id),
    onSuccess: () => { message.success('Image deleted'); invalidate() },
    onError: () => message.error('Failed to delete image'),
  })

  const captionDirty = caption !== (image.caption ?? '')

  return (
    <Card
      size="small"
      styles={{ body: { padding: 12 } }}
      cover={
        <div style={{ aspectRatio: '1', overflow: 'hidden', background: '#f5f5f5' }}>
          <Image
            src={image.thumbnailUrl}
            preview={{ src: image.imageUrl }}
            width="100%"
            height="100%"
            style={{ objectFit: 'cover', opacity: image.isActive ? 1 : 0.45 }}
          />
        </div>
      }
    >
      {!image.isActive && (
        <Tag color="default" style={{ marginBottom: 8 }}>Hidden</Tag>
      )}

      <Input.TextArea
        value={caption}
        onChange={e => setCaption(e.target.value)}
        placeholder="Add a caption"
        maxLength={2000}
        autoSize={{ minRows: 1, maxRows: 3 }}
        aria-label="Caption"
        style={{ marginBottom: 8 }}
      />
      <Button
        size="small"
        type="link"
        disabled={!captionDirty}
        loading={updateCaption.isPending}
        onClick={() => updateCaption.mutate()}
        style={{ padding: 0, marginBottom: 8 }}
      >
        Save caption
      </Button>

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Switch
            size="small"
            checked={image.isActive}
            loading={toggleActive.isPending}
            onChange={checked => toggleActive.mutate(checked)}
            aria-label={image.isActive ? 'Hide image' : 'Show image'}
          />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {image.isActive ? 'Visible' : 'Hidden'}
          </Typography.Text>
        </span>
        <Popconfirm
          title="Delete this image?"
          okText="Delete"
          okButtonProps={{ danger: true }}
          onConfirm={() => remove.mutate()}
        >
          <Button
            danger
            size="small"
            type="text"
            icon={<DeleteOutlined />}
            loading={remove.isPending}
            aria-label="Delete image"
          />
        </Popconfirm>
      </div>
    </Card>
  )
}

export default function GalleryManagePage() {
  const [category, setCategory] = useState<ItemCategory>(DEFAULT_CATEGORY)
  const [uploading, setUploading] = useState(false)
  const queryClient = useQueryClient()

  const { data: images, isLoading } = useQuery({
    queryKey: ['admin-gallery', category],
    queryFn: () => galleryAdminApi.list(category),
  })

  async function handleUpload(files: File[]) {
    setUploading(true)
    try {
      await galleryAdminApi.upload(category, files)
      message.success(files.length > 1 ? `${files.length} images uploaded` : 'Image uploaded')
      queryClient.invalidateQueries({ queryKey: ['admin-gallery'] })
    } catch {
      message.error('Failed to upload images')
    } finally {
      setUploading(false)
    }
  }

  return (
    <div>
      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center', marginBottom: 24 }}>
        <Select<ItemCategory>
          value={category}
          onChange={setCategory}
          options={CATEGORY_OPTIONS}
          style={{ width: 200 }}
          aria-label="Category"
        />
        <Upload
          accept="image/*"
          multiple
          showUploadList={false}
          disabled={uploading}
          beforeUpload={(_file, fileList) => {
            // AntD calls beforeUpload once per file; upload the whole batch on the
            // last call so a multi-select becomes a single request.
            const isLast = _file === fileList[fileList.length - 1]
            if (isLast) handleUpload(fileList as unknown as File[])
            return false
          }}
        >
          <Button type="primary" icon={<PlusOutlined />} loading={uploading}>
            Upload to {CATEGORY_LABELS.get(category)}
          </Button>
        </Upload>
      </div>

      {isLoading && (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}>
          <Spin size="large" />
        </div>
      )}

      {!isLoading && (images?.length ?? 0) === 0 && (
        <Empty description="No images in this category yet" />
      )}

      {!isLoading && (images?.length ?? 0) > 0 && (
        <Row gutter={[16, 16]}>
          {images!.map(image => (
            <Col key={image.id} xs={12} sm={8} md={6} lg={6} xl={4}>
              <ImageCard image={image} />
            </Col>
          ))}
        </Row>
      )}
    </div>
  )
}
