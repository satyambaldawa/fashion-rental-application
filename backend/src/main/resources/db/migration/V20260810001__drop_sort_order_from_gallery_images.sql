-- Gallery display order is now newest-first (created_at DESC); manual sort_order removed.
-- Dropping the column also drops the dependent idx_gallery_images_category_sort index.
ALTER TABLE gallery_images DROP COLUMN sort_order;

CREATE INDEX idx_gallery_images_category_created ON gallery_images (category, created_at DESC);
