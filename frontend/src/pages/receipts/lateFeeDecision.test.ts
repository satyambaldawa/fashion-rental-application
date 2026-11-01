import { describe, it, expect } from 'vitest'
import {
  reconcileLateFeeDecision,
  applyLateFeeFlag,
  NO_DECISION,
  type LateFeeDecisionState,
} from './lateFeeDecision'

describe('reconcileLateFeeDecision', () => {
  it('gives no prompt and clears the decision when the fee is 0', () => {
    const decided: LateFeeDecisionState = { decision: 'APPLY', decidedForFee: 300 }

    const { next, prompt } = reconcileLateFeeDecision(decided, 0)

    expect(prompt).toBe(false)
    expect(next).toEqual(NO_DECISION)
  })

  it('prompts when the fee is above 0 and there is no decision yet', () => {
    const { next, prompt } = reconcileLateFeeDecision(NO_DECISION, 300)

    expect(prompt).toBe(true)
    expect(next).toEqual(NO_DECISION)
  })

  it('keeps the decision when the same fee is recalculated', () => {
    const decided: LateFeeDecisionState = { decision: 'WAIVE', decidedForFee: 300 }

    const { next, prompt } = reconcileLateFeeDecision(decided, 300)

    expect(prompt).toBe(false)
    expect(next).toBe(decided)
  })

  it('clears the decision and prompts again when the fee changes', () => {
    const decided: LateFeeDecisionState = { decision: 'WAIVE', decidedForFee: 300 }

    const { next, prompt } = reconcileLateFeeDecision(decided, 450)

    expect(prompt).toBe(true)
    expect(next).toEqual(NO_DECISION)
  })

  it('clears the decision when the fee drops to 0, then prompts again once it rises', () => {
    const decided: LateFeeDecisionState = { decision: 'APPLY', decidedForFee: 300 }

    const afterZero = reconcileLateFeeDecision(decided, 0)
    expect(afterZero.prompt).toBe(false)
    expect(afterZero.next).toEqual(NO_DECISION)

    const afterRise = reconcileLateFeeDecision(afterZero.next, 300)
    expect(afterRise.prompt).toBe(true)
    expect(afterRise.next).toEqual(NO_DECISION)
  })
})

describe('applyLateFeeFlag', () => {
  it('maps null to undefined', () => {
    expect(applyLateFeeFlag(null)).toBeUndefined()
  })

  it('maps APPLY to true', () => {
    expect(applyLateFeeFlag('APPLY')).toBe(true)
  })

  it('maps WAIVE to false', () => {
    expect(applyLateFeeFlag('WAIVE')).toBe(false)
  })
})
