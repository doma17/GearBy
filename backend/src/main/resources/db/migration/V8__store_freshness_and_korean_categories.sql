ALTER TABLE stores ADD COLUMN verified_at TIMESTAMPTZ;

UPDATE stores
SET verified_at = updated_at
WHERE status = 'PUBLISHED';

UPDATE categories
SET display_name = CASE slug
    WHEN 'HIKING' THEN '등산'
    WHEN 'BACKPACKING' THEN '백패킹'
    WHEN 'CAMPING' THEN '캠핑'
    WHEN 'CLIMBING' THEN '클라이밍'
END
WHERE slug IN ('HIKING', 'BACKPACKING', 'CAMPING', 'CLIMBING');
