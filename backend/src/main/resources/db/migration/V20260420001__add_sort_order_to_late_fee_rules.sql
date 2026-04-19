ALTER TABLE late_fee_rules ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;

UPDATE late_fee_rules SET sort_order = 1 WHERE duration_from_hours = 0;
UPDATE late_fee_rules SET sort_order = 2 WHERE duration_from_hours = 3;
UPDATE late_fee_rules SET sort_order = 3 WHERE duration_from_hours = 6;
UPDATE late_fee_rules SET sort_order = 4 WHERE duration_from_hours = 24;
UPDATE late_fee_rules SET sort_order = 5 WHERE duration_from_hours = 48;
