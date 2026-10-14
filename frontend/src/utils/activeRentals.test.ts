import { describe, it, expect } from 'vitest'
import dayjs from 'dayjs'
import * as f from '../test/factories'
import { groupByPickupDate, matchesRentalSearch } from './activeRentals'

describe('matchesRentalSearch', () => {
  const receipt = f.aReceiptSummary({ customerName: 'Priya Sharma', itemNames: ['Red Lehenga ×1', 'Royal Sherwani ×2'] })

  it('matches every receipt when the query is blank', () => {
    expect(matchesRentalSearch(receipt, '   ')).toBe(true)
  })

  it('matches on part of the customer name, ignoring case', () => {
    expect(matchesRentalSearch(receipt, 'priya')).toBe(true)
  })

  it('matches on part of any costume name, ignoring case', () => {
    expect(matchesRentalSearch(receipt, 'SHERWANI')).toBe(true)
  })

  it('ignores surrounding spaces in the query', () => {
    expect(matchesRentalSearch(receipt, '  red lehenga ')).toBe(true)
  })

  it('does not match when neither the customer nor any costume contains the query', () => {
    expect(matchesRentalSearch(receipt, 'anarkali')).toBe(false)
  })
})

describe('groupByPickupDate', () => {
  const now = dayjs('2026-10-15T12:00:00+05:30')
  const pickupAt = (iso: string, o = {}) => f.aReceiptSummary({ id: iso, startDatetime: iso, ...o })

  it('orders pickup dates from past to future', () => {
    const groups = groupByPickupDate([
      pickupAt('2026-10-18T10:00:00+05:30'),
      pickupAt('2026-10-12T10:00:00+05:30'),
      pickupAt('2026-10-15T10:00:00+05:30'),
    ], now)

    expect(groups.map(g => g.dateKey)).toEqual(['2026-10-12', '2026-10-15', '2026-10-18'])
  })

  it('puts rentals picked up on the same day into one group, earliest pickup first', () => {
    const groups = groupByPickupDate([
      pickupAt('2026-10-16T18:00:00+05:30'),
      pickupAt('2026-10-16T09:00:00+05:30'),
    ], now)

    expect(groups).toHaveLength(1)
    expect(groups[0].receipts.map(r => r.id)).toEqual(['2026-10-16T09:00:00+05:30', '2026-10-16T18:00:00+05:30'])
  })

  it('groups by the IST calendar day, not the UTC one', () => {
    const groups = groupByPickupDate([pickupAt('2026-10-15T20:00:00Z')], now)

    expect(groups[0].dateKey).toBe('2026-10-16')
  })

  it('labels today, tomorrow, later and past pickup dates', () => {
    const groups = groupByPickupDate([
      pickupAt('2026-10-12T10:00:00+05:30'),
      pickupAt('2026-10-15T10:00:00+05:30'),
      pickupAt('2026-10-16T10:00:00+05:30'),
      pickupAt('2026-10-18T10:00:00+05:30'),
    ], now)

    expect(groups.map(g => g.label)).toEqual([
      'Mon · 12 Oct · picked up',
      'Today · 15 Oct',
      'Tomorrow · 16 Oct',
      'Sun · 18 Oct',
    ])
  })

  it('opens today and tomorrow by default, keeping later dates folded', () => {
    const groups = groupByPickupDate([
      pickupAt('2026-10-15T10:00:00+05:30'),
      pickupAt('2026-10-16T10:00:00+05:30'),
      pickupAt('2026-10-18T10:00:00+05:30'),
    ], now)

    expect(groups.map(g => g.isOpenByDefault)).toEqual([true, true, false])
  })

  it('opens a past pickup date by default only when one of its rentals is overdue', () => {
    const groups = groupByPickupDate([
      pickupAt('2026-10-10T10:00:00+05:30', { isOverdue: true, overdueHours: 5 }),
      pickupAt('2026-10-12T10:00:00+05:30'),
    ], now)

    expect(groups.map(g => g.isOpenByDefault)).toEqual([true, false])
  })
})
