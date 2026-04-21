import { useState } from 'react'
import { Upload, Button, message, Typography } from 'antd'
import { DeleteOutlined, PlusOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons'
import { ShopOutlined } from '@ant-design/icons'
import type { ItemPhoto } from '../../../types/inventory'
import { itemsApi } from '../../../api/items'

interface Props {
  itemId: string
  photos: ItemPhoto[]
  onPhotosChange: (photos: ItemPhoto[]) => void
}

const MAX_PHOTOS = 8

export default function PhotoManager({ itemId, photos, onPhotosChange }: Props) {
  const [activeIndex, setActiveIndex] = useState(0)
  const [uploading, setUploading] = useState(false)

  // Keep activeIndex in bounds when photos change
  const safeIndex = photos.length === 0 ? 0 : Math.min(activeIndex, photos.length - 1)

  function prev() {
    setActiveIndex(i => (i === 0 ? photos.length - 1 : i - 1))
  }

  function next() {
    setActiveIndex(i => (i === photos.length - 1 ? 0 : i + 1))
  }

  async function handleUpload(file: File) {
    if (photos.length >= MAX_PHOTOS) {
      message.warning(`Maximum ${MAX_PHOTOS} photos allowed per item`)
      return false
    }
    setUploading(true)
    try {
      const newPhoto = await itemsApi.uploadPhoto(itemId, file)
      const updated = [...photos, newPhoto]
      onPhotosChange(updated)
      setActiveIndex(updated.length - 1)
      message.success('Photo uploaded')
    } catch {
      message.error('Failed to upload photo')
    } finally {
      setUploading(false)
    }
    return false
  }

  async function handleDelete(index: number) {
    const photo = photos[index]
    try {
      await itemsApi.deletePhoto(itemId, photo.id)
      const updated = photos.filter((_, i) => i !== index)
      onPhotosChange(updated)
      setActiveIndex(i => Math.min(i, Math.max(0, updated.length - 1)))
      message.success('Photo deleted')
    } catch {
      message.error('Failed to delete photo')
    }
  }

  const coverPhoto = photos[safeIndex] ?? null

  return (
    <div>
      {/* Cover photo */}
      <div
        style={{
          position: 'relative',
          width: '100%',
          aspectRatio: '4 / 3',
          background: '#f0f0f0',
          borderRadius: 8,
          overflow: 'hidden',
          marginBottom: 10,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {coverPhoto ? (
          <img
            src={coverPhoto.url}
            alt="cover"
            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
          />
        ) : (
          <ShopOutlined style={{ fontSize: 64, color: '#bbb' }} />
        )}

        {/* Prev / Next arrows — only when 2+ photos */}
        {photos.length > 1 && (
          <>
            <Button
              shape="circle"
              icon={<LeftOutlined />}
              onClick={prev}
              size="small"
              style={{
                position: 'absolute',
                left: 8,
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
                right: 8,
                top: '50%',
                transform: 'translateY(-50%)',
                background: 'rgba(0,0,0,0.45)',
                border: 'none',
                color: '#fff',
              }}
            />
            {/* Counter badge */}
            <div
              style={{
                position: 'absolute',
                bottom: 8,
                right: 10,
                background: 'rgba(0,0,0,0.45)',
                color: '#fff',
                fontSize: 12,
                padding: '2px 8px',
                borderRadius: 10,
              }}
            >
              {safeIndex + 1} / {photos.length}
            </div>
          </>
        )}

        {/* Delete button on cover */}
        {coverPhoto && (
          <Button
            danger
            size="small"
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(safeIndex)}
            style={{
              position: 'absolute',
              top: 8,
              right: 8,
              background: 'rgba(255,255,255,0.85)',
              border: 'none',
            }}
          />
        )}
      </div>

      {/* Thumbnail strip */}
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        {photos.map((photo, i) => (
          <div
            key={photo.id}
            onClick={() => setActiveIndex(i)}
            style={{
              width: 56,
              height: 56,
              borderRadius: 6,
              overflow: 'hidden',
              cursor: 'pointer',
              border: i === safeIndex ? '2px solid #1677ff' : '2px solid transparent',
              flexShrink: 0,
            }}
          >
            <img
              src={photo.thumbnailUrl}
              alt={`photo ${i + 1}`}
              style={{ width: '100%', height: '100%', objectFit: 'cover' }}
            />
          </div>
        ))}

        {/* Upload button in thumbnail strip */}
        {photos.length < MAX_PHOTOS && (
          <Upload
            accept="image/*"
            showUploadList={false}
            beforeUpload={handleUpload}
            disabled={uploading}
          >
            <div
              style={{
                width: 56,
                height: 56,
                border: '1px dashed #d9d9d9',
                borderRadius: 6,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                cursor: 'pointer',
                background: '#fafafa',
              }}
            >
              <PlusOutlined style={{ fontSize: 16 }} />
              <Typography.Text style={{ fontSize: 10 }}>Add</Typography.Text>
            </div>
          </Upload>
        )}
      </div>

      {photos.length >= MAX_PHOTOS && (
        <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 6 }}>
          Maximum {MAX_PHOTOS} photos reached
        </Typography.Text>
      )}
    </div>
  )
}
