-- =============================================================================
-- ĐỒ ÁN GỘP 2 MÔN: DATABASE PROGRAMMING & MOBILE APPLICATION PROGRAMMING
-- ĐỀ TÀI: HỆ SINH THÁI ĐẶT ĐỒ ĂN & QUẢN LÝ F&B (SMART FOOD DELIVERY / FOODFLOW)
-- PHÂN HỆ: THÀNH VIÊN 1 (TV1 - DATABASE ARCHITECT: TRUNG)
-- FILE 03: 03_indexes_and_explain.sql
-- Nội dung:
--   - BƯỚC 1: Xóa Index cũ (nếu có) & Bơm 10.000 đơn hàng giả lập (~25.000 dòng chi tiết)
--   - BƯỚC 2: Đo hiệu năng EXPLAIN (ANALYZE, BUFFERS) TRƯỚC khi đánh Index (Seq Scan)
--   - BƯỚC 3: Tạo 4 Composite B-Tree Indexes & Đo lại EXPLAIN (ANALYZE, BUFFERS) (Index Scan)
-- =============================================================================
-- HƯỚNG DẪN CHẠY TRÊN PGADMIN 4 ĐỂ CHỤP ẢNH BÁO CÁO:
--   1. Bôi đen toàn bộ [BƯỚC 1] -> Bấm F5 (Execute) để bơm 10.000 đơn hàng.
--   2. Bôi đen từng câu lệnh trong [BƯỚC 2] -> Bấm F5 -> Chụp ảnh màn hình kết quả "Seq Scan".
--   3. Bôi đen phần Tạo Index và từng câu lệnh trong [BƯỚC 3] -> Bấm F5 -> Chụp ảnh "Index Scan".
-- =============================================================================

SET client_encoding = 'UTF8';

-- =============================================================================
-- BƯỚC 1: XÓA INDEX CŨ (NẾU CÓ) & BƠM 10.000 ĐƠN HÀNG GIẢ LẬP (+25.000 ORDER_DETAILS)
-- (Hãy bôi đen từ đây đến hết lệnh SELECT COUNT(*) của Bước 1 rồi bấm F5)
-- =============================================================================

DROP INDEX IF EXISTS idx_orders_restaurant_status_date;
DROP INDEX IF EXISTS idx_orders_customer_date;
DROP INDEX IF EXISTS idx_order_details_item_id;
DROP INDEX IF EXISTS idx_menu_items_category_available;

-- Tạm tắt User Triggers (nếu TV2 đã chạy file 04) trong lúc bơm 10.000 dòng lịch sử
-- để tốc độ bơm cực nhanh và không bị chặn bởi trigger giới hạn đơn của Shipper
ALTER TABLE orders DISABLE TRIGGER USER;

DO $$
DECLARE
    v_i INT;
    v_group_idx INT;
    v_slot INT;
    v_cust_id INT;
    v_rest_id INT;
    v_status VARCHAR(20);
    v_payment VARCHAR(20);
    v_created_at TIMESTAMP;
    v_subtotal NUMERIC(12,2);
    v_shipping NUMERIC(12,2);
    v_discount NUMERIC(12,2);
    v_total NUMERIC(12,2);
    v_new_order_id INT;
    v_base_item_id INT;
    v_pool_size INT;
    v_num_items INT;
    v_j INT;
    v_item_offset INT;
    v_item_id INT;
    v_qty INT;
    v_price NUMERIC(12,2);
    v_prices NUMERIC[];
    v_payments VARCHAR[] := ARRAY['COD', 'VIETQR', 'MOMO', 'ZALOPAY'];
BEGIN
    -- -------------------------------------------------------------------------
    -- CƠ CHẾ TỰ ĐỘNG KHỞI TẠO DỮ LIỆU NỀN ĐẦY ĐỦ 100% (AUTO-SEED GUARD):
    -- Nếu người chạy bấm chạy file 03 trước file 06_seed_data.sql (hoặc thiếu vouchers,
    -- thiếu 3 đơn mẫu FF_QR_DEMO, thiếu cột UI), khối lệnh này sẽ tự động nạp đầy đủ
    -- 8 users, 4 quán, 8 danh mục, 20 món ăn (kèm ảnh & options_config), 5 vouchers,
    -- 3 đơn hàng mẫu và 10 dòng audit_logs đồng nhất 100% với 06_seed_data.sql!
    -- -------------------------------------------------------------------------
    IF (SELECT COUNT(*) FROM users) < 8
       OR (SELECT COUNT(*) FROM menu_items) < 20
       OR (SELECT COUNT(*) FROM vouchers) < 5
       OR (SELECT COUNT(*) FROM orders WHERE qr_verify_token LIKE 'FF_QR_DEMO%') < 3
       OR EXISTS (SELECT 1 FROM menu_items WHERE image_url IS NULL)
    THEN
        RAISE NOTICE 'Phát hiện dữ liệu nền chưa đầy đủ -> Đang tự động nạp trọn bộ Seed Data chuẩn (8 users, 4 quán, 20 món, 5 vouchers, 3 đơn mẫu)...';

        TRUNCATE TABLE audit_logs, order_details, orders, vouchers, menu_items, categories, restaurants, users
        RESTART IDENTITY CASCADE;

        INSERT INTO users (
            username, password_hash, full_name, phone, role,
            loyalty_points, member_tier, saved_addresses, favorite_restaurant_ids
        ) VALUES
        (
            'khach_trung',
            '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy',
            'Nguyễn Quốc Trung', '0901234561', 'CUSTOMER', 1250, 'GOLD',
            '[{"label": "Nhà riêng", "address": "123 Nguyễn Thị Minh Khai, Quận 1, TP.HCM", "lat": 10.7756, "lng": 106.6924}, {"label": "Công ty", "address": "Bitexco Financial Tower, 2 Hải Triều, Quận 1, TP.HCM", "lat": 10.7717, "lng": 106.7044}]'::jsonb,
            ARRAY[1, 2, 3]
        ),
        (
            'khach_lan',
            '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy',
            'Trần Ngọc Lan', '0901234562', 'CUSTOMER', 450, 'SILVER',
            '[{"label": "Nhà riêng", "address": "45 Lê Thánh Tôn, Quận 1, TP.HCM", "lat": 10.7782, "lng": 106.7012}]'::jsonb,
            ARRAY[1, 4]
        ),
        (
            'khach_minh',
            '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy',
            'Lê Hoàng Minh', '0901234563', 'CUSTOMER', 3200, 'DIAMOND',
            '[{"label": "Chung cư", "address": "88 Đồng Khởi, Quận 1, TP.HCM", "lat": 10.7745, "lng": 106.7035}]'::jsonb,
            ARRAY[2, 3]
        ),
        ('chuquan_highlands', '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy', 'Quản Lý Highlands Nguyễn Huệ', '0901234564', 'RESTAURANT', 0, 'SILVER', '[]'::jsonb, '{}'),
        ('chuquan_phuclong', '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy', 'Quản Lý Phúc Long Lê Lợi', '0901234565', 'RESTAURANT', 0, 'SILVER', '[]'::jsonb, '{}'),
        ('chuquan_pizza4ps', '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy', 'Quản Lý Pizza 4Ps Bến Thành', '0901234566', 'RESTAURANT', 0, 'SILVER', '[]'::jsonb, '{}'),
        ('chuquan_comtam', '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy', 'Cô Ba Cơm Tấm Sài Gòn', '0901234567', 'RESTAURANT', 0, 'SILVER', '[]'::jsonb, '{}'),
        ('shipper_nam', '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy', 'Phạm Văn Nam (Tài xế 5 Sao)', '0901234568', 'SHIPPER', 0, 'SILVER', '[]'::jsonb, '{}');

        INSERT INTO restaurants (
            owner_id, name, address, latitude, longitude,
            open_time, close_time, is_active,
            brand_name, cover_image_url, rating, is_freeship, prep_time_minutes
        ) VALUES
        (4, 'Highlands Coffee - Phố Đi Bộ Nguyễn Huệ', '75 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP.HCM', 10.7738, 106.7041, '06:30:00', '23:00:00', TRUE, 'Highlands Coffee', 'https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=800', 4.85, TRUE, 10),
        (5, 'Phúc Long Coffee & Tea - Lê Lợi', '63 Lê Lợi, Phường Bến Nghé, Quận 1, TP.HCM', 10.7725, 106.6980, '07:00:00', '22:30:00', TRUE, 'Phúc Long', 'https://images.unsplash.com/photo-1556679343-c7306c1976bc?w=800', 4.90, TRUE, 12),
        (6, 'Pizza 4P''s - Bến Thành', '8 Thủ Khoa Huân, Phường Bến Thành, Quận 1, TP.HCM', 10.7731, 106.6968, '10:00:00', '22:00:00', TRUE, 'Pizza 4P''s', 'https://images.unsplash.com/photo-1513104890138-7c749659a591?w=800', 4.95, FALSE, 20),
        (7, 'Cơm Tấm Sài Gòn Xưa - Quận 3', '182 Võ Văn Tần, Phường 5, Quận 3, TP.HCM', 10.7797, 106.6882, '06:00:00', '21:30:00', TRUE, 'Cơm Tấm Sài Gòn', 'https://images.unsplash.com/photo-1512058564366-18510be2db19?w=800', 4.75, TRUE, 15);

        INSERT INTO categories (restaurant_id, name, display_order) VALUES
        (1, 'Trà Trái Cây & Trà Sen Vàng', 1), (1, 'Cà Phê Phin & Freeze Đá Xay', 2),
        (2, 'Trà Sữa Đậm Vị Phúc Long', 1), (2, 'Trà Trái Cây Nhiệt Đới', 2),
        (3, 'Pizza Nướng Củi Thủ Công', 1), (3, 'Mỳ Ý & Khai Vị Phô Mai', 2),
        (4, 'Cơm Tấm Sườn Nướng Than Hoa', 1), (4, 'Canh & Món Thêm Đặc Sắc', 2);

        INSERT INTO menu_items (
            category_id, name, description, price, stock_quantity, image_url, is_available,
            calories, dietary_tags, options_config
        ) VALUES
        (1, 'Trà Sen Vàng (Đặc Biệt)', 'Trà Ô Long kết hợp hạt sen bùi bùi, củ năng giòn ngọt và lớp kem phô mai macchiato béo ngậy. (Món Demo Race Condition: Tồn kho = 1)', 49000, 1, 'https://images.unsplash.com/photo-1544787219-7f47ccb76574?w=500', TRUE, 240, ARRAY['Best Seller', 'Ít đường'], '{"sizes": [{"name": "S", "extra": 0}, {"name": "M", "extra": 6000}, {"name": "L", "extra": 10000}], "sugar": ["30%", "50%", "70%", "100%"], "ice": ["Ít đá", "Bình thường", "Đá riêng"], "toppings": [{"name": "Thêm hạt sen", "price": 10000}, {"name": "Thêm củ năng", "price": 10000}]}'::jsonb),
        (1, 'Trà Thạch Đào Cam Sả', 'Vị trà đào thanh mát hòa quyện cùng miếng đào giòn và thạch đào thơm lừng.', 49000, 50, 'https://images.unsplash.com/photo-1556679343-c7306c1976bc?w=500', TRUE, 190, ARRAY['Healthy', 'Trái cây tươi'], '{"sizes": [{"name": "S", "extra": 0}, {"name": "M", "extra": 6000}, {"name": "L", "extra": 10000}], "sugar": ["50%", "70%", "100%"], "ice": ["Ít đá", "Bình thường"]}'::jsonb),
        (1, 'Trà Thạch Vải Nhiệt Đới', 'Trà đen ủ lạnh kết hợp trái vải mọng nước và thạch vải giòn dai.', 45000, 40, 'https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?w=500', TRUE, 185, ARRAY['Giải nhiệt'], '{"sizes": [{"name": "S", "extra": 0}, {"name": "M", "extra": 6000}]}'::jsonb),
        (2, 'Phin Sữa Đá Đậm Đà', 'Cà phê phin truyền thống Việt Nam pha cùng sữa đặc ngọt dịu.', 35000, 80, 'https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?w=500', TRUE, 210, ARRAY['Tỉnh táo'], '{"sizes": [{"name": "S", "extra": 0}, {"name": "M", "extra": 6000}, {"name": "L", "extra": 10000}]}'::jsonb),
        (2, 'Freeze Trà Xanh Matcha', 'Đá xay matcha Nhật Bản béo mịn kèm thạch trà xanh và kem tươi whipping.', 59000, 30, 'https://images.unsplash.com/photo-1536256263959-770b48d82b0a?w=500', TRUE, 380, ARRAY['Đá xay'], '{"sizes": [{"name": "M", "extra": 0}, {"name": "L", "extra": 10000}]}'::jsonb),
        (3, 'Trà Sữa Phúc Long Truyền Thống', 'Trà đen ủ đậm vị đặc trưng của Phúc Long hòa quyện sữa tươi béo ngậy.', 55000, 60, 'https://images.unsplash.com/photo-1558857563-b371033873b8?w=500', TRUE, 320, ARRAY['Best Seller'], '{"sizes": [{"name": "M", "extra": 0}, {"name": "L", "extra": 10000}], "sugar": ["30%", "50%", "70%", "100%"], "toppings": [{"name": "Trân châu trắng", "price": 10000}, {"name": "Pudding trứng", "price": 12000}]}'::jsonb),
        (3, 'Trà Sữa Ô Long Sữa Tươi', 'Hương trà Ô Long nướng thơm dịu, hậu vị thanh ngọt tự nhiên.', 55000, 45, 'https://images.unsplash.com/photo-1576092768241-dec231879fc3?w=500', TRUE, 290, ARRAY['Ít đường'], '{"sizes": [{"name": "M", "extra": 0}, {"name": "L", "extra": 10000}]}'::jsonb),
        (4, 'Trà Vải Lài Phúc Long', 'Trà hoa lài ướp hương tự nhiên kết hợp trái vải ngâm giòn ngọt.', 50000, 50, 'https://images.unsplash.com/photo-1556679343-c7306c1976bc?w=500', TRUE, 170, ARRAY['Healthy', 'Ít calo'], '{"sizes": [{"name": "M", "extra": 0}, {"name": "L", "extra": 10000}]}'::jsonb),
        (4, 'Trà Đào Đá Xay Kem Sữa', 'Trà đào xay nhuyễn mát lạnh phủ lớp kem phô mai mặn nhẹ.', 65000, 25, 'https://images.unsplash.com/photo-1499638673689-79a0b5115d87?w=500', TRUE, 340, ARRAY['Đá xay'], '{"sizes": [{"name": "M", "extra": 0}, {"name": "L", "extra": 10000}]}'::jsonb),
        (4, 'Hồng Trà Đác Cam Mật Ong', 'Hồng trà ủ lạnh ăn kèm hạt đác rim dứa và cam vàng tươi mát.', 60000, 35, 'https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?w=500', TRUE, 220, ARRAY['Healthy', 'Best Seller'], '{"sizes": [{"name": "M", "extra": 0}, {"name": "L", "extra": 10000}]}'::jsonb),
        (5, 'Pizza Burrata Parma Ham & Rau Rocket', 'Pizza nướng lò củi với nguyên khối phô mai tươi Burrata làm thủ công tại Đà Lạt và thịt nguội Parma Ham.', 298000, 20, 'https://images.unsplash.com/photo-1513104890138-7c749659a591?w=500', TRUE, 780, ARRAY['Signature', 'Giàu đạm'], '{"crust": ["Đế mỏng giòn", "Đế truyền thống"], "extra_cheese": [{"name": "Thêm phô mai Mozzarella", "price": 45000}]}'::jsonb),
        (5, 'Pizza 4 Cheese (4 Loại Phô Mai Mật Ong)', 'Sự hòa quyện của Camembert, Mozzarella, Gorgonzola và Parmesan dùng kèm mật ong rừng.', 258000, 25, 'https://images.unsplash.com/photo-1574071318508-1cdbab80d002?w=500', TRUE, 820, ARRAY['Best Seller', 'Chay'], '{"crust": ["Đế mỏng giòn", "Đế truyền thống"]}'::jsonb),
        (5, 'Pizza Cá Hồi Teriyaki Kiểu Nhật', 'Cá hồi nướng sốt Teriyaki, rong biển và phô mai tươi tan chảy.', 268000, 18, 'https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?w=500', TRUE, 740, ARRAY['Omega-3'], '{"crust": ["Đế truyền thống"]}'::jsonb),
        (6, 'Mỳ Ý Cua Sốt Kem Cà Chua & Phô Mai Ricotta', 'Mỳ Spaghetti sốt kem cà chua thịt cua tươi ngọt lịm phủ phô mai Ricotta.', 228000, 30, 'https://images.unsplash.com/photo-1563379926898-05f4575a45d8?w=500', TRUE, 650, ARRAY['Best Seller'], '{"spicy_level": ["Không cay", "Cay nhẹ"]}'::jsonb),
        (6, 'Salad Trái Cây Nhiệt Đới & Phô Mai Burrata', 'Xoài cát, bơ sáp, cà chua bi, rau xanh hữu cơ trộn sốt chanh dây cùng Burrata.', 168000, 25, 'https://images.unsplash.com/photo-1540420773420-3366772f4999?w=500', TRUE, 310, ARRAY['Healthy', 'Chay', 'Ít calo'], '{"dressing": ["Sốt chanh dây", "Dầu oliu Balsamic"]}'::jsonb),
        (7, 'Cơm Tấm Sườn Bì Chả Trứng Ốp La Đặc Biệt', 'Dĩa cơm tấm đầy đặn với sườn cốt lết nướng mật ong than hoa, bì thính, chả trứng hấp và trứng ốp la lòng đào.', 65000, 70, 'https://images.unsplash.com/photo-1512058564366-18510be2db19?w=500', TRUE, 690, ARRAY['Best Seller', 'No lâu'], '{"rice": ["Cơm bình thường", "Thêm cơm (+5k)"], "egg": ["Ốp la lòng đào", "Ốp la chín kỹ"], "fish_sauce": ["Nước mắm kẹo ngọt", "Nước mắm ớt cay"]}'::jsonb),
        (7, 'Cơm Tấm Sườn Nướng Mật Ong', 'Sườn heo bản lớn ướp gia truyền nướng vàng ươm trên lửa than hồng.', 48000, 85, 'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=500', TRUE, 540, ARRAY['Truyền thống'], '{"fish_sauce": ["Nước mắm kẹo ngọt", "Nước mắm ớt cay"]}'::jsonb),
        (7, 'Cơm Tấm Đùi Gà Nướng Ngũ Vị', 'Đùi gà góc tư ướp ngũ vị hương nướng da giòn, thịt mềm mọng nước.', 52000, 50, 'https://images.unsplash.com/photo-1598515214211-89d3c73ae83b?w=500', TRUE, 580, ARRAY['Giàu đạm'], '{"fish_sauce": ["Nước mắm kẹo ngọt", "Nước mắm ớt cay"]}'::jsonb),
        (8, 'Canh Khổ Qua Nhồi Thịt Hầm Xương', 'Khổ qua tươi nhồi thịt heo bằm và mộc nhĩ, nước dùng ngọt thanh giải nhiệt.', 20000, 40, 'https://images.unsplash.com/photo-1547592166-23ac45744acd?w=500', TRUE, 140, ARRAY['Healthy', 'Giải nhiệt'], '{}'::jsonb),
        (8, 'Chả Trứng Hấp Nấm Hương (Phần Thêm)', 'Miếng chả trứng thịt bằm, bún tàu, nấm mèo béo thơm.', 15000, 60, 'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=500', TRUE, 160, ARRAY['Món thêm'], '{}'::jsonb);

        INSERT INTO vouchers (
            code, discount_percent, max_discount_amount, min_order_amount,
            start_date, end_date, usage_limit, used_count,
            voucher_type, restaurant_id, points_cost
        ) VALUES
        ('FOODFLOW20', 20, 50000, 100000, CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP + INTERVAL '60 days', 100, 12, 'DISCOUNT', NULL, 0),
        ('FREESHIP15K', 100, 15000, 50000, CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP + INTERVAL '60 days', 200, 45, 'FREESHIP', NULL, 0),
        ('HIGHLANDS30', 30, 40000, 80000, CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP + INTERVAL '30 days', 50, 10, 'DISCOUNT', 1, 100),
        ('FLASH50_LAST1', 50, 60000, 80000, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '15 days', 10, 9, 'DISCOUNT', NULL, 0),
        ('VIPDIAMOND40', 40, 100000, 200000, CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP + INTERVAL '90 days', 30, 5, 'DISCOUNT', NULL, 500);

        INSERT INTO orders (
            customer_id, restaurant_id, shipper_id, voucher_id,
            subtotal, shipping_fee, discount_amount, total_amount,
            order_status, qr_verify_token, delivery_address, delivery_lat, delivery_lng,
            created_at, vat_amount, payment_method, take_utensils, is_group_order,
            restaurant_rating, shipper_rating, review_comment
        ) VALUES
        (1, 1, NULL, 2, 133000, 15000, 15000, 133000, 'PENDING', 'FF_QR_DEMO00000001', '123 Nguyễn Thị Minh Khai, Phường Bến Thành, Quận 1, TP.HCM', 10.7756, 106.6924, CURRENT_TIMESTAMP - INTERVAL '15 minutes', 10640, 'VIETQR', TRUE, FALSE, NULL, NULL, NULL),
        (2, 2, 8, 1, 165000, 15000, 33000, 147000, 'DELIVERING', 'FF_QR_DEMO00000002', '45 Lê Thánh Tôn, Phường Bến Nghé, Quận 1, TP.HCM', 10.7782, 106.7012, CURRENT_TIMESTAMP - INTERVAL '40 minutes', 13200, 'MOMO', FALSE, FALSE, NULL, NULL, NULL),
        (3, 3, 8, 5, 526000, 15000, 100000, 441000, 'COMPLETED', 'FF_QR_DEMO00000003', '88 Đồng Khởi, Phường Bến Nghé, Quận 1, TP.HCM', 10.7745, 106.7035, CURRENT_TIMESTAMP - INTERVAL '2 hours', 42080, 'VIETQR', TRUE, TRUE, 5, 5, 'Pizza Burrata nóng hổi, phô mai tươi cực ngon, anh tài xế giao siêu nhanh!');

        INSERT INTO order_details (
            order_id, item_id, quantity, unit_price, subtotal,
            selected_options, kitchen_note, group_member_name
        ) VALUES
        (1, 2, 2, 49000, 98000, '{"size": "M", "sugar": "50%", "ice": "Ít đá"}'::jsonb, 'Ít ngọt giúp mình nhé', 'Trung'),
        (1, 4, 1, 35000, 35000, '{"size": "S"}'::jsonb, 'Nhiều sữa', 'Trung'),
        (2, 6, 2, 55000, 110000, '{"size": "L", "sugar": "70%", "topping": "Trân châu trắng"}'::jsonb, NULL, 'Lan'),
        (2, 7, 1, 55000, 55000, '{"size": "M", "sugar": "50%"}'::jsonb, 'Đá để riêng', 'Lan'),
        (3, 11, 1, 298000, 298000, '{"crust": "Đế mỏng giòn"}'::jsonb, 'Cắt làm 8 miếng', 'Minh'),
        (3, 14, 1, 228000, 228000, '{"spicy_level": "Không cay"}'::jsonb, NULL, 'Hương');

        INSERT INTO audit_logs (order_id, old_status, new_status, changed_by_user_id, note, changed_at) VALUES
        (1, NULL, 'PENDING', 1, 'Khách hàng đặt đơn mới qua ứng dụng Android', CURRENT_TIMESTAMP - INTERVAL '15 minutes'),
        (2, NULL, 'PENDING', 2, 'Khách hàng đặt đơn mới', CURRENT_TIMESTAMP - INTERVAL '40 minutes'),
        (2, 'PENDING', 'ACCEPTED', 5, 'Chủ quán Phúc Long đã nhận đơn', CURRENT_TIMESTAMP - INTERVAL '35 minutes'),
        (2, 'ACCEPTED', 'COOKING', 5, 'Bếp đang pha chế thức uống', CURRENT_TIMESTAMP - INTERVAL '30 minutes'),
        (2, 'COOKING', 'DELIVERING', 8, 'Tài xế Phạm Văn Nam đã nhận món và đang giao', CURRENT_TIMESTAMP - INTERVAL '15 minutes'),
        (3, NULL, 'PENDING', 3, 'Khách hàng đặt đơn nhóm (Group Order)', CURRENT_TIMESTAMP - INTERVAL '2 hours'),
        (3, 'PENDING', 'ACCEPTED', 6, 'Nhà hàng Pizza 4P''s đã xác nhận đơn', CURRENT_TIMESTAMP - INTERVAL '110 minutes'),
        (3, 'ACCEPTED', 'COOKING', 6, 'Đang nướng Pizza trong lò củi', CURRENT_TIMESTAMP - INTERVAL '100 minutes'),
        (3, 'COOKING', 'DELIVERING', 8, 'Tài xế đang trên đường giao hàng', CURRENT_TIMESTAMP - INTERVAL '80 minutes'),
        (3, 'DELIVERING', 'COMPLETED', 8, 'Quét mã QR FF_QR_DEMO00000003 xác nhận giao hàng thành công', CURRENT_TIMESTAMP - INTERVAL '65 minutes');
    END IF;

    -- Xóa dữ liệu bơm thử nghiệm cũ (nếu chạy lại nhiều lần) để luôn giữ đúng 10.000 đơn giả lập + 3 đơn mẫu
    DELETE FROM orders WHERE qr_verify_token LIKE 'FF_BULK_%';

    -- Đọc mảng giá thực tế từ bảng menu_items (20 món) để đơn giá order_details khớp 100% thực đơn
    SELECT array_agg(price ORDER BY id) INTO v_prices FROM menu_items;

    RAISE NOTICE 'Đang tiến hành bơm 10.000 đơn hàng và 25.000 chi tiết đơn hàng chuẩn hóa...';

    FOR v_i IN 1..10000 LOOP
        -- Phân bổ đều khách hàng (ID 1..3) và nhà hàng (ID 1..4) không bị khóa pha
        v_cust_id := 1 + (v_i % 3);
        v_rest_id := 1 + ((v_i - 1) % 4);
        v_group_idx := (v_i - 1) / 4; -- Từ 0 đến 2499 cho mỗi nhà hàng

        -- Phân bổ trạng thái & thời gian thực tế:
        -- 1) Đơn 1..9600: Lịch sử từ 8 đến 180 ngày trước (80% COMPLETED, 20% CANCELLED)
        -- 2) Đơn 9601..9968 (92 đơn/quán): Rải đều 7 ngày gần nhất để biểu đồ MPAndroidChart 7 ngày luôn đầy đặn
        -- 3) Đơn 9969..10000 (8 đơn/quán): Trong 48 giờ gần nhất, mỗi quán đều có đủ PENDING, ACCEPTED, COOKING, COMPLETED
        --    (Không gán DELIVERING trong tập bulk để shipper_nam chỉ giữ đúng 1 đơn DELIVERING mẫu #2,
        --     đảm bảo Trigger 3 chặn quá tải >= 2 đơn của TV2 hoạt động chuẩn xác khi test trên App/API)
        IF v_i <= 9600 THEN
            v_status := CASE WHEN ((v_group_idx + v_rest_id) % 5 = 0) THEN 'CANCELLED' ELSE 'COMPLETED' END;
            v_created_at := CURRENT_TIMESTAMP - ((8 + (v_group_idx % 173)) || ' days')::INTERVAL
                                              - (((v_i * 7) % 1440) || ' minutes')::INTERVAL;
        ELSIF v_i <= 9968 THEN
            v_status := CASE WHEN ((v_group_idx + v_rest_id) % 6 = 0) THEN 'CANCELLED' ELSE 'COMPLETED' END;
            v_created_at := CURRENT_TIMESTAMP - ((v_group_idx % 7) || ' days')::INTERVAL
                                              - (((v_i * 11) % 720) || ' minutes')::INTERVAL;
        ELSE
            v_slot := (v_i - 9969) / 4; -- 0..7 cho mỗi nhà hàng
            IF v_slot IN (0, 1, 2) THEN
                v_status := 'PENDING';
            ELSIF v_slot IN (3, 4) THEN
                v_status := 'ACCEPTED';
            ELSIF v_slot IN (5, 6) THEN
                v_status := 'COOKING';
            ELSE
                v_status := 'COMPLETED';
            END IF;
            v_created_at := CURRENT_TIMESTAMP - ((1 + v_slot * 4 + v_rest_id) || ' hours')::INTERVAL
                                              - ((v_i % 50) || ' minutes')::INTERVAL;
        END IF;

        v_payment := v_payments[1 + ((v_group_idx + v_rest_id) % 4)];
        v_shipping := CASE WHEN (v_group_idx % 3 = 0) THEN 15000 ELSE 25000 END;
        v_discount := CASE WHEN (v_group_idx % 4 = 0) THEN 15000 ELSE 0 END;

        -- Mỗi quán có đúng 1.250 đơn 2 món và 1.250 đơn 3 món -> Tổng đúng 25.000 dòng order_details
        v_num_items := 2 + (v_group_idx % 2);
        v_subtotal := 0;

        -- Khởi tạo đơn hàng (Chỉ gán shipper_id = 8 cho đơn COMPLETED để không chạm trần Trigger 3 DELIVERING)
        INSERT INTO orders (
            customer_id, restaurant_id, shipper_id, voucher_id,
            subtotal, shipping_fee, discount_amount, total_amount,
            order_status, qr_verify_token, delivery_address, delivery_lat, delivery_lng,
            created_at, vat_amount, payment_method, take_utensils, is_group_order
        ) VALUES (
            v_cust_id,
            v_rest_id,
            CASE WHEN v_status = 'COMPLETED' THEN 8 ELSE NULL END,
            NULL,
            100000,
            v_shipping,
            v_discount,
            100000 + v_shipping - v_discount,
            v_status,
            'FF_BULK_' || LPAD(v_i::TEXT, 7, '0'),
            'Số ' || (v_i % 500 + 1) || ' Đường Nguyễn Thị Minh Khai, Quận 1, TP.HCM',
            10.7700 + ((v_i % 100) * 0.0002),
            106.6900 + ((v_i % 100) * 0.0002),
            v_created_at,
            8000,
            v_payment,
            (v_group_idx % 2 = 0),
            FALSE
        ) RETURNING id INTO v_new_order_id;

        -- Phân bổ món ăn phủ đều tất cả các món của từng quán:
        -- Quán 1: dùng 4 món (ID 2..5), giữ nguyên món số 1 (Trà Sen Vàng tồn kho = 1) cho Demo Race Condition
        -- Quán 2: dùng đủ 5 món (ID 6..10)
        -- Quán 3: dùng đủ 5 món (ID 11..15)
        -- Quán 4: dùng đủ 5 món (ID 16..20)
        IF v_rest_id = 1 THEN
            v_base_item_id := 2;
            v_pool_size := 4;
        ELSE
            v_base_item_id := (v_rest_id - 1) * 5 + 1;
            v_pool_size := 5;
        END IF;

        FOR v_j IN 0..(v_num_items - 1) LOOP
            v_item_offset := (v_group_idx + v_j) % v_pool_size;
            v_item_id := v_base_item_id + v_item_offset;
            -- Tạo độ chênh lệch sản lượng tự nhiên giữa các món để biểu đồ Top 5 món bán chạy phân bậc rõ ràng
            v_qty := 1 + ((v_group_idx + v_j + (v_pool_size - v_item_offset)) % 3)
                       + CASE WHEN v_item_offset = 0 AND (v_group_idx % 3 = 0) THEN 1 ELSE 0 END;
            v_price := v_prices[v_item_id];

            INSERT INTO order_details (
                order_id, item_id, quantity, unit_price, subtotal, selected_options
            ) VALUES (
                v_new_order_id,
                v_item_id,
                v_qty,
                v_price,
                v_price * v_qty,
                '{"size": "M", "sugar": "70%"}'::jsonb
            );

            v_subtotal := v_subtotal + (v_price * v_qty);
        END LOOP;

        v_total := GREATEST(0, v_subtotal + v_shipping - v_discount);

        UPDATE orders
        SET subtotal = v_subtotal,
            total_amount = v_total,
            vat_amount = ROUND(v_subtotal * 0.08, 2)
        WHERE id = v_new_order_id;
    END LOOP;

    RAISE NOTICE '=== ĐÃ BƠM XONG 10.000 ĐƠN HÀNG & 25.000 CHI TIẾT ĐƠN HÀNG THÀNH CÔNG! ===';
END $$;

-- Bật lại User Triggers trên bảng orders
ALTER TABLE orders ENABLE TRIGGER USER;

-- Cập nhật thống kê cho bộ tối ưu hóa truy vấn (Query Planner) của PostgreSQL
ANALYZE orders, order_details, menu_items, restaurants;

-- Kiểm tra tổng số dòng hiện có sau khi bơm
SELECT
    (SELECT COUNT(*) FROM orders) AS total_orders_rows,
    (SELECT COUNT(*) FROM order_details) AS total_order_details_rows;


-- =============================================================================
-- BƯỚC 2: ĐO HIỆU NĂNG TRƯỚC KHI CÓ INDEX (CHỤP ẢNH MÀN HÌNH 1: "Seq Scan")
-- Hướng dẫn: Bôi đen từng câu lệnh EXPLAIN bên dưới và bấm F5.
-- Quan sát trong tab Output/Explain: PostgreSQL phải quét tuần tự toàn bảng (Seq Scan on orders / order_details).
-- =============================================================================

-- [TRUY VẤN 2.1 - CHƯA CÓ INDEX]: Tìm các đơn hàng đang chờ (PENDING) của Quán 1 trong 3 ngày gần nhất
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, customer_id, total_amount, order_status, created_at
FROM orders
WHERE restaurant_id = 1
  AND order_status = 'PENDING'
  AND created_at >= CURRENT_TIMESTAMP - INTERVAL '3 days'
ORDER BY created_at DESC;

-- [TRUY VẤN 2.2 - CHƯA CÓ INDEX]: Lấy 10 đơn hàng mới nhất của Khách hàng ID = 1 (Màn hình Lịch sử đơn SCR-24)
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, restaurant_id, total_amount, order_status, created_at
FROM orders
WHERE customer_id = 1
ORDER BY created_at DESC
LIMIT 10;

-- [TRUY VẤN 2.3 - CHƯA CÓ INDEX]: Thống kê tổng số lượng bán và doanh thu của món ăn item_id = 2
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    item_id,
    COUNT(order_id) AS total_orders_count,
    SUM(quantity) AS total_quantity_sold,
    SUM(subtotal) AS total_revenue
FROM order_details
WHERE item_id = 2
GROUP BY item_id;


-- =============================================================================
-- BƯỚC 3: TẠO COMPOSITE B-TREE INDEXES & ĐO LẠI HIỆU NĂNG (CHỤP ẢNH MÀN HÌNH 2: "Index Scan")
-- Hướng dẫn:
--   1. Bôi đen khối CREATE INDEX + ANALYZE bên dưới và bấm F5.
--   2. Bôi đen từng câu EXPLAIN (3.1, 3.2, 3.3) và bấm F5 để thấy chuyển sang "Index Scan" / "Bitmap Index Scan"
--      với Execution Time giảm mạnh từ hàng chục ms xuống chỉ còn ~0.05 - 0.30 ms!
-- =============================================================================

-- 1. Composite B-Tree Index tối ưu cho Chủ quán lọc đơn theo trạng thái và thời gian mới nhất
CREATE INDEX IF NOT EXISTS idx_orders_restaurant_status_date
    ON orders (restaurant_id, order_status, created_at DESC);

-- 2. Composite B-Tree Index tối ưu cho Khách hàng xem lịch sử đơn hàng mới nhất (phân trang LIMIT)
CREATE INDEX IF NOT EXISTS idx_orders_customer_date
    ON orders (customer_id, created_at DESC);

-- 3. Composite B-Tree Index tối ưu thống kê món ăn bán chạy (Top-selling items) và JOIN order_details
CREATE INDEX IF NOT EXISTS idx_order_details_item_id
    ON order_details (item_id, order_id);

-- 4. Composite B-Tree Index tối ưu tải thực đơn theo danh mục và trạng thái còn hàng
CREATE INDEX IF NOT EXISTS idx_menu_items_category_available
    ON menu_items (category_id, is_available);

-- Cập nhật thống kê ngay sau khi tạo Index
ANALYZE orders, order_details, menu_items;


-- [TRUY VẤN 3.1 - ĐÃ CÓ B-TREE INDEX]: Tìm đơn PENDING của Quán 1 trong 3 ngày gần nhất -> Ra "Index Scan"
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, customer_id, total_amount, order_status, created_at
FROM orders
WHERE restaurant_id = 1
  AND order_status = 'PENDING'
  AND created_at >= CURRENT_TIMESTAMP - INTERVAL '3 days'
ORDER BY created_at DESC;

-- [TRUY VẤN 3.2 - ĐÃ CÓ B-TREE INDEX]: Lấy 10 đơn hàng mới nhất của Khách hàng ID = 1 -> Ra "Index Scan" cực nhanh
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, restaurant_id, total_amount, order_status, created_at
FROM orders
WHERE customer_id = 1
ORDER BY created_at DESC
LIMIT 10;

-- [TRUY VẤN 3.3 - ĐÃ CÓ B-TREE INDEX]: Thống kê doanh thu của món item_id = 2 -> Ra "Index Scan" / "Bitmap Index Scan"
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    item_id,
    COUNT(order_id) AS total_orders_count,
    SUM(quantity) AS total_quantity_sold,
    SUM(subtotal) AS total_revenue
FROM order_details
WHERE item_id = 2
GROUP BY item_id;
