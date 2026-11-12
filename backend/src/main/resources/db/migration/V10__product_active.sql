-- Archived products stay in the database (orders and carts refer to them) but are hidden from the store.
ALTER TABLE product ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;
