CREATE TABLE reviews (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reviewer_name    VARCHAR(80)  NOT NULL,
    phone            VARCHAR(15)  NOT NULL,
    item_description VARCHAR(100) NOT NULL,
    rating           INTEGER      NOT NULL CHECK (rating BETWEEN 1 AND 5),
    review_text      VARCHAR(256) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    submitter_ip     VARCHAR(45)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    moderated_at     TIMESTAMPTZ
);

CREATE TABLE review_images (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id     UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    url           TEXT NOT NULL,
    thumbnail_url TEXT NOT NULL,
    sort_order    INTEGER NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Public list: the two sort modes, both filtered to APPROVED
CREATE INDEX idx_reviews_status_created  ON reviews (status, created_at DESC);
CREATE INDEX idx_reviews_status_rating   ON reviews (status, rating DESC, created_at DESC);

-- Rate limiter lookups
CREATE INDEX idx_reviews_ip_created      ON reviews (submitter_ip, created_at);
CREATE INDEX idx_reviews_phone_created   ON reviews (phone, created_at);

CREATE INDEX idx_review_images_review_id ON review_images (review_id);

-- reviews holds phone numbers -- the one piece of PII this feature touches -- so it gets the
-- same RLS hardening as every other public-schema table (see
-- V20260921001__enable_rls_public_tables.sql). The app connects directly as the table owner,
-- which always bypasses RLS, so this has no effect on the app itself; it only denies
-- anon/authenticated by default, which is already covered by that migration's schema-wide
-- REVOKE ALL / ALTER DEFAULT PRIVILEGES statements.
ALTER TABLE public.reviews         ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.review_images   ENABLE ROW LEVEL SECURITY;
