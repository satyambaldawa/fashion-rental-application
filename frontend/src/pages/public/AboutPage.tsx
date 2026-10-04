import { Button, Card, Carousel, Col, Rate, Row, Space, Typography } from 'antd'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  EnvironmentOutlined,
  ClockCircleOutlined,
  PhoneOutlined,
  WhatsAppOutlined,
  InstagramOutlined,
  PictureOutlined,
  StarOutlined,
  LeftOutlined,
  RightOutlined,
} from '@ant-design/icons'
import PageHeader from '../../components/common/PageHeader'
import ItemPhotoPlaceholder from '../../components/common/ItemPhotoPlaceholder'
import { galleryApi } from '../../api/gallery'
import { reviewsApi } from '../../api/reviews'
import { CATEGORY_LABELS } from '../../constants/categories'

const { Title, Paragraph, Text } = Typography

const PHONE_DISPLAY = '+91 77200 01926'
const WHATSAPP_URL =
  'https://wa.me/917720001926?text=' +
  encodeURIComponent("Hi! I'd like to know more about renting an outfit from Manisha's Drapery.")
const INSTAGRAM_URL = 'https://www.instagram.com/manishasdrapery/'
const MAPS_URL = 'https://maps.app.goo.gl/xR1Rjn4QxWJ3PKeQ8'
// Coordinates resolved from the shop's Maps share link above, used for the embedded preview.
const MAPS_EMBED_URL = 'https://www.google.com/maps?q=18.6486092,73.7511599&z=16&output=embed'

const CAROUSEL_SIZE = 8
const REVIEW_PREVIEW_SIZE = 2

const HOURS: { day: string; ranges: string }[] = [
  { day: 'Monday', ranges: '10:00 AM – 1:00 PM, 5:00 PM – 9:00 PM' },
  { day: 'Tuesday', ranges: '10:00 AM – 1:00 PM, 5:00 PM – 9:00 PM' },
  { day: 'Wednesday', ranges: '10:00 AM – 1:00 PM, 5:00 PM – 9:00 PM' },
  { day: 'Thursday', ranges: '10:00 AM – 1:00 PM, 5:00 PM – 9:00 PM' },
  { day: 'Friday', ranges: '10:00 AM – 1:00 PM, 5:00 PM – 9:00 PM' },
  { day: 'Saturday', ranges: '10:00 AM – 1:00 PM, 5:00 PM – 9:00 PM' },
  { day: 'Sunday', ranges: '5:00 PM – 9:00 PM' },
]

// Curated by the owner — one hand-picked photo per bucket (public/about/), not
// pulled dynamically from the gallery, so the card shown here is always intentional.
const OFFER_BUCKETS: { title: string; blurb: string; image: string }[] = [
  {
    title: 'Navratri & Festive Wear',
    blurb:
      'From vibrant Navratri and Garba outfits to festive and wedding wear, dress up for every celebration without the cost of buying new.',
    image: '/about/offer-navratri-festive.jpg',
  },
  {
    title: 'Costumes for Every Occasion',
    blurb:
      'Traditional, mythological, freedom-fighter and profession-themed costumes — perfect for school events, plays, and fancy-dress competitions.',
    image: '/about/offer-traditional.jpg',
  },
  {
    title: 'Dress-Up Essentials',
    blurb:
      'A wide range of costumes, dresses, pagdis, accessories and ornaments to complete any look, for any occasion.',
    image: '/about/offer-dressup.jpg',
  },
  {
    title: "Kids' Drapery",
    blurb:
      "Sizes and styles for kids too, across all our collections — so the little ones can join in the celebration.",
    image: '/about/offer-kids-drapery.jpg',
  },
]

function SectionTitle({ children }: { children: string }) {
  return (
    <Title level={4} style={{ color: '#6E0B37', marginBottom: 12 }}>
      {children}
    </Title>
  )
}

function BrandButton({ href, icon, children }: { href: string; icon: React.ReactNode; children: string }) {
  return (
    <Button
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      icon={icon}
      style={{
        background: '#6E0B37',
        borderColor: '#6E0B37',
        color: '#fff',
        fontFamily: '"Jost", system-ui, sans-serif',
        fontWeight: 500,
        borderRadius: 8,
      }}
    >
      {children}
    </Button>
  )
}

// react-slick clones this with onClick (and a few DOM props) when passed as
// Carousel's nextArrow/prevArrow — the direction prop controls icon + side only.
function CarouselArrow({
  direction,
  onClick,
}: {
  direction: 'prev' | 'next'
  onClick?: React.MouseEventHandler<HTMLButtonElement>
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={direction === 'prev' ? 'Previous image' : 'Next image'}
      style={{
        position: 'absolute',
        top: '50%',
        [direction === 'prev' ? 'left' : 'right']: 12,
        transform: 'translateY(-50%)',
        zIndex: 2,
        width: 40,
        height: 40,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'rgba(20, 8, 13, 0.75)',
        color: '#fff',
        border: 'none',
        borderRadius: 8,
        cursor: 'pointer',
      }}
    >
      {direction === 'prev' ? <LeftOutlined /> : <RightOutlined />}
    </button>
  )
}

function HoursList() {
  const today = new Date().toLocaleDateString('en-US', { weekday: 'long' })
  return (
    <div>
      <Text strong style={{ color: '#33101F', display: 'block', marginBottom: 8 }}>
        <ClockCircleOutlined style={{ color: '#A81259', marginRight: 8 }} />
        Hours
      </Text>
      {HOURS.map(({ day, ranges }) => {
        const isToday = day === today
        return (
          <div
            key={day}
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              gap: 12,
              padding: '3px 0',
            }}
          >
            <Text strong={isToday} style={{ color: isToday ? '#33101F' : undefined }}>
              {day}
            </Text>
            <Text type={isToday ? undefined : 'secondary'} strong={isToday}>
              {ranges}
            </Text>
          </div>
        )
      })}
    </div>
  )
}

export default function AboutPage() {
  const { data: images } = useQuery({
    queryKey: ['public-gallery', 'about'],
    queryFn: () => galleryApi.list(),
  })
  const { data: reviewsPage } = useQuery({
    queryKey: ['public-reviews', 'about'],
    queryFn: () => reviewsApi.listPublic({ sort: 'HIGHEST_RATED', page: 0 }),
  })

  const carouselImages = (images ?? []).slice(0, CAROUSEL_SIZE)
  const previewReviews = (reviewsPage?.content ?? []).slice(0, REVIEW_PREVIEW_SIZE)

  return (
    <>
      <PageHeader label="About" title="About" accent="Manisha's Drapery" />

      <Row gutter={[40, 24]} align="stretch" style={{ marginBottom: 40 }}>
        <Col xs={24} md={16}>
          <Paragraph style={{ fontSize: 16, lineHeight: 1.9, marginBottom: 20 }}>
            For twelve years, Manisha's Fancy Collection has been a familiar name in the
            neighbourhood — the oldest clothing store around, trusted for its honest pricing,
            genuine care, and quality that keeps customers coming back. What started as a small,
            home-based sari and kurti shop has grown, year after year, into a store that dresses
            the whole family — from everyday sari-kurtis and western wear to the essentials every
            woman and child needs.
          </Paragraph>
          <Paragraph style={{ fontSize: 16, lineHeight: 1.9, marginBottom: 0 }}>
            Now, Manisha is bringing that same trust to something new: Manisha's Drapery, a
            dedicated rental venture for costumes and festive outfits — Navratri wear, wedding and
            festival costumes, and everything in between. It's the same dedication that built the
            shop, now reimagined for a new way of dressing up: rent, celebrate, return.
          </Paragraph>
        </Col>
        <Col xs={24} md={8}>
          <div style={{ width: '100%', height: '100%', minHeight: 240, borderRadius: 12, overflow: 'hidden' }}>
            <img
              src="/about/shop-front.jpg"
              alt="Manisha's Fancy Collection storefront in Ravet"
              style={{ width: '100%', height: '100%', objectFit: 'cover' }}
            />
          </div>
        </Col>
      </Row>

      <div style={{ marginBottom: 40 }}>
        <SectionTitle>What We Offer</SectionTitle>
        <Row gutter={[16, 16]} align="stretch">
          {OFFER_BUCKETS.map(bucket => (
            <Col key={bucket.title} xs={12} md={6} style={{ display: 'flex' }}>
              <Card
                style={{ width: '100%' }}
                styles={{ body: { padding: 16 } }}
                cover={
                  <div style={{ width: '100%', aspectRatio: '3/4', borderRadius: '14px 14px 0 0', overflow: 'hidden' }}>
                    <img
                      src={bucket.image}
                      alt={bucket.title}
                      style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                    />
                  </div>
                }
              >
                <Card.Meta
                  title={<span style={{ whiteSpace: 'normal' }}>{bucket.title}</span>}
                  description={bucket.blurb}
                />
              </Card>
            </Col>
          ))}
        </Row>
      </div>

      <div style={{ marginBottom: 40 }}>
        <SectionTitle>Visit Us</SectionTitle>
        <Paragraph>
          Manisha's Drapery operates out of Manisha's Fancy Collection, Pipeline Road, near
          Ganpati Mandir, Shinde Vasti, Ravet.
        </Paragraph>
        <Row gutter={[24, 24]}>
          <Col xs={24} md={12}>
            <div style={{ borderRadius: 12, overflow: 'hidden', lineHeight: 0 }}>
              <iframe
                title="Manisha's Drapery location"
                src={MAPS_EMBED_URL}
                width="100%"
                height={260}
                style={{ border: 0 }}
                loading="lazy"
                referrerPolicy="no-referrer-when-downgrade"
              />
            </div>
            <div style={{ marginTop: 12 }}>
              <BrandButton href={MAPS_URL} icon={<EnvironmentOutlined />}>
                Get Directions
              </BrandButton>
            </div>
          </Col>
          <Col xs={24} md={12}>
            <HoursList />
          </Col>
        </Row>
      </div>

      <div style={{ marginBottom: 40 }}>
        <SectionTitle>Reach Out</SectionTitle>
        <Paragraph>
          <PhoneOutlined style={{ color: '#A81259', marginRight: 8 }} />
          <a href="tel:+917720001926" style={{ color: '#33101F' }}>
            {PHONE_DISPLAY}
          </a>
        </Paragraph>
        <Space wrap size={12}>
          <BrandButton href={WHATSAPP_URL} icon={<WhatsAppOutlined />}>
            Chat on WhatsApp
          </BrandButton>
          <BrandButton href={INSTAGRAM_URL} icon={<InstagramOutlined />}>
            Follow on Instagram
          </BrandButton>
        </Space>
      </div>

      <div style={{ marginBottom: 40 }}>
        <SectionTitle>Our Collection</SectionTitle>
        {carouselImages.length > 0 ? (
          <div className="collection-carousel" style={{ maxWidth: 1100, margin: '0 auto 16px' }}>
            <Carousel
              autoplay
              autoplaySpeed={5000}
              centerMode
              centerPadding="0px"
              slidesToShow={3}
              dots={false}
              arrows
              prevArrow={<CarouselArrow direction="prev" />}
              nextArrow={<CarouselArrow direction="next" />}
              responsive={[
                // Three full-width cards get too small to read below tablet width —
                // fall back to one centered card with a partial peek on each side.
                { breakpoint: 768, settings: { slidesToShow: 1, centerPadding: '18%' } },
              ]}
            >
              {carouselImages.map(image => (
                <div key={image.id} style={{ padding: '0 8px' }}>
                  <div
                    style={{
                      position: 'relative',
                      width: '100%',
                      aspectRatio: '3/4',
                      borderRadius: 16,
                      overflow: 'hidden',
                      boxShadow: '0 16px 32px rgba(110, 11, 55, 0.22)',
                    }}
                  >
                    <img
                      src={image.imageUrl}
                      alt={image.caption ?? CATEGORY_LABELS.get(image.category) ?? 'From our collection'}
                      style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                    />
                    <div
                      style={{
                        position: 'absolute',
                        inset: 0,
                        background: 'linear-gradient(180deg, rgba(20,8,13,0.55) 0%, rgba(20,8,13,0) 35%)',
                      }}
                    />
                    <Text
                      strong
                      style={{
                        position: 'absolute',
                        top: 12,
                        left: 14,
                        color: '#fff',
                        fontFamily: '"Jost", system-ui, sans-serif',
                      }}
                    >
                      {image.caption ?? CATEGORY_LABELS.get(image.category) ?? 'From our collection'}
                    </Text>
                  </div>
                </div>
              ))}
            </Carousel>
          </div>
        ) : (
          <div style={{ width: '100%', maxWidth: 200, aspectRatio: '3/4', marginBottom: 16 }}>
            <ItemPhotoPlaceholder />
          </div>
        )}
        <Link to="/gallery">
          <Button
            icon={<PictureOutlined />}
            style={{ fontFamily: '"Jost", system-ui, sans-serif', fontWeight: 500, borderRadius: 8 }}
          >
            View Full Gallery
          </Button>
        </Link>
      </div>

      <div>
        <SectionTitle>What Customers Say</SectionTitle>
        {previewReviews.length > 0 && (
          <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
            {previewReviews.map(review => (
              <Col key={review.id} xs={24} sm={12}>
                <div style={{ background: '#fff', border: '1px solid #eed6e0', borderRadius: 12, padding: 16, height: '100%' }}>
                  <Space align="center" size={8} wrap style={{ marginBottom: 6 }}>
                    <Text strong style={{ color: '#33101F' }}>
                      {review.reviewerName}
                    </Text>
                    <Rate disabled value={review.rating} style={{ fontSize: 13 }} />
                  </Space>
                  <Paragraph type="secondary" ellipsis={{ rows: 2 }} style={{ marginBottom: 0 }}>
                    {review.reviewText}
                  </Paragraph>
                </div>
              </Col>
            ))}
          </Row>
        )}
        <Link to="/reviews">
          <Button
            icon={<StarOutlined />}
            style={{ fontFamily: '"Jost", system-ui, sans-serif', fontWeight: 500, borderRadius: 8 }}
          >
            Read All Reviews
          </Button>
        </Link>
      </div>
    </>
  )
}
