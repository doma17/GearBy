CREATE TABLE categories (
    slug VARCHAR(32) PRIMARY KEY,
    display_name VARCHAR(80) NOT NULL
);

CREATE TABLE stores (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    normalized_address VARCHAR(400) NOT NULL,
    latitude NUMERIC(9, 6) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(9, 6) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    phone VARCHAR(40),
    hours VARCHAR(500),
    description TEXT,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'IN_REVIEW', 'PUBLISHED', 'REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE store_categories (
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    category_slug VARCHAR(32) NOT NULL REFERENCES categories(slug),
    PRIMARY KEY (store_id, category_slug)
);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    actor VARCHAR(200) NOT NULL,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id UUID NOT NULL,
    before_state JSONB,
    after_state JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX stores_published_name_idx ON stores (name) WHERE status = 'PUBLISHED';
CREATE INDEX store_categories_category_idx ON store_categories (category_slug, store_id);
CREATE INDEX audit_events_resource_idx ON audit_events (resource_type, resource_id, created_at DESC);

INSERT INTO categories (slug, display_name) VALUES
    ('HIKING', 'Hiking'),
    ('BACKPACKING', 'Backpacking'),
    ('CAMPING', 'Camping'),
    ('CLIMBING', 'Climbing');

INSERT INTO stores (id, name, normalized_address, latitude, longitude, phone, hours, description, status) VALUES
    ('11111111-1111-1111-1111-111111111111', 'GearBy Seoul Trail', '서울특별시 종로구 세종대로 1', 37.566500, 126.978000, '02-0000-0001', '10:00-20:00', 'Seed reviewed hiking store.', 'PUBLISHED'),
    ('22222222-2222-2222-2222-222222222222', 'GearBy Gyeonggi Camp', '경기도 성남시 분당구 판교역로 1', 37.394700, 127.111200, '031-000-0002', '10:00-19:00', 'Seed reviewed camping store.', 'PUBLISHED');

INSERT INTO store_categories (store_id, category_slug) VALUES
    ('11111111-1111-1111-1111-111111111111', 'HIKING'),
    ('22222222-2222-2222-2222-222222222222', 'CAMPING');
