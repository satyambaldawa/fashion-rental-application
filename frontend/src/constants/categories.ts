import type { ItemCategory } from '../types/inventory'

// Display labels for the item/gallery categories. Kept in one place so the
// public gallery and the admin gallery page stay in sync.
export const CATEGORY_OPTIONS: { label: string; value: ItemCategory }[] = [
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

export const CATEGORY_LABELS: Map<ItemCategory, string> = new Map(
  CATEGORY_OPTIONS.map(opt => [opt.value, opt.label]),
)
