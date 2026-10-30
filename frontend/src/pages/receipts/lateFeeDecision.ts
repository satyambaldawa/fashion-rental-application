// Pure late-fee decision helper for ProcessReturnPage (#184).
//
// The late fee depends only on the return datetime, the receipt end time, the rates and
// quantities (every line is always returned) and the live rules. Damage, notes and payment
// method never change it, so keying the decision on `calculatedLateFee` rather than on
// individual change events:
// - keeps the decision across edits to Notes, Damage Notes, payment method and damage;
// - clears it exactly when a date/time change alters the fee (a change that leaves the fee
//   the same keeps the decision);
// - covers the fee going to 0, then above 0 again;
// - avoids fragile event plumbing.

export type LateFeeDecision = 'APPLY' | 'WAIVE'

export interface LateFeeDecisionState {
  decision: LateFeeDecision | null
  decidedForFee: number | null
}

export const NO_DECISION: LateFeeDecisionState = { decision: null, decidedForFee: null }

export interface ReconcileResult {
  next: LateFeeDecisionState
  prompt: boolean
}

/** Called with every fresh preview to decide whether the existing decision still applies. */
export function reconcileLateFeeDecision(
  state: LateFeeDecisionState,
  calculatedLateFee: number,
): ReconcileResult {
  if (calculatedLateFee <= 0) {
    return { next: NO_DECISION, prompt: false }
  }
  if (state.decision !== null && state.decidedForFee === calculatedLateFee) {
    return { next: state, prompt: false }
  }
  return { next: NO_DECISION, prompt: true }
}

export function applyLateFeeFlag(decision: LateFeeDecision | null): boolean | undefined {
  if (decision === null) return undefined
  return decision === 'APPLY'
}
