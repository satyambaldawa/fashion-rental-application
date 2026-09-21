-- Supabase's default project setup grants ALL privileges on every public-schema
-- table to the anon/authenticated roles, since that's the expected pattern for
-- apps built with supabase-js + RLS. This app never uses Supabase Auth or the
-- PostgREST Data API -- it connects directly as the postgres table owner, which
-- always bypasses RLS regardless of policies. Enabling RLS here with no policies
-- denies anon/authenticated by default and has no effect on the app itself.
ALTER TABLE public.users                ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.customers            ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.receipts             ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.receipt_line_items   ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.invoices             ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.invoice_line_items   ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.items                ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.item_photos          ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.package_components   ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.late_fee_rules       ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.gallery_images       ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.coupons              ENABLE ROW LEVEL SECURITY;

-- Belt and suspenders: the grants themselves are also unused attack surface, so
-- drop them outright rather than relying on RLS alone. Includes default
-- privileges so a future CREATE TABLE doesn't silently reopen this. Guarded by
-- role existence because anon/authenticated are Supabase-specific and don't
-- exist on a plain local-dev Postgres.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM anon';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA public FROM authenticated';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM authenticated';
    END IF;
END $$;
