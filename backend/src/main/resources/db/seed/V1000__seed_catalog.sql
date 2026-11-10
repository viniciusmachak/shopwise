-- Demo catalog for local development. Not loaded in production.
INSERT INTO category (slug, name) VALUES
    ('running-shoes', 'Running Shoes'),
    ('hiking-boots', 'Hiking Boots'),
    ('jackets', 'Jackets'),
    ('backpacks', 'Backpacks'),
    ('t-shirts', 'T-Shirts');

INSERT INTO product (slug, name, brand, description, category_id, price, attributes) VALUES
    ('trail-runner-x', 'Trail Runner X', 'Northpeak', 'Lightweight trail running shoe with a grippy lug outsole and a rock plate for rough terrain.', 1, 89.90, '{"terrain":"trail","waterproof":false,"drop_mm":6}'),
    ('cloudstride-road', 'Cloudstride Road', 'Velo', 'Cushioned daily trainer for road running. Soft foam midsole, breathable knit upper.', 1, 119.00, '{"terrain":"road","waterproof":false,"drop_mm":8}'),
    ('stormguard-gtx-runner', 'Stormguard GTX Runner', 'Northpeak', 'Waterproof running shoe with a membrane upper. Keeps feet dry on wet roads and muddy paths.', 1, 139.00, '{"terrain":"mixed","waterproof":true,"drop_mm":8}'),
    ('tempo-lite', 'Tempo Lite', 'Velo', 'Light, responsive shoe for tempo runs and races. Firm ride, minimal upper.', 1, 99.50, '{"terrain":"road","waterproof":false,"drop_mm":4}'),
    ('budget-jogger', 'Everyday Jogger', 'Basic Co', 'Simple, durable running shoe for beginners and short easy runs.', 1, 49.90, '{"terrain":"road","waterproof":false,"drop_mm":10}'),
    ('ridge-mid-wp', 'Ridge Mid WP', 'Alpstep', 'Mid-cut waterproof hiking boot with ankle support for day hikes on rocky trails.', 2, 159.00, '{"cut":"mid","waterproof":true}'),
    ('summit-pro', 'Summit Pro', 'Alpstep', 'Stiff leather mountaineering boot, crampon compatible, insulated for cold weather.', 2, 289.00, '{"cut":"high","waterproof":true,"insulated":true}'),
    ('canyon-low', 'Canyon Low', 'Northpeak', 'Low-cut breathable hiking shoe for dry, warm trails. Quick drying mesh.', 2, 109.00, '{"cut":"low","waterproof":false}'),
    ('rainshell-25', 'Rainshell 2.5L', 'Northpeak', 'Packable waterproof rain jacket. Fully taped seams, adjustable hood, packs into its pocket.', 3, 129.00, '{"waterproof":true,"insulated":false,"packable":true}'),
    ('downlight-hoodie', 'Downlight Hoodie', 'Alpstep', 'Warm 800-fill down jacket with a hood. Very light, ideal for cold dry days.', 3, 219.00, '{"waterproof":false,"insulated":true,"fill":"down"}'),
    ('windrunner', 'Windrunner', 'Velo', 'Ultralight wind jacket for running. Reflective details for low light.', 3, 79.00, '{"waterproof":false,"insulated":false,"reflective":true}'),
    ('fleece-midlayer', 'Polar Fleece Midlayer', 'Basic Co', 'Cozy fleece jacket for layering in cool weather.', 3, 59.00, '{"waterproof":false,"insulated":true,"fill":"fleece"}'),
    ('daypack-22', 'Daypack 22', 'Northpeak', '22 liter daypack with a hydration sleeve, hip belt and rain cover.', 4, 74.90, '{"volume_l":22,"rain_cover":true}'),
    ('trek-55', 'Trek 55', 'Alpstep', '55 liter trekking backpack with an adjustable back system for multi-day trips.', 4, 199.00, '{"volume_l":55,"rain_cover":true}'),
    ('city-commuter-18', 'City Commuter 18', 'Basic Co', 'Slim 18 liter backpack with a padded laptop sleeve for daily commuting.', 4, 54.00, '{"volume_l":18,"laptop_sleeve":true}'),
    ('run-vest-5', 'Run Vest 5', 'Velo', '5 liter running vest with two soft flasks for long runs and trail races.', 4, 89.00, '{"volume_l":5,"flasks":2}'),
    ('merino-tee', 'Merino Tee', 'Alpstep', 'Soft merino wool t-shirt that resists odor on multi-day trips.', 5, 69.00, '{"material":"merino"}'),
    ('dryfit-run-tee', 'DryFit Run Tee', 'Velo', 'Quick drying technical t-shirt for running in hot weather.', 5, 29.90, '{"material":"polyester"}'),
    ('cotton-basic-tee', 'Cotton Basic Tee', 'Basic Co', 'Classic organic cotton t-shirt for everyday wear.', 5, 19.90, '{"material":"cotton"}');

-- Shoe and boot sizes (US)
INSERT INTO product_variant (product_id, sku, size, color)
SELECT p.id, upper(p.slug) || '-' || s.size, s.size, 'Black'
FROM product p
JOIN category c ON c.id = p.category_id AND c.slug IN ('running-shoes', 'hiking-boots')
CROSS JOIN (VALUES ('8'), ('9'), ('10'), ('11'), ('12')) AS s(size);

-- Apparel sizes
INSERT INTO product_variant (product_id, sku, size, color)
SELECT p.id, upper(p.slug) || '-' || s.size, s.size, 'Blue'
FROM product p
JOIN category c ON c.id = p.category_id AND c.slug IN ('jackets', 't-shirts')
CROSS JOIN (VALUES ('S'), ('M'), ('L'), ('XL')) AS s(size);

-- Backpacks come in one size
INSERT INTO product_variant (product_id, sku, size, color)
SELECT p.id, upper(p.slug), NULL, 'Gray'
FROM product p
JOIN category c ON c.id = p.category_id AND c.slug = 'backpacks';

-- Deterministic stock; some variants are sold out
INSERT INTO inventory (variant_id, quantity)
SELECT id, (id * 7) % 13 FROM product_variant;
