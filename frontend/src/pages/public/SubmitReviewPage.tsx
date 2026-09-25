import { useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { Alert, Button, Card, Form, Input, Rate, Result, Upload } from 'antd'
import type { UploadFile, UploadProps } from 'antd'
import { reviewsApi } from '../../api/reviews'
import PageHeader from '../../components/common/PageHeader'
import { ErrorMessage } from '../../components/common/ErrorMessage'
import type { ApiResponse } from '../../types/api'
import type { SubmitReviewRequest } from '../../types/review'

const MAX_IMAGES = 3
const MAX_IMAGE_BYTES = 5 * 1024 * 1024
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const INDIAN_MOBILE_PATTERN = /^[6-9]\d{9}$/
const GENERIC_SUBMIT_ERROR = 'Could not submit your review. Please try again.'

function submissionErrorMessage(error: unknown): string {
  if (isAxiosError<ApiResponse<unknown>>(error) && error.response?.data?.error) {
    return error.response.data.error
  }
  return GENERIC_SUBMIT_ERROR
}

export default function SubmitReviewPage() {
  const [form] = Form.useForm<SubmitReviewRequest>()
  const [fileList, setFileList] = useState<UploadFile[]>([])
  const [fileError, setFileError] = useState<string | null>(null)
  // submission.isPending only reflects the last render, so a second Enter/click
  // inside that render's window can still slip past it — this ref is set
  // synchronously, before React even schedules the re-render.
  const isSubmitting = useRef(false)

  const submission = useMutation({
    mutationFn: (request: SubmitReviewRequest) =>
      reviewsApi.submit(request, fileList.flatMap(f => (f.originFileObj ? [f.originFileObj] : []))),
    onSettled: () => {
      isSubmitting.current = false
    },
  })

  const holdFileForSubmit: UploadProps['beforeUpload'] = file => {
    if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
      setFileError('Only JPEG, PNG, and WebP photos are allowed')
      return Upload.LIST_IGNORE
    }
    if (file.size > MAX_IMAGE_BYTES) {
      setFileError('Each photo must be smaller than 5MB')
      return Upload.LIST_IGNORE
    }
    setFileError(null)
    // Returning false stops AntD uploading on its own; the file is held and sent with the form.
    return false
  }

  if (submission.isSuccess) {
    return (
      <Result
        status="success"
        title="Thank you for your review"
        subTitle="It will appear on our reviews page once it has been approved."
      />
    )
  }

  return (
    <div>
      <PageHeader label="Reviews" title="Leave a" accent="Review" />

      <Card style={{ maxWidth: 560 }}>
        {submission.isError && (
          <div style={{ marginBottom: 16 }}>
            <ErrorMessage message={submissionErrorMessage(submission.error)} />
          </div>
        )}

        <Form
          form={form}
          layout="vertical"
          onFinish={values => {
            if (isSubmitting.current) return
            isSubmitting.current = true
            submission.mutate(values)
          }}
        >
          <Form.Item
            name="reviewerName"
            label="Your name"
            rules={[{ required: true, whitespace: true, message: 'Name is required' }]}
          >
            <Input maxLength={80} autoComplete="name" placeholder="e.g. Priya S" />
          </Form.Item>

          <Form.Item
            name="phone"
            label="Mobile number"
            extra="We use this only to verify your rental. It is never shown publicly."
            rules={[
              { required: true, message: 'Mobile number is required' },
              { pattern: INDIAN_MOBILE_PATTERN, message: 'Enter a valid 10-digit Indian mobile number' },
            ]}
          >
            <Input maxLength={10} inputMode="numeric" autoComplete="tel-national" />
          </Form.Item>

          <Form.Item
            name="itemDescription"
            label="What did you rent?"
            rules={[{ required: true, whitespace: true, message: 'Tell us what you rented' }]}
          >
            <Input maxLength={100} placeholder="e.g. Red bridal lehenga" />
          </Form.Item>

          <Form.Item
            name="rating"
            label="Rating"
            rules={[{ required: true, message: 'Please give a rating' }]}
          >
            <Rate />
          </Form.Item>

          <Form.Item
            name="reviewText"
            label="Your review"
            rules={[{ required: true, whitespace: true, message: 'Review cannot be empty' }]}
          >
            <Input.TextArea rows={4} maxLength={256} showCount />
          </Form.Item>

          <Form.Item label="Photos (optional, up to 3)">
            <Upload
              listType="picture-card"
              accept={ALLOWED_IMAGE_TYPES.join(',')}
              maxCount={MAX_IMAGES}
              fileList={fileList}
              beforeUpload={holdFileForSubmit}
              onChange={({ fileList: next }) => setFileList(next)}
            >
              {fileList.length < MAX_IMAGES && <div>+ Add photo</div>}
            </Upload>
            {fileError && <Alert type="error" message={fileError} showIcon />}
          </Form.Item>

          <Button type="primary" htmlType="submit" loading={submission.isPending}>
            Submit review
          </Button>
        </Form>
      </Card>
    </div>
  )
}
