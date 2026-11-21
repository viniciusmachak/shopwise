-- Payments can now wait for the customer on a provider's page (Stripe Checkout).
ALTER TABLE payment ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'simulated';
ALTER TABLE payment ADD COLUMN provider_payment_id VARCHAR(100);
ALTER TABLE payment ADD COLUMN checkout_url VARCHAR(1000);
ALTER TABLE payment ADD COLUMN expires_at TIMESTAMPTZ;
CREATE INDEX payment_reference_idx ON payment (reference);

-- Orders waiting for payment keep the cart until it's paid, and a link to pay.
ALTER TABLE orders ADD COLUMN cart_key UUID;
ALTER TABLE orders ADD COLUMN payment_url VARCHAR(1000);
ALTER TABLE orders ADD COLUMN payment_expires_at TIMESTAMPTZ;
