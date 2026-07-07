-- Expand category CHECK constraint to include new high-level categories
-- Existing values (COSTUME, ACCESSORIES, PAGDI, DRESS, ORNAMENTS) are preserved as-is
ALTER TABLE items DROP CONSTRAINT items_category_check;
ALTER TABLE items
    ADD CONSTRAINT items_category_check
        CHECK (category IN (
            'COSTUME', 'ACCESSORIES', 'PAGDI', 'DRESS', 'ORNAMENTS',
            'TRADITIONAL', 'MYTHOLOGICAL', 'FREEDOM_FIGHTER', 'PROFESSIONS',
            'FANCY_DRESS', 'SEASONAL', 'OTHER'
        ));
