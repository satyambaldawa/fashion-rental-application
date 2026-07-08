-- Re-activate all late fee rules.
-- A Hibernate L1-cache bug in the previous ConfigService.updateLateFeeRules()
-- caused deactivateAll() to persist (is_active = false) but the per-row
-- re-activation UPDATEs to be skipped (dirty-check saw no change in cached entities).
-- Fixed in code with @Modifying(clearAutomatically = true); this migration
-- restores the rows that were left inactive.
UPDATE late_fee_rules SET is_active = true;
