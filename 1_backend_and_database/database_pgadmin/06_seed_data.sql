-- =============================================================================
-- ĐỒ ÁN GỘP 2 MÔN: DATABASE PROGRAMMING & MOBILE APPLICATION PROGRAMMING
-- ĐỀ TÀI: HỆ SINH THÁI ĐẶT ĐỒ ĂN & QUẢN LÝ F&B (SMART FOOD DELIVERY / FOODFLOW)
-- PHÂN HỆ: THÀNH VIÊN 1 & THÀNH VIÊN 2 (DATABASE SEED DATA)
-- FILE 06: 06_seed_data.sql
-- Nội dung:
--   - Làm sạch dữ liệu & Reset chuỗi tự tăng (RESTART IDENTITY CASCADE)
--   - 8 Users (3 CUSTOMER, 4 RESTAURANT Owners, 1 SHIPPER) - Mật khẩu chung: 123456
--   - 4 Quán ăn thực tế tại TP.HCM (Highlands, Phúc Long, Pizza 4P's, Cơm Tấm Sài Gòn)
--   - 8 Danh mục (Categories) & 20 Món ăn (Menu Items) kèm JSONB options_config
--   - ĐẶC BIỆT: Món số 1 "Trà Sen Vàng (Đặc Biệt)" có stock_quantity = 1 để Demo Race Condition!
--   - 5 Mã giảm giá (Vouchers) gồm mã toàn sàn, mã Freeship, mã riêng của quán & mã còn 1 lượt cuối
--   - 3 Đơn hàng mẫu (PENDING, DELIVERING, COMPLETED) kèm order_details & audit_logs
-- =============================================================================

SET client_encoding = 'UTF8';

-- Làm sạch toàn bộ dữ liệu cũ và đưa bộ đếm SERIAL về lại 1
TRUNCATE TABLE
    audit_logs,
    order_details,
    orders,
    vouchers,
    menu_items,
    categories,
    restaurants,
    users
RESTART IDENTITY CASCADE;

-- =============================================================================
-- 1. SEED BẢNG users (8 TÀI KHOẢN: 3 CUSTOMER, 4 RESTAURANT, 1 SHIPPER)
-- Lưu ý: Mật khẩu gốc của tất cả tài khoản bên dưới là '123456'
-- Chuỗi bcrypt chuẩn (cost = 10): $2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy
-- =============================================================================
INSERT INTO users (
    username, password_hash, full_name, phone, role,
    loyalty_points, member_tier, saved_addresses, favorite_restaurant_ids
) VALUES
-- ID 1..3: KHÁCH HÀNG (CUSTOMER)
(
    'khach_trung',
    '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy',
    'Nguyễn Quốc Trung',
    '0901234561',
    'CUSTOMER',
    1250,
    'GOLD',
    '[{"label": "Nhà riêng", "address": "123 Nguyễn Thị Minh Khai, Quận 1, TP.HCM", "lat": 10.7756, "lng": 106.6924}, {"label": "Công ty", "address": "Bitexco Financial Tower, 2 Hải Triều, Quận 1, TP.HCM", "lat": 10.7717, "lng": 106.7044}]'::jsonb,
    ARRAY[1, 2, 3]
),
(
    'khach_lan',
    '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy',
    'Trần Ngọc Lan',
    '0901234562',
    'CUSTOMER',
    450,
    'SILVER',
    '[{"label": "Nhà riêng", "address": "45 Lê Thánh Tôn, Quận 1, TP.HCM", "lat": 10.7782, "lng": 106.7012}]'::jsonb,
    ARRAY[1, 4]
),
(
    'khach_minh',
    '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy',
    'Lê Hoàng Minh',
    '0901234563',
    'CUSTOMER',
    3200,
    'DIAMOND',
    '[{"label": "Chung cư", "address": "88 Đồng Khởi, Quận 1, TP.HCM", "lat": 10.7745, "lng": 106.7035}]'::jsonb,
    ARRAY[2, 3]
),

-- ID 4..7: CHỦ QUÁN (RESTAURANT)
(
    'chuquan_highlands',
    '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy',
    'Quản Lý Highlands Nguyễn Huệ',
    '0901234564',
    'RESTAURANT',
    0, 'SILVER', '[]'::jsonb, '{}'
),
(
    'chuquan_phuclong',
    '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy',
    'Quản Lý Phúc Long Lê Lợi',
    '0901234565',
    'RESTAURANT',
    0, 'SILVER', '[]'::jsonb, '{}'
),
(
    'chuquan_pizza4ps',
    '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy',
    'Quản Lý Pizza 4Ps Bến Thành',
    '0901234566',
    'RESTAURANT',
    0, 'SILVER', '[]'::jsonb, '{}'
),
(
    'chuquan_comtam',
    '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy',
    'Cô Ba Cơm Tấm Sài Gòn',
    '0901234567',
    'RESTAURANT',
    0, 'SILVER', '[]'::jsonb, '{}'
),

-- ID 8: TÀI XẾ GIAO HÀNG (SHIPPER)
(
    'shipper_nam',
    '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy',
    'Phạm Văn Nam (Tài xế 5 Sao)',
    '0901234568',
    'SHIPPER',
    0, 'SILVER', '[]'::jsonb, '{}'
);

-- =============================================================================
-- 2. SEED BẢNG restaurants (4 QUÁN ĂN THƯƠNG HIỆU TẠI TRUNG TÂM TP.HCM - SCR-08)
-- =============================================================================
INSERT INTO restaurants (
    owner_id, name, address, latitude, longitude,
    open_time, close_time, is_active,
    brand_name, cover_image_url, rating, is_freeship, prep_time_minutes
) VALUES
(
    4,
    'Highlands Coffee - Phố Đi Bộ Nguyễn Huệ',
    '75 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP.HCM',
    10.7738, 106.7041,
    '06:30:00', '23:00:00', TRUE,
    'Highlands Coffee',
    'https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=800',
    4.85, TRUE, 10
),
(
    5,
    'Phúc Long Coffee & Tea - Lê Lợi',
    '63 Lê Lợi, Phường Bến Nghé, Quận 1, TP.HCM',
    10.7725, 106.6980,
    '07:00:00', '22:30:00', TRUE,
    'Phúc Long',
    'https://images.unsplash.com/photo-1556679343-c7306c1976bc?w=800',
    4.90, TRUE, 12
),
(
    6,
    'Pizza 4P''s - Bến Thành',
    '8 Thủ Khoa Huân, Phường Bến Thành, Quận 1, TP.HCM',
    10.7731, 106.6968,
    '10:00:00', '22:00:00', TRUE,
    'Pizza 4P''s',
    'https://images.unsplash.com/photo-1513104890138-7c749659a591?w=800',
    4.95, FALSE, 20
),
(
    7,
    'Cơm Tấm Sài Gòn Xưa - Quận 3',
    '182 Võ Văn Tần, Phường 5, Quận 3, TP.HCM',
    10.7797, 106.6882,
    '06:00:00', '21:30:00', TRUE,
    'Cơm Tấm Sài Gòn',
    'https://images.unsplash.com/photo-1512058564366-18510be2db19?w=800',
    4.75, TRUE, 15
);

-- =============================================================================
-- 3. SEED BẢNG categories (8 DANH MỤC: MỖI QUÁN 2 DANH MỤC)
-- =============================================================================
INSERT INTO categories (restaurant_id, name, display_order) VALUES
-- Quán 1: Highlands Coffee (Category ID 1, 2)
(1, 'Trà Trái Cây & Trà Sen Vàng', 1),
(1, 'Cà Phê Phin & Freeze Đá Xay', 2),

-- Quán 2: Phúc Long (Category ID 3, 4)
(2, 'Trà Sữa Đậm Vị Phúc Long', 1),
(2, 'Trà Trái Cây Nhiệt Đới', 2),

-- Quán 3: Pizza 4P's (Category ID 5, 6)
(3, 'Pizza Nướng Củi Thủ Công', 1),
(3, 'Mỳ Ý & Khai Vị Phô Mai', 2),

-- Quán 4: Cơm Tấm Sài Gòn Xưa (Category ID 7, 8)
(4, 'Cơm Tấm Sườn Nướng Than Hoa', 1),
(4, 'Canh & Món Thêm Đặc Sắc', 2);

-- =============================================================================
-- 4. SEED BẢNG menu_items (20 MÓN ĂN - MỖI QUÁN 5 MÓN)
-- ĐẶC BIỆT: Món ID = 1 ("Trà Sen Vàng (Đặc Biệt)") có stock_quantity = 1
-- để nhóm Demo kịch bản 2 máy Android cùng bấm đặt ly cuối cùng (Race Condition)!
-- =============================================================================
INSERT INTO menu_items (
    category_id, name, description, price, stock_quantity, image_url, is_available,
    calories, dietary_tags, options_config
) VALUES
-- === QUÁN 1: HIGHLANDS COFFEE (Item ID 1..5) ===
(
    1,
    'Trà Sen Vàng (Đặc Biệt)',
    'Trà Ô Long kết hợp hạt sen bùi bùi, củ năng giòn ngọt và lớp kem phô mai macchiato béo ngậy. (Món Demo Race Condition: Tồn kho = 1)',
    49000,
    1, -- [QUAN TRỌNG] TỒN KHO = 1 ĐỂ TEST RACE CONDITION & TRIGGER TỰ ĐỘNG KHÓA MÓN
    'https://images.unsplash.com/photo-1544787219-7f47ccb76574?w=500',
    TRUE,
    240,
    ARRAY['Best Seller', 'Ít đường'],
    '{"sizes": [{"name": "S", "extra": 0}, {"name": "M", "extra": 6000}, {"name": "L", "extra": 10000}], "sugar": ["30%", "50%", "70%", "100%"], "ice": ["Ít đá", "Bình thường", "Đá riêng"], "toppings": [{"name": "Thêm hạt sen", "price": 10000}, {"name": "Thêm củ năng", "price": 10000}]}'::jsonb
),
(
    1,
    'Trà Thạch Đào Cam Sả',
    'Vị trà đào thanh mát hòa quyện cùng miếng đào giòn và thạch đào thơm lừng.',
    49000,
    50,
    'https://images.unsplash.com/photo-1556679343-c7306c1976bc?w=500',
    TRUE,
    190,
    ARRAY['Healthy', 'Trái cây tươi'],
    '{"sizes": [{"name": "S", "extra": 0}, {"name": "M", "extra": 6000}, {"name": "L", "extra": 10000}], "sugar": ["50%", "70%", "100%"], "ice": ["Ít đá", "Bình thường"]}'::jsonb
),
(
    1,
    'Trà Thạch Vải Nhiệt Đới',
    'Trà đen ủ lạnh kết hợp trái vải mọng nước và thạch vải giòn dai.',
    45000,
    40,
    'https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?w=500',
    TRUE,
    185,
    ARRAY['Giải nhiệt'],
    '{"sizes": [{"name": "S", "extra": 0}, {"name": "M", "extra": 6000}]}'::jsonb
),
(
    2,
    'Phin Sữa Đá Đậm Đà',
    'Cà phê phin truyền thống Việt Nam pha cùng sữa đặc ngọt dịu.',
    35000,
    80,
    'https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?w=500',
    TRUE,
    210,
    ARRAY['Tỉnh táo'],
    '{"sizes": [{"name": "S", "extra": 0}, {"name": "M", "extra": 6000}, {"name": "L", "extra": 10000}]}'::jsonb
),
(
    2,
    'Freeze Trà Xanh Matcha',
    'Đá xay matcha Nhật Bản béo mịn kèm thạch trà xanh và kem tươi whipping.',
    59000,
    30,
    'https://images.unsplash.com/photo-1536256263959-770b48d82b0a?w=500',
    TRUE,
    380,
    ARRAY['Đá xay'],
    '{"sizes": [{"name": "M", "extra": 0}, {"name": "L", "extra": 10000}]}'::jsonb
),

-- === QUÁN 2: PHÚC LONG COFFEE & TEA (Item ID 6..10) ===
(
    3,
    'Trà Sữa Phúc Long Truyền Thống',
    'Trà đen ủ đậm vị đặc trưng của Phúc Long hòa quyện sữa tươi béo ngậy.',
    55000,
    60,
    'https://images.unsplash.com/photo-1558857563-b371033873b8?w=500',
    TRUE,
    320,
    ARRAY['Best Seller'],
    '{"sizes": [{"name": "M", "extra": 0}, {"name": "L", "extra": 10000}], "sugar": ["30%", "50%", "70%", "100%"], "toppings": [{"name": "Trân châu trắng", "price": 10000}, {"name": "Pudding trứng", "price": 12000}]}'::jsonb
),
(
    3,
    'Trà Sữa Ô Long Sữa Tươi',
    'Hương trà Ô Long nướng thơm dịu, hậu vị thanh ngọt tự nhiên.',
    55000,
    45,
    'https://images.unsplash.com/photo-1576092768241-dec231879fc3?w=500',
    TRUE,
    290,
    ARRAY['Ít đường'],
    '{"sizes": [{"name": "M", "extra": 0}, {"name": "L", "extra": 10000}]}'::jsonb
),
(
    4,
    'Trà Vải Lài Phúc Long',
    'Trà hoa lài ướp hương tự nhiên kết hợp trái vải ngâm giòn ngọt.',
    50000,
    50,
    'https://images.unsplash.com/photo-1556679343-c7306c1976bc?w=500',
    TRUE,
    170,
    ARRAY['Healthy', 'Ít calo'],
    '{"sizes": [{"name": "M", "extra": 0}, {"name": "L", "extra": 10000}]}'::jsonb
),
(
    4,
    'Trà Đào Đá Xay Kem Sữa',
    'Trà đào xay nhuyễn mát lạnh phủ lớp kem phô mai mặn nhẹ.',
    65000,
    25,
    'https://images.unsplash.com/photo-1499638673689-79a0b5115d87?w=500',
    TRUE,
    340,
    ARRAY['Đá xay'],
    '{"sizes": [{"name": "M", "extra": 0}, {"name": "L", "extra": 10000}]}'::jsonb
),
(
    4,
    'Hồng Trà Đác Cam Mật Ong',
    'Hồng trà ủ lạnh ăn kèm hạt đác rim dứa và cam vàng tươi mát.',
    60000,
    35,
    'https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?w=500',
    TRUE,
    220,
    ARRAY['Healthy', 'Best Seller'],
    '{"sizes": [{"name": "M", "extra": 0}, {"name": "L", "extra": 10000}]}'::jsonb
),

-- === QUÁN 3: PIZZA 4P'S BẾN THÀNH (Item ID 11..15) ===
(
    5,
    'Pizza Burrata Parma Ham & Rau Rocket',
    'Pizza nướng lò củi với nguyên khối phô mai tươi Burrata làm thủ công tại Đà Lạt và thịt nguội Parma Ham.',
    298000,
    20,
    'https://images.unsplash.com/photo-1513104890138-7c749659a591?w=500',
    TRUE,
    780,
    ARRAY['Signature', 'Giàu đạm'],
    '{"crust": ["Đế mỏng giòn", "Đế truyền thống"], "extra_cheese": [{"name": "Thêm phô mai Mozzarella", "price": 45000}]}'::jsonb
),
(
    5,
    'Pizza 4 Cheese (4 Loại Phô Mai Mật Ong)',
    'Sự hòa quyện của Camembert, Mozzarella, Gorgonzola và Parmesan dùng kèm mật ong rừng.',
    258000,
    25,
    'https://images.unsplash.com/photo-1574071318508-1cdbab80d002?w=500',
    TRUE,
    820,
    ARRAY['Best Seller', 'Chay'],
    '{"crust": ["Đế mỏng giòn", "Đế truyền thống"]}'::jsonb
),
(
    5,
    'Pizza Cá Hồi Teriyaki Kiểu Nhật',
    'Cá hồi nướng sốt Teriyaki, rong biển và phô mai tươi tan chảy.',
    268000,
    18,
    'https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?w=500',
    TRUE,
    740,
    ARRAY['Omega-3'],
    '{"crust": ["Đế truyền thống"]}'::jsonb
),
(
    6,
    'Mỳ Ý Cua Sốt Kem Cà Chua & Phô Mai Ricotta',
    'Mỳ Spaghetti sốt kem cà chua thịt cua tươi ngọt lịm phủ phô mai Ricotta.',
    228000,
    30,
    'https://images.unsplash.com/photo-1563379926898-05f4575a45d8?w=500',
    TRUE,
    650,
    ARRAY['Best Seller'],
    '{"spicy_level": ["Không cay", "Cay nhẹ"]}'::jsonb
),
(
    6,
    'Salad Trái Cây Nhiệt Đới & Phô Mai Burrata',
    'Xoài cát, bơ sáp, cà chua bi, rau xanh hữu cơ trộn sốt chanh dây cùng Burrata.',
    168000,
    25,
    'https://images.unsplash.com/photo-1540420773420-3366772f4999?w=500',
    TRUE,
    310,
    ARRAY['Healthy', 'Chay', 'Ít calo'],
    '{"dressing": ["Sốt chanh dây", "Dầu oliu Balsamic"]}'::jsonb
),

-- === QUÁN 4: CƠM TẤM SÀI GÒN XƯA (Item ID 16..20) ===
(
    7,
    'Cơm Tấm Sườn Bì Chả Trứng Ốp La Đặc Biệt',
    'Dĩa cơm tấm đầy đặn với sườn cốt lết nướng mật ong than hoa, bì thính, chả trứng hấp và trứng ốp la lòng đào.',
    65000,
    70,
    'https://images.unsplash.com/photo-1512058564366-18510be2db19?w=500',
    TRUE,
    690,
    ARRAY['Best Seller', 'No lâu'],
    '{"rice": ["Cơm bình thường", "Thêm cơm (+5k)"], "egg": ["Ốp la lòng đào", "Ốp la chín kỹ"], "fish_sauce": ["Nước mắm kẹo ngọt", "Nước mắm ớt cay"]}'::jsonb
),
(
    7,
    'Cơm Tấm Sườn Nướng Mật Ong',
    'Sườn heo bản lớn ướp gia truyền nướng vàng ươm trên lửa than hồng.',
    48000,
    85,
    'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=500',
    TRUE,
    540,
    ARRAY['Truyền thống'],
    '{"fish_sauce": ["Nước mắm kẹo ngọt", "Nước mắm ớt cay"]}'::jsonb
),
(
    7,
    'Cơm Tấm Đùi Gà Nướng Ngũ Vị',
    'Đùi gà góc tư ướp ngũ vị hương nướng da giòn, thịt mềm mọng nước.',
    52000,
    50,
    'https://images.unsplash.com/photo-1598515214211-89d3c73ae83b?w=500',
    TRUE,
    580,
    ARRAY['Giàu đạm'],
    '{"fish_sauce": ["Nước mắm kẹo ngọt", "Nước mắm ớt cay"]}'::jsonb
),
(
    8,
    'Canh Khổ Qua Nhồi Thịt Hầm Xương',
    'Khổ qua tươi nhồi thịt heo bằm và mộc nhĩ, nước dùng ngọt thanh giải nhiệt.',
    20000,
    40,
    'https://images.unsplash.com/photo-1547592166-23ac45744acd?w=500',
    TRUE,
    140,
    ARRAY['Healthy', 'Giải nhiệt'],
    '{}'::jsonb
),
(
    8,
    'Chả Trứng Hấp Nấm Hương (Phần Thêm)',
    'Miếng chả trứng thịt bằm, bún tàu, nấm mèo béo thơm.',
    15000,
    60,
    'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=500',
    TRUE,
    160,
    ARRAY['Món thêm'],
    '{}'::jsonb
);

-- =============================================================================
-- 5. SEED BẢNG vouchers (5 MÃ GIẢM GIÁ: TOÀN SÀN, FREESHIP, RIÊNG QUÁN, FLASH SALE CÒN 1 LƯỢT)
-- =============================================================================
INSERT INTO vouchers (
    code, discount_percent, max_discount_amount, min_order_amount,
    start_date, end_date, usage_limit, used_count,
    voucher_type, restaurant_id, points_cost
) VALUES
(
    'FOODFLOW20',
    20, 50000, 100000,
    CURRENT_TIMESTAMP - INTERVAL '10 days',
    CURRENT_TIMESTAMP + INTERVAL '60 days',
    100, 12,
    'DISCOUNT', NULL, 0
),
(
    'FREESHIP15K',
    100, 15000, 50000,
    CURRENT_TIMESTAMP - INTERVAL '10 days',
    CURRENT_TIMESTAMP + INTERVAL '60 days',
    200, 45,
    'FREESHIP', NULL, 0
),
(
    'HIGHLANDS30',
    30, 40000, 80000,
    CURRENT_TIMESTAMP - INTERVAL '5 days',
    CURRENT_TIMESTAMP + INTERVAL '30 days',
    50, 10,
    'DISCOUNT', 1, 100
),
(
    'FLASH50_LAST1',
    50, 60000, 80000,
    CURRENT_TIMESTAMP - INTERVAL '1 day',
    CURRENT_TIMESTAMP + INTERVAL '15 days',
    10, 9, -- [QUAN TRỌNG] CHỈ CÒN ĐÚNG 1 LƯỢT CUỐI CÙNG (9/10) ĐỂ DEMO TRANH CHẤP VOUCHER
    'DISCOUNT', NULL, 0
),
(
    'VIPDIAMOND40',
    40, 100000, 200000,
    CURRENT_TIMESTAMP - INTERVAL '5 days',
    CURRENT_TIMESTAMP + INTERVAL '90 days',
    30, 5,
    'DISCOUNT', NULL, 500
);

-- =============================================================================
-- 6. SEED 3 ĐƠN HÀNG MẪU (orders, order_details, audit_logs) ĐỂ TEST APP MOBILE NGAY
-- =============================================================================
INSERT INTO orders (
    customer_id, restaurant_id, shipper_id, voucher_id,
    subtotal, shipping_fee, discount_amount, total_amount,
    order_status, qr_verify_token, delivery_address, delivery_lat, delivery_lng,
    created_at, vat_amount, payment_method, take_utensils, is_group_order,
    restaurant_rating, shipper_rating, review_comment
) VALUES
-- Đơn hàng 1: Đang chờ quán xác nhận (PENDING) tại Highlands Coffee
(
    1, 1, NULL, 2,
    133000, 15000, 15000, 133000,
    'PENDING', 'FF_QR_DEMO00000001',
    '123 Nguyễn Thị Minh Khai, Phường Bến Thành, Quận 1, TP.HCM',
    10.7756, 106.6924,
    CURRENT_TIMESTAMP - INTERVAL '15 minutes',
    10640, 'VIETQR', TRUE, FALSE,
    NULL, NULL, NULL
),
-- Đơn hàng 2: Tài xế đang giao (DELIVERING) từ Phúc Long -> sẵn sàng để quét QR hoàn tất!
(
    2, 2, 8, 1,
    165000, 15000, 33000, 147000,
    'DELIVERING', 'FF_QR_DEMO00000002',
    '45 Lê Thánh Tôn, Phường Bến Nghé, Quận 1, TP.HCM',
    10.7782, 106.7012,
    CURRENT_TIMESTAMP - INTERVAL '40 minutes',
    13200, 'MOMO', FALSE, FALSE,
    NULL, NULL, NULL
),
-- Đơn hàng 3: Đã hoàn tất (COMPLETED) tại Pizza 4P's -> lên biểu đồ doanh thu MPAndroidChart!
(
    3, 3, 8, 5,
    526000, 15000, 100000, 441000,
    'COMPLETED', 'FF_QR_DEMO00000003',
    '88 Đồng Khởi, Phường Bến Nghé, Quận 1, TP.HCM',
    10.7745, 106.7035,
    CURRENT_TIMESTAMP - INTERVAL '2 hours',
    42080, 'VIETQR', TRUE, TRUE,
    5, 5, 'Pizza Burrata nóng hổi, phô mai tươi cực ngon, anh tài xế giao siêu nhanh!'
);

-- Chi tiết món ăn của 3 đơn hàng mẫu
INSERT INTO order_details (
    order_id, item_id, quantity, unit_price, subtotal,
    selected_options, kitchen_note, group_member_name
) VALUES
-- Chi tiết Đơn 1 (Highlands Coffee)
(1, 2, 2, 49000, 98000, '{"size": "M", "sugar": "50%", "ice": "Ít đá"}'::jsonb, 'Ít ngọt giúp mình nhé', 'Trung'),
(1, 4, 1, 35000, 35000, '{"size": "S"}'::jsonb, 'Nhiều sữa', 'Trung'),

-- Chi tiết Đơn 2 (Phúc Long)
(2, 6, 2, 55000, 110000, '{"size": "L", "sugar": "70%", "topping": "Trân châu trắng"}'::jsonb, NULL, 'Lan'),
(2, 7, 1, 55000, 55000, '{"size": "M", "sugar": "50%"}'::jsonb, 'Đá để riêng', 'Lan'),

-- Chi tiết Đơn 3 (Pizza 4P's)
(3, 11, 1, 298000, 298000, '{"crust": "Đế mỏng giòn"}'::jsonb, 'Cắt làm 8 miếng', 'Minh'),
(3, 14, 1, 228000, 228000, '{"spicy_level": "Không cay"}'::jsonb, NULL, 'Hương');

-- Lịch sử truy vết trạng thái (audit_logs) của 3 đơn hàng mẫu
INSERT INTO audit_logs (order_id, old_status, new_status, changed_by_user_id, note, changed_at) VALUES
-- Log Đơn 1
(1, NULL, 'PENDING', 1, 'Khách hàng đặt đơn mới qua ứng dụng Android', CURRENT_TIMESTAMP - INTERVAL '15 minutes'),

-- Log Đơn 2 (Đã qua 4 bước: PENDING -> ACCEPTED -> COOKING -> DELIVERING)
(2, NULL, 'PENDING', 2, 'Khách hàng đặt đơn mới', CURRENT_TIMESTAMP - INTERVAL '40 minutes'),
(2, 'PENDING', 'ACCEPTED', 5, 'Chủ quán Phúc Long đã nhận đơn', CURRENT_TIMESTAMP - INTERVAL '35 minutes'),
(2, 'ACCEPTED', 'COOKING', 5, 'Bếp đang pha chế thức uống', CURRENT_TIMESTAMP - INTERVAL '30 minutes'),
(2, 'COOKING', 'DELIVERING', 8, 'Tài xế Phạm Văn Nam đã nhận món và đang giao', CURRENT_TIMESTAMP - INTERVAL '15 minutes'),

-- Log Đơn 3 (Đủ 5 bước hoàn chỉnh: PENDING -> ACCEPTED -> COOKING -> DELIVERING -> COMPLETED)
(3, NULL, 'PENDING', 3, 'Khách hàng đặt đơn nhóm (Group Order)', CURRENT_TIMESTAMP - INTERVAL '2 hours'),
(3, 'PENDING', 'ACCEPTED', 6, 'Nhà hàng Pizza 4P''s đã xác nhận đơn', CURRENT_TIMESTAMP - INTERVAL '110 minutes'),
(3, 'ACCEPTED', 'COOKING', 6, 'Đang nướng Pizza trong lò củi', CURRENT_TIMESTAMP - INTERVAL '100 minutes'),
(3, 'COOKING', 'DELIVERING', 8, 'Tài xế đang trên đường giao hàng', CURRENT_TIMESTAMP - INTERVAL '80 minutes'),
(3, 'DELIVERING', 'COMPLETED', 8, 'Quét mã QR FF_QR_DEMO00000003 xác nhận giao hàng thành công', CURRENT_TIMESTAMP - INTERVAL '65 minutes');

DO $$
BEGIN
    RAISE NOTICE '=== [TV1 - TRUNG] ĐÃ NẠP THÀNH CÔNG DỮ LIỆU MẪU (8 USERS, 4 QUÁN, 20 MÓN, 5 VOUCHER, 3 ĐƠN) ===';
END $$;
