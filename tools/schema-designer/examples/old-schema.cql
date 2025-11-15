-- Example old schema for migration testing

CREATE KEYSPACE IF NOT EXISTS ecommerce
WITH replication = {'class': 'NetworkTopologyStrategy', 'dc1': 3};

CREATE TABLE IF NOT EXISTS ecommerce.users (
    user_id UUID,
    email TEXT,
    name TEXT,
    created_at TIMESTAMP,
    PRIMARY KEY (user_id)
);

CREATE TABLE IF NOT EXISTS ecommerce.orders (
    user_id UUID,
    order_id UUID,
    order_time TIMESTAMP,
    total_amount DECIMAL,
    status TEXT,
    PRIMARY KEY (user_id, order_time, order_id)
) WITH CLUSTERING ORDER BY (order_time DESC, order_id ASC);
