-- Demo product photos from Unsplash (free license, hotlinked from their CDN).
-- Production catalogs set image_url through the backoffice or the CSV import.
UPDATE product AS p SET image_url = v.url, updated_at = now()
FROM (VALUES
    ('trail-runner-x', 'https://images.unsplash.com/photo-1580058572462-98e2c0e0e2f0?auto=format&fit=crop&w=800&q=80'),
    ('cloudstride-road', 'https://images.unsplash.com/photo-1560769629-975ec94e6a86?auto=format&fit=crop&w=800&q=80'),
    ('stormguard-gtx-runner', 'https://images.unsplash.com/photo-1671906531003-8634ef6d597c?auto=format&fit=crop&w=800&q=80'),
    ('tempo-lite', 'https://images.unsplash.com/photo-1637437411360-b4607d62ddd3?auto=format&fit=crop&w=800&q=80'),
    ('budget-jogger', 'https://images.unsplash.com/photo-1653179767393-381b3bfae1a9?auto=format&fit=crop&w=800&q=80'),
    ('ridge-mid-wp', 'https://images.unsplash.com/photo-1779812773752-3d19944bf291?auto=format&fit=crop&w=800&q=80'),
    ('summit-pro', 'https://images.unsplash.com/photo-1776725121337-48de2c501672?auto=format&fit=crop&w=800&q=80'),
    ('canyon-low', 'https://images.unsplash.com/photo-1698732662028-2443c4acd739?auto=format&fit=crop&w=800&q=80'),
    ('rainshell-25', 'https://images.unsplash.com/photo-1624548140150-108c3287f551?auto=format&fit=crop&w=800&q=80'),
    ('downlight-hoodie', 'https://images.unsplash.com/photo-1572349618468-4e4208f41af2?auto=format&fit=crop&w=800&q=80'),
    ('windrunner', 'https://images.unsplash.com/photo-1571867424485-369464ed33cc?auto=format&fit=crop&w=800&q=80'),
    ('fleece-midlayer', 'https://images.unsplash.com/photo-1649937408746-4d2f603f91c8?auto=format&fit=crop&w=800&q=80'),
    ('daypack-22', 'https://images.unsplash.com/photo-1509762774605-f07235a08f1f?auto=format&fit=crop&w=800&q=80'),
    ('trek-55', 'https://images.unsplash.com/photo-1622260614153-03223fb72052?auto=format&fit=crop&w=800&q=80'),
    ('city-commuter-18', 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=800&q=80'),
    ('run-vest-5', 'https://images.unsplash.com/photo-1789126344191-17a53bbc3893?auto=format&fit=crop&w=800&q=80'),
    ('merino-tee', 'https://images.unsplash.com/photo-1618354691373-d851c5c3a990?auto=format&fit=crop&w=800&q=80'),
    ('dryfit-run-tee', 'https://images.unsplash.com/photo-1564584217132-2271feaeb3c5?auto=format&fit=crop&w=800&q=80'),
    ('cotton-basic-tee', 'https://images.unsplash.com/photo-1581655353564-df123a1eb820?auto=format&fit=crop&w=800&q=80')
) AS v(slug, url)
WHERE p.slug = v.slug;
