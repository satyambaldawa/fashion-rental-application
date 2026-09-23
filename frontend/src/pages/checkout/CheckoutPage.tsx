import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery, useMutation } from '@tanstack/react-query'
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Col,
  Descriptions,
  Divider,
  Input,
  InputNumber,
  message,
  Modal,
  Pagination,
  Popover,
  Row,
  Space,
  Grid,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd'
import {
  ShoppingCartOutlined,
  PlusOutlined,
  MinusOutlined,
  DeleteOutlined,
} from '@ant-design/icons'
import ItemPhotoPlaceholder from '../../components/common/ItemPhotoPlaceholder'
import PageHeader from '../../components/common/PageHeader'
import dayjs from 'dayjs'
import type { Dayjs } from 'dayjs'
import DatePicker from 'antd/es/date-picker'
import { useCart } from '../../hooks/useCart'
import { toApiDatetime } from '../../utils/datetime'
import { itemsApi } from '../../api/items'
import { receiptsApi } from '../../api/receipts'
import CustomerSearch from '../../components/common/CustomerSearch'
import { customersApi } from '../../api/customers'
import type { CustomerSummary } from '../../types/customer'
import type { ItemSummary } from '../../types/inventory'
import type {
  CartItem,
  CatalogueCartItem,
  AdHocCartItem,
  CheckoutPreviewRequest,
  CheckoutRequest,
} from '../../types/receipt'
import { formatCurrency } from '../../utils/currency'
import ItemBrowseModal from './ItemBrowseModal'
import AdHocItemModal from './AdHocItemModal'
import { lineRentOf, perDayRateOf, MAX_AD_HOC_QUANTITY } from './cartPricing'
import { useAuth } from '../../hooks/useAuth'
import { CATEGORY_OPTIONS } from '../../constants/categories'

type Screen = 'home' | 'browse' | 'preview' | 'customer'

const LARGE_DISCOUNT_WARNING_RATIO = 0.9

const CATEGORY_CHIP_OPTIONS: { label: string; value: string | undefined }[] = [
  { label: 'All', value: undefined },
  ...CATEGORY_OPTIONS,
]

const CHIP_FOCUS_STYLE = `
  .checkout-chip:focus-visible {
    outline: 2px solid #A81259;
    outline-offset: 2px;
  }
`

function injectChipFocusStyleOnce() {
  if (typeof document !== 'undefined' && !document.getElementById('checkout-chip-focus-style')) {
    const style = document.createElement('style')
    style.id = 'checkout-chip-focus-style'
    style.textContent = CHIP_FOCUS_STYLE
    document.head.appendChild(style)
  }
}

export default function CheckoutPage() {
  injectChipFocusStyleOnce()

  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const screens = Grid.useBreakpoint()
  const isMobile = !screens.lg
  const { isOwner } = useAuth()
  const {
    cart, createCart, addItem, removeItem, updateQuantity, applyCoupon, removeCoupon, clearCart,
    droppedCouponCode,
  } = useCart()

  const [screen, setScreen] = useState<Screen>(cart ? 'browse' : 'home')

  // Customer selection — declared before the useEffect that references setSelectedCustomer
  const [selectedCustomer, setSelectedCustomer] = useState<CustomerSummary | null>(null)

  // Auto-select customer returned from registration page
  useEffect(() => {
    const newCustomerId = searchParams.get('newCustomerId')
    if (newCustomerId) {
      customersApi.get(newCustomerId).then(customer => {
        setSelectedCustomer({
          id: customer.id,
          name: customer.name,
          phone: customer.phone,
          address: customer.address,
          customerType: customer.customerType,
          organizationName: customer.organizationName,
          activeRentalsCount: 0,
        })
        setScreen('customer')
      }).catch(() => {})
      setSearchParams({}, { replace: true })
    }
  }, [searchParams, setSearchParams])

  // Create cart modal
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [useNow, setUseNow] = useState(true)
  const [startPicker, setStartPicker] = useState<Dayjs | null>(null)
  const [rentalDays, setRentalDays] = useState(1)
  const [createError, setCreateError] = useState<string | null>(null)

  // Browse screen
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState<string | undefined>()
  const [itemSize, setItemSize] = useState('')
  const [itemType, setItemType] = useState<'INDIVIDUAL' | 'PACKAGE' | undefined>(undefined)
  const [browsePage, setBrowsePage] = useState(0)
  const [packageModal, setPackageModal] = useState<ItemSummary | null>(null)
  const [showAdHocModal, setShowAdHocModal] = useState(false)

  // Preview / confirm
  const [conflictError, setConflictError] = useState<string | null>(null)

  // Coupon
  const [couponInput, setCouponInput] = useState('')
  const [couponError, setCouponError] = useState<string | null>(null)

  // useCart's mutators silently null out cart.appliedCoupon on any cart edit (the discount
  // is a function of the subtotal, so a stale one must never be trusted) — but that alone
  // leaves the coupon code still sitting in the (now-collapsed) input, which reads as
  // "still applied" even though buildRequest will now send couponCode: null. Surface it.
  // The check against the previously-applied code (not just "went to null") is what tells
  // this apart from an explicit Remove click: handleRemoveCoupon clears couponInput in the
  // same batch as removeCoupon(), so by the time this effect runs the input no longer
  // matches, and the warning is correctly suppressed for a deliberate removal.
  const lastAppliedCouponCode = useRef<string | null>(null)
  useEffect(() => {
    const current = cart?.appliedCoupon?.couponCode ?? null
    const previous = lastAppliedCouponCode.current
    if (previous && !current && couponInput === previous) {
      message.warning(`Coupon ${previous} was removed because the cart changed — please re-apply it.`)
      setCouponInput('')
    }
    lastAppliedCouponCode.current = current
  }, [cart?.appliedCoupon?.couponCode, couponInput])

  // Covers the gap the effect above cannot: CheckoutPage fully unmounts on navigation to
  // /customers/register (a separate route, not a screen within this component), so a coupon
  // dropped by useCart's rehydration guard on the return trip has no "previous" render to
  // compare against — only useCart itself, which ran loadCart() before this component's
  // effects ever existed, knows a coupon just vanished. Surfaced once per mount.
  useEffect(() => {
    if (droppedCouponCode) {
      message.warning(`Coupon ${droppedCouponCode} was not carried over — please re-apply it.`)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- droppedCouponCode is fixed for this mount; fire once
  }, [])

  // Items query — only runs when cart exists and we're browsing/previewing
  const { data: itemsPage, isLoading: itemsLoading } = useQuery({
    queryKey: ['items-for-cart', search, category, itemSize, itemType, browsePage, cart?.startDatetime, cart?.endDatetime],
    queryFn: () => itemsApi.list({
      search: search || undefined,
      category: category || undefined,
      itemSize: itemSize || undefined,
      itemType: itemType || undefined,
      page: browsePage,
      size: 20,
      startDatetime: cart!.startDatetime,
      endDatetime: cart!.endDatetime,
    }),
    enabled: !!cart,
  })

  // Fresh item data for the preview screen — no search/category filter so all cart items are included.
  // Needed because localStorage cart items may be missing category/size/componentNames (stale data).
  const { data: previewItemsPage } = useQuery({
    queryKey: ['items-for-preview', cart?.startDatetime, cart?.endDatetime],
    queryFn: () => itemsApi.list({
      size: 200,
      startDatetime: cart!.startDatetime,
      endDatetime: cart!.endDatetime,
    }),
    enabled: !!cart && screen === 'preview',
  })

  // Only show items that are actually available for the cart's dates
  const availableItems = (itemsPage?.content ?? []).filter(i => i.availableQuantity > 0)

  const createMutation = useMutation({
    mutationFn: (req: CheckoutRequest) => receiptsApi.create(req),
    onSuccess: (receipt) => {
      clearCart()
      navigate(`/receipts/${receipt.id}`)
    },
    onError: (err: unknown) => {
      const apiErr = err as { response?: { data?: { error?: string } } }
      setConflictError(apiErr?.response?.data?.error ?? 'Failed to create receipt. Please try again.')
    },
  })

  // The server's preview endpoint is the only place the discount is computed — this UI
  // never reimplements the percent/floor rule, so it can't drift from what createReceipt
  // actually charges.
  //
  // The cart is not locked while this request is in flight — quantities can change, lines
  // can be added or removed — so the response is only trustworthy if the cart it was priced
  // against is still the cart on screen. mutationFn captures a signature of the cart at
  // dispatch time; onSuccess discards a response computed against a cart that has since
  // changed, rather than writing stale totals onto the current one.
  const previewMutation = useMutation({
    mutationFn: async (code: string) => {
      const signature = cartSignature() // captured before the request, not after
      const preview = await receiptsApi.preview(buildPreviewRequest(code))
      return { preview, signature }
    },
    onSuccess: ({ preview, signature }) => {
      if (signature !== cartSignature()) {
        setCouponError('The cart changed while applying this coupon — please apply it again.')
        return
      }
      if (!preview.couponCode) return
      const applyIt = () => {
        applyCoupon({
          couponCode: preview.couponCode!,
          discountAmount: preview.discountAmount,
          totalRent: preview.totalRent,
          totalDeposit: preview.totalDeposit,
          grandTotal: preview.grandTotal,
        })
        setCouponInput(preview.couponCode!)
        setCouponError(null)
      }
      // CouponDiscountResolver clamps the discount to the subtotal, so this ratio can reach
      // 1.0 but never exceed it — a typo'd FIXED value (e.g. "5000" meant to be "50") still
      // clamps to the full rent, which is exactly the case this exists to catch. A
      // legitimate 100%-off promotion also crosses this threshold; that's an acceptable
      // false positive; there's no backend hard cap to lean on instead, since a large
      // package subtotal shouldn't be artificially capped.
      if (preview.totalRent > 0
          && preview.discountAmount / preview.totalRent > LARGE_DISCOUNT_WARNING_RATIO) {
        Modal.confirm({
          title: 'Large discount',
          content: `This coupon discounts ${formatCurrency(preview.discountAmount)} off a ${formatCurrency(preview.totalRent)} rent subtotal. Apply it?`,
          okText: 'Apply anyway',
          onOk: applyIt,
        })
      } else {
        applyIt()
      }
    },
    onError: (err: unknown) => {
      const apiErr = err as { response?: { data?: { error?: string } } }
      setCouponError(apiErr?.response?.data?.error ?? 'Failed to apply coupon. Please try again.')
    },
  })

  // --- Create cart helpers ---

  function handleOpenCreateModal() {
    setUseNow(true)
    setStartPicker(null)
    setRentalDays(1)
    setCreateError(null)
    setShowCreateModal(true)
  }

  function handleConfirmCreate() {
    const start = useNow ? dayjs() : startPicker
    if (!start) {
      setCreateError('Please select a start date and time.')
      return
    }
    const end = start.add(rentalDays, 'day')
    createCart(toApiDatetime(start), toApiDatetime(end), rentalDays)
    setShowCreateModal(false)
    setScreen('browse')
  }

  // --- Cart actions ---

  function handleAddToCart(item: ItemSummary) {
    const cartItem: CartItem = {
      kind: 'CATALOGUE',
      lineKey: item.id,
      itemId: item.id,
      itemName: item.name,
      itemType: item.itemType,
      category: item.category,
      size: item.size,
      componentNames: item.componentNames ?? null,
      thumbnailUrl: item.thumbnailUrl,
      rate: item.rate,
      deposit: item.deposit,
      quantity: 1,
      availableQuantity: item.availableQuantity,
    }
    addItem(cartItem)
  }

  function handleDeleteCart() {
    clearCart()
    setScreen('home')
  }

  // --- Preview ---

  // Cheap fingerprint of "what was priced" — items, quantities, and dates. Used only to
  // detect whether the cart changed between dispatching a coupon preview request and its
  // response landing; never persisted or sent to the server.
  function cartSignature(): string {
    return JSON.stringify({ start: cart!.startDatetime, end: cart!.endDatetime, items: cart!.items })
  }

  function buildPreviewRequest(couponCode?: string): CheckoutPreviewRequest {
    return {
      startDatetime: cart!.startDatetime,
      endDatetime: cart!.endDatetime,
      items: cart!.items
        .filter((i): i is CatalogueCartItem => i.kind === 'CATALOGUE')
        .map(i => ({ itemId: i.itemId, quantity: i.quantity })),
      adHocItems: cart!.items
        .filter((i): i is AdHocCartItem => i.kind === 'ADHOC')
        .map(i => ({
          name: i.itemName,
          size: i.size,
          flatPrice: i.flatPrice,
          deposit: i.deposit,
          quantity: i.quantity,
        })),
      couponCode,
    }
  }

  function buildRequest(customerId: string): CheckoutRequest {
    return {
      customerId,
      startDatetime: cart!.startDatetime,
      endDatetime: cart!.endDatetime,
      items: cart!.items
        .filter((i): i is CatalogueCartItem => i.kind === 'CATALOGUE')
        .map(i => ({ itemId: i.itemId, quantity: i.quantity })),
      adHocItems: cart!.items
        .filter((i): i is AdHocCartItem => i.kind === 'ADHOC')
        .map(i => ({
          name: i.itemName,
          size: i.size,
          flatPrice: i.flatPrice,
          deposit: i.deposit,
          quantity: i.quantity,
        })),
      couponCode: cart!.appliedCoupon?.couponCode ?? null,
    }
  }

  function handleApplyCoupon() {
    if (!couponInput.trim()) return
    setCouponError(null)
    previewMutation.mutate(couponInput.trim())
  }

  function handleRemoveCoupon() {
    removeCoupon()
    setCouponInput('')
    setCouponError(null)
  }

  // Known, accepted gap: a non-owner submitting a cart with inherited ad-hoc lines (e.g. the
  // owner built one and logged out without checking out, on the shared tablet) gets a clean 400
  // from CheckoutService.hasOwnerRole() here, not silent corruption. isOwner already blocks
  // *creating* ad-hoc lines; blocking submission too was judged not worth the added state for now.
  function handleConfirmReceipt() {
    if (!selectedCustomer) return
    setConflictError(null)
    createMutation.mutate(buildRequest(selectedCustomer.id))
  }

  // --- Render helpers ---

  const cartCount = cart?.items.reduce((s, i) => s + i.quantity, 0) ?? 0
  const cartTotal = cart
    ? cart.items.reduce((s, i) => s + lineRentOf(i, cart.rentalDays) + i.deposit * i.quantity, 0)
    : 0

  // ---- HOME ----
  if (screen === 'home') {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', paddingTop: 80, gap: 16 }}>
        <ShoppingCartOutlined style={{ fontSize: 64, color: '#7a5361' }} />
        <h1 style={{
          fontFamily: '"Cormorant Garamond", Georgia, serif',
          fontWeight: 500,
          fontSize: 36,
          lineHeight: 1.1,
          color: '#33101F',
          margin: 0,
        }}>
          New <em style={{ color: '#A81259', fontStyle: 'italic' }}>Rental</em>
        </h1>
        <Typography.Text type="secondary">Start by creating a cart with the rental period.</Typography.Text>
        <Button type="primary" size="large" icon={<PlusOutlined />} onClick={handleOpenCreateModal}>
          Create New Cart
        </Button>

        <CreateCartModal
          open={showCreateModal}
          useNow={useNow}
          startPicker={startPicker}
          rentalDays={rentalDays}
          error={createError}
          onUseNowChange={setUseNow}
          onStartPickerChange={setStartPicker}
          onDaysChange={setRentalDays}
          onConfirm={handleConfirmCreate}
          onCancel={() => setShowCreateModal(false)}
        />
      </div>
    )
  }

  // ---- BROWSE ----
  if (screen === 'browse') {
    const packageModalCartLine = packageModal
      ? cart!.items.find(c => c.kind === 'CATALOGUE' && c.itemId === packageModal.id)
      : undefined

    return (
      <div style={{ paddingBottom: 100 }}>
        {/* Header */}
        <PageHeader
          label="New Rental"
          title="Browse"
          accent="Items"
          action={
            <Space>
              <Tag color="blue">{dayjs(cart!.startDatetime).format('DD MMM HH:mm')} → {dayjs(cart!.endDatetime).format('DD MMM HH:mm')}</Tag>
              <Tag color="purple">{cart!.rentalDays} day{cart!.rentalDays !== 1 ? 's' : ''}</Tag>
            </Space>
          }
        />

        {/* Filters */}
        <div style={{ marginBottom: 16 }}>
          {/* Category chips */}
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 12 }}>
            {CATEGORY_CHIP_OPTIONS.map(opt => {
              const isActive = (opt.value ?? undefined) === category
              return (
                <button
                  key={opt.label}
                  className="checkout-chip"
                  aria-pressed={isActive}
                  onClick={() => { setCategory(opt.value); setBrowsePage(0); }}
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
            <Input.Search
              placeholder="Search items..."
              style={{ width: 240, borderColor: '#eed6e0' }}
              value={search}
              onChange={e => { setSearch(e.target.value); setBrowsePage(0); }}
              allowClear
              onClear={() => { setSearch(''); setBrowsePage(0); }}
            />
            <Input
              placeholder="Size"
              value={itemSize}
              onChange={e => { setItemSize(e.target.value); setBrowsePage(0); }}
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
                  className="checkout-chip"
                  aria-pressed={isActive}
                  onClick={() => { setItemType(opt.value); setBrowsePage(0); }}
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
            {isOwner && (
              <Button icon={<PlusOutlined />} onClick={() => setShowAdHocModal(true)}>
                Add custom product
              </Button>
            )}
          </Space>
        </div>

        {itemsLoading && <div style={{ textAlign: 'center', padding: 40 }}><Spin /></div>}

        {!itemsLoading && availableItems.length === 0 && (
          <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>
            No items available for the selected period.
          </div>
        )}

        <Row gutter={[16, 16]}>
          {availableItems.map(item => {
            const inCart = cart!.items.find(c => c.kind === 'CATALOGUE' && c.itemId === item.id)
            return (
              <Col key={item.id} xs={24} sm={12} lg={8}>
                <Card
                  onClick={() => setPackageModal(item)}
                  style={{ cursor: 'pointer' }}
                  cover={
                    item.thumbnailUrl ? (
                      <img
                        src={item.thumbnailUrl}
                        alt={item.name}
                        style={{ width: '100%', aspectRatio: '3/4', objectFit: 'cover' }}
                      />
                    ) : (
                      <div style={{ width: '100%', aspectRatio: '3/4', overflow: 'hidden' }}>
                        <ItemPhotoPlaceholder />
                      </div>
                    )
                  }
                  actions={[
                    inCart ? (
                      <div
                        style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 12 }}
                        key="qty"
                        onClick={e => e.stopPropagation()}
                      >
                        <Button
                          size="small"
                          icon={<MinusOutlined />}
                          onClick={() => inCart.quantity === 1 ? removeItem(inCart.lineKey) : updateQuantity(inCart.lineKey, inCart.quantity - 1)}
                        />
                        <Typography.Text strong style={{ minWidth: 20, textAlign: 'center' }}>
                          {inCart.quantity}
                        </Typography.Text>
                        <Button
                          size="small"
                          icon={<PlusOutlined />}
                          disabled={inCart.quantity >= item.availableQuantity}
                          onClick={() => updateQuantity(inCart.lineKey, inCart.quantity + 1)}
                        />
                      </div>
                    ) : (
                      <Button
                        type="primary"
                        ghost
                        icon={<PlusOutlined />}
                        onClick={(e) => { e.stopPropagation(); handleAddToCart(item) }}
                        key="add"
                      >
                        Add to Cart
                      </Button>
                    ),
                  ]}
                >
                  <Card.Meta
                    title={item.name}
                    description={
                      <Space direction="vertical" size={4} style={{ width: '100%' }}>
                        <Space>
                          <Tag color="blue">{item.category}</Tag>
                          {item.itemType === 'PACKAGE'
                            ? <Tag color="purple">Combo</Tag>
                            : <Tag>Individual</Tag>}
                          {item.size && <Tag>{item.size}</Tag>}
                        </Space>
                        <div>{formatCurrency(item.rate)}/day</div>
                        <div style={{ color: '#666' }}>Deposit: {formatCurrency(item.deposit)}</div>
                        <Tag color="success">{item.availableQuantity} available</Tag>
                        {inCart && <Tag color="orange">In cart ×{inCart.quantity}</Tag>}
                        {item.itemType === 'PACKAGE' && item.componentNames && item.componentNames.length > 0 && (
                          <div style={{ marginTop: 4, fontSize: 12, color: '#722ed1' }}>
                            <span style={{ fontWeight: 500 }}>Includes: </span>
                            {item.componentNames.join(', ')}
                          </div>
                        )}
                      </Space>
                    }
                  />
                </Card>
              </Col>
            )
          })}
        </Row>

        {itemsPage && (
          <div style={{ display: 'flex', justifyContent: 'center', marginTop: 24, marginBottom: 80 }}>
            <Pagination
              current={browsePage + 1}
              total={itemsPage.totalElements}
              pageSize={20}
              onChange={p => setBrowsePage(p - 1)}
              showSizeChanger={false}
            />
          </div>
        )}

        {/* Sticky bottom bar */}
        <div style={{
          position: 'fixed', bottom: 0, left: isMobile ? 0 : 220, right: 0,
          background: '#fff', borderTop: '1px solid #eed6e0',
          boxShadow: '0 -2px 8px rgba(110,11,55,0.15)',
          padding: isMobile ? '8px 12px' : '12px 24px',
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
          zIndex: 100,
        }}>
          <Popover
            trigger="click"
            placement="topLeft"
            title="Cart"
            content={
              <div style={{ maxWidth: 320, maxHeight: 320, overflowY: 'auto' }}>
                <Space direction="vertical" size={8} style={{ width: '100%' }}>
                  {cart!.items.map(item => (
                    <div
                      key={item.lineKey}
                      style={{ display: 'flex', justifyContent: 'space-between', gap: 16 }}
                    >
                      <Typography.Text style={{ flex: 1 }}>
                        {item.itemName} ×{item.quantity}
                      </Typography.Text>
                      <Typography.Text type="secondary">
                        {formatCurrency(lineRentOf(item, cart!.rentalDays))}
                      </Typography.Text>
                    </div>
                  ))}
                </Space>
              </div>
            }
          >
            <Space style={{ cursor: 'pointer' }}>
              <ShoppingCartOutlined style={{ fontSize: 20 }} />
              <Typography.Text strong>
                {cartCount} item{cartCount !== 1 ? 's' : ''} · {formatCurrency(cartTotal)}
              </Typography.Text>
            </Space>
          </Popover>
          <Space>
            <Button danger onClick={handleDeleteCart}>Delete Cart</Button>
            <Button
              type="primary"
              onClick={() => setScreen('preview')}
            >
              Checkout
            </Button>
          </Space>
        </div>

        <ItemBrowseModal
          item={packageModal}
          onClose={() => setPackageModal(null)}
          onAddToCart={handleAddToCart}
          onRemoveFromCart={removeItem}
          onUpdateQty={updateQuantity}
          inCartQty={packageModalCartLine?.quantity ?? 0}
          inCartLineKey={packageModalCartLine?.lineKey ?? null}
          maxQty={packageModal?.availableQuantity ?? 1}
        />

        {isOwner && (
          <AdHocItemModal
            open={showAdHocModal}
            rentalDays={cart!.rentalDays}
            onCancel={() => setShowAdHocModal(false)}
            onAdd={(item) => { addItem(item); setShowAdHocModal(false) }}
          />
        )}
      </div>
    )
  }

  // ---- PREVIEW ----
  if (screen === 'preview') {
    // Build a lookup map from fresh API data so stale localStorage cart entries get enriched
    const freshItemMap = new Map((previewItemsPage?.content ?? []).map(i => [i.id, i]))

    const previewColumns = [
      {
        title: 'Item',
        key: 'name',
        render: (_: unknown, r: CartItem) => {
          const fresh = r.kind === 'CATALOGUE' ? freshItemMap.get(r.itemId) : undefined
          const category = r.kind === 'CATALOGUE' ? (fresh?.category ?? r.category) : null
          const size = fresh?.size ?? r.size ?? null
          const componentNames = r.kind === 'CATALOGUE' ? (fresh?.componentNames ?? r.componentNames ?? null) : null
          const thumbnailUrl = r.kind === 'CATALOGUE' ? (r.thumbnailUrl ?? fresh?.thumbnailUrl ?? null) : null
          return (
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
              <div style={{ width: 72, height: 72, flexShrink: 0, overflow: 'hidden', borderRadius: 4 }}>
                {thumbnailUrl ? (
                  <img
                    src={thumbnailUrl}
                    alt={r.itemName}
                    style={{ width: 72, height: 72, objectFit: 'cover', borderRadius: 4 }}
                  />
                ) : (
                  <ItemPhotoPlaceholder />
                )}
              </div>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap', marginBottom: 2 }}>
                  <span style={{ fontWeight: 500 }}>{r.itemName}</span>
                  {r.kind === 'CATALOGUE' && (r.itemType === 'PACKAGE'
                    ? <Tag color="purple" style={{ margin: 0 }}>Combo</Tag>
                    : <Tag style={{ margin: 0 }}>Individual</Tag>)}
                  {r.kind === 'ADHOC' && <Tag color="gold" style={{ margin: 0 }}>Custom</Tag>}
                  {category && <Tag color="blue" style={{ margin: 0 }}>{category}</Tag>}
                  {size && <Tag style={{ margin: 0 }}>{size}</Tag>}
                </div>
                {r.kind === 'CATALOGUE' && r.itemType === 'PACKAGE' && componentNames && componentNames.length > 0 && (
                  <div style={{ fontSize: 12, paddingLeft: 2 }}>
                    <div style={{ color: '#722ed1', fontWeight: 500, marginBottom: 3 }}>Includes:</div>
                    {componentNames.map((name, i) => (
                      <div key={i} style={{ color: '#444', paddingLeft: 8, lineHeight: '20px' }}>
                        · {name}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )
        },
      },
      {
        title: 'Qty',
        dataIndex: 'quantity',
        key: 'qty',
        render: (qty: number, row: CartItem) => (
          <InputNumber
            min={1}
            max={row.kind === 'CATALOGUE' ? row.availableQuantity : MAX_AD_HOC_QUANTITY}
            precision={0}
            value={qty}
            onChange={(v) => { if (v && v >= 1) updateQuantity(row.lineKey, v) }}
            size="small"
          />
        ),
      },
      {
        title: 'Rate/day',
        key: 'rate',
        render: (_: unknown, r: CartItem) => r.kind === 'ADHOC'
          ? <span>{formatCurrency(perDayRateOf(r, cart!.rentalDays))} <Typography.Text type="secondary" style={{ fontSize: 11 }}>(derived)</Typography.Text></span>
          : formatCurrency(perDayRateOf(r, cart!.rentalDays)),
      },
      { title: 'Deposit', key: 'deposit', render: (_: unknown, r: CartItem) => formatCurrency(r.deposit) },
      {
        title: 'Line Rent',
        key: 'lineRent',
        render: (_: unknown, r: CartItem) => formatCurrency(lineRentOf(r, cart!.rentalDays)),
      },
      {
        title: 'Line Deposit',
        key: 'lineDeposit',
        render: (_: unknown, r: CartItem) => formatCurrency(r.deposit * r.quantity),
      },
      {
        title: '',
        key: 'remove',
        width: 56,
        render: (_: unknown, r: CartItem) => (
          <Button
            type="text"
            danger
            size="large"
            icon={<DeleteOutlined style={{ fontSize: 20 }} />}
            aria-label="Remove"
            onClick={() => removeItem(r.lineKey)}
          />
        ),
      },
    ]

    const localTotalRent = cart!.items.reduce((s, i) => s + lineRentOf(i, cart!.rentalDays), 0)
    const localTotalDeposit = cart!.items.reduce((s, i) => s + i.deposit * i.quantity, 0)
    const appliedCoupon = cart!.appliedCoupon ?? null
    // Once a coupon is applied, every total on this screen renders from the server's
    // preview response rather than local math — otherwise a rate change between the
    // preview call and now would make this screen and the server disagree.
    const totalRent = appliedCoupon?.totalRent ?? localTotalRent
    const totalDeposit = appliedCoupon?.totalDeposit ?? localTotalDeposit
    const grandTotal = appliedCoupon?.grandTotal ?? (localTotalRent + localTotalDeposit)

    return (
      <div style={{ maxWidth: 920, width: '100%' }}>
        <PageHeader label="New Rental" title="Order" accent="Preview" />

        <Descriptions size="small" style={{ marginBottom: 16 }}>
          <Descriptions.Item label="Start">{dayjs(cart!.startDatetime).format('DD MMM YYYY HH:mm')}</Descriptions.Item>
          <Descriptions.Item label="End">{dayjs(cart!.endDatetime).format('DD MMM YYYY HH:mm')}</Descriptions.Item>
          <Descriptions.Item label="Duration">{cart!.rentalDays} day{cart!.rentalDays !== 1 ? 's' : ''}</Descriptions.Item>
        </Descriptions>

        <div style={{ overflowX: 'auto', marginBottom: 24 }}>
          <Table
            dataSource={cart!.items}
            columns={previewColumns}
            rowKey="lineKey"
            pagination={false}
            size="small"
            scroll={{ x: 'max-content' }}
          />
        </div>

        <Card size="small" style={{ maxWidth: 360, marginBottom: 24 }}>
          <Descriptions column={1} size="small">
            <Descriptions.Item label="Total Rent">{formatCurrency(totalRent)}</Descriptions.Item>
            {appliedCoupon && (
              <Descriptions.Item label={`Discount (${appliedCoupon.couponCode})`}>
                <span style={{ color: '#52c41a' }}>−{formatCurrency(appliedCoupon.discountAmount)}</span>
              </Descriptions.Item>
            )}
            <Descriptions.Item label="Total Deposit">{formatCurrency(totalDeposit)}</Descriptions.Item>
            <Descriptions.Item label={<strong>Grand Total</strong>}>
              <strong>{formatCurrency(grandTotal)}</strong>
            </Descriptions.Item>
          </Descriptions>

          <Divider style={{ margin: '12px 0' }} />

          {appliedCoupon ? (
            <Space>
              <Tag color="green">{appliedCoupon.couponCode}</Tag>
              <Button size="small" type="link" style={{ padding: 0 }} onClick={handleRemoveCoupon}>
                Remove coupon
              </Button>
            </Space>
          ) : (
            <>
              <Space.Compact style={{ width: '100%' }}>
                <Input
                  placeholder="Have a coupon?"
                  value={couponInput}
                  onChange={e => setCouponInput(e.target.value)}
                  onPressEnter={handleApplyCoupon}
                />
                <Button loading={previewMutation.isPending} onClick={handleApplyCoupon}>
                  Apply
                </Button>
              </Space.Compact>
              {couponError && (
                <Alert type="error" message={couponError} showIcon style={{ marginTop: 8 }} />
              )}
            </>
          )}
        </Card>

        <Space wrap>
          <Button onClick={() => setScreen('browse')}>Back to Items</Button>
          {isOwner && (
            <Button icon={<PlusOutlined />} onClick={() => setShowAdHocModal(true)}>
              Add custom product
            </Button>
          )}
          <Button type="primary" disabled={cart!.items.length === 0} onClick={() => setScreen('customer')}>
            Confirm & Proceed
          </Button>
        </Space>

        {isOwner && (
          <AdHocItemModal
            open={showAdHocModal}
            rentalDays={cart!.rentalDays}
            onCancel={() => setShowAdHocModal(false)}
            onAdd={(item) => { addItem(item); setShowAdHocModal(false) }}
          />
        )}
      </div>
    )
  }

  // ---- CUSTOMER SELECTION ----
  if (screen === 'customer') {
    const localTotalRent = cart!.items.reduce((s, i) => s + lineRentOf(i, cart!.rentalDays), 0)
    const localTotalDeposit = cart!.items.reduce((s, i) => s + i.deposit * i.quantity, 0)
    const appliedCoupon = cart!.appliedCoupon ?? null
    // Renders from the same stored preview result as the preview screen — never
    // recomputed independently here, so the two screens can't show different totals for
    // an applied coupon.
    const totalRent = appliedCoupon?.totalRent ?? localTotalRent
    const totalDeposit = appliedCoupon?.totalDeposit ?? localTotalDeposit
    const grandTotal = appliedCoupon?.grandTotal ?? (localTotalRent + localTotalDeposit)

    return (
      <div style={{ maxWidth: 560 }}>
        <PageHeader label="New Rental" title="Select" accent="Customer" />

        <Typography.Paragraph type="secondary">
          Search by phone number or name. If the customer is new, register them first.
        </Typography.Paragraph>

        <CustomerSearch onSelect={setSelectedCustomer} placeholder="Search by phone or name..." />

        {selectedCustomer && (
          <Card size="small" style={{ marginTop: 16, marginBottom: 24 }}>
            <Descriptions column={1} size="small">
              <Descriptions.Item label="Name">{selectedCustomer.name}</Descriptions.Item>
              <Descriptions.Item label="Phone">{selectedCustomer.phone}</Descriptions.Item>
              {selectedCustomer.organizationName && (
                <Descriptions.Item label="Organisation">{selectedCustomer.organizationName}</Descriptions.Item>
              )}
            </Descriptions>
          </Card>
        )}

        <Divider />

        <Descriptions column={1} size="small" style={{ marginBottom: 24 }}>
          <Descriptions.Item label="Items">{cartCount}</Descriptions.Item>
          <Descriptions.Item label="Total Rent">{formatCurrency(totalRent)}</Descriptions.Item>
          {appliedCoupon && (
            <Descriptions.Item label={`Discount (${appliedCoupon.couponCode})`}>
              <span style={{ color: '#52c41a' }}>−{formatCurrency(appliedCoupon.discountAmount)}</span>
            </Descriptions.Item>
          )}
          <Descriptions.Item label="Total Deposit">{formatCurrency(totalDeposit)}</Descriptions.Item>
          <Descriptions.Item label={<strong>Grand Total</strong>}>
            <strong>{formatCurrency(grandTotal)}</strong>
          </Descriptions.Item>
        </Descriptions>

        {conflictError && (
          <Alert type="error" message={conflictError} style={{ marginBottom: 16 }} />
        )}

        <Space>
          <Button onClick={() => setScreen('preview')}>Back</Button>
          <Button onClick={() => navigate('/customers/register?returnTo=checkout')}>New Customer</Button>
          <Button
            type="primary"
            disabled={!selectedCustomer}
            loading={createMutation.isPending}
            onClick={handleConfirmReceipt}
          >
            Create Receipt
          </Button>
        </Space>
      </div>
    )
  }

  return null
}

// ---- Create Cart Modal ----

interface CreateCartModalProps {
  open: boolean
  useNow: boolean
  startPicker: Dayjs | null
  rentalDays: number
  error: string | null
  onUseNowChange: (v: boolean) => void
  onStartPickerChange: (v: Dayjs | null) => void
  onDaysChange: (v: number) => void
  onConfirm: () => void
  onCancel: () => void
}

function CreateCartModal({
  open, useNow, startPicker, rentalDays, error,
  onUseNowChange, onStartPickerChange, onDaysChange, onConfirm, onCancel,
}: CreateCartModalProps) {
  const previewStart = useNow ? dayjs() : startPicker
  const previewEnd = previewStart ? previewStart.add(rentalDays, 'day') : null

  return (
    <Modal
      open={open}
      title="Create New Cart"
      onOk={onConfirm}
      onCancel={onCancel}
      okText="Start Browsing"
    >
      <Space direction="vertical" size="large" style={{ width: '100%', paddingTop: 8 }}>
        <div>
          <Typography.Text strong>Start Time</Typography.Text>
          <div style={{ marginTop: 8 }}>
            <Checkbox
              checked={useNow}
              onChange={e => onUseNowChange(e.target.checked)}
              style={{ marginBottom: 8 }}
            >
              Use current time
            </Checkbox>
            {!useNow && (
              <DatePicker
                showTime
                format="DD MMM YYYY HH:mm"
                value={startPicker}
                onChange={onStartPickerChange}
                style={{ width: '100%' }}
              />
            )}
          </div>
        </div>

        <div>
          <Typography.Text strong>Number of Days</Typography.Text>
          <div style={{ marginTop: 8 }}>
            <InputNumber
              min={1}
              max={30}
              value={rentalDays}
              onChange={v => onDaysChange(v ?? 1)}
              addonAfter="days"
              style={{ width: 160 }}
            />
          </div>
        </div>

        {previewEnd && (
          <Alert
            type="info"
            message={
              `Return by: ${previewEnd.format('DD MMM YYYY HH:mm')} · ${rentalDays} day${rentalDays !== 1 ? 's' : ''}`
            }
          />
        )}

        {error && <Alert type="error" message={error} />}
      </Space>
    </Modal>
  )
}
