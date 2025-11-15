-- Example new schema for migration testing

CREATE KEYSPACE IF NOT EXISTS ecommerce
WITH replication = {'class': 'NetworkTopologyStrategy', 'dc1': 3};

CREATE TABLE IF NOT EXISTS ecommerce.users (
    user_id UUID,
    email TEXT,
    name TEXT,
    phone TEXT,  -- NEW COLUMN
    created_at TIMESTAMP,
    updated_at TIMESTAMP,  -- NEW COLUMN
    PRIMARY KEY (user_id)
);

-- New materialized view
CREATE MATERIALIZED VIEW IF NOT EXISTS ecommerce.users_by_email AS
    SELECT * FROM ecommerce.users
    WHERE email IS NOT NULL AND user_id IS NOT NULL
    PRIMARY KEY (email, user_id);

CREATE TABLE IF NOT EXISTS ecommerce.orders (
    user_id UUID,
    order_month INT,  -- NEW: Time bucketing added
    order_id UUID,
    order_time TIMESTAMP,
    total_amount DECIMAL,
    status TEXT,
    PRIMARY KEY ((user_id, order_month), order_time, order_id)
) WITH CLUSTERING ORDER BY (order_time DESC, order_id ASC);

-- New table
CREATE TABLE IF NOT EXISTS ecommerce.products (
    product_id UUID,
    name TEXT,
    price DECIMAL,
    category TEXT,
    PRIMARY KEY (product_id)
);
