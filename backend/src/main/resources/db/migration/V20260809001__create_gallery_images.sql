CREATE TABLE gallery_images (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category       VARCHAR(20) NOT NULL,
    image_url      TEXT NOT NULL,
    thumbnail_url  TEXT NOT NULL,
    caption        TEXT,
    sort_order     INTEGER NOT NULL DEFAULT 0,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gallery_images_category_sort ON gallery_images (category, sort_order);
