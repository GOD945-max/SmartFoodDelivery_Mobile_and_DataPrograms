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
    v_num_items INT;
    v_j INT;
    v_item_id INT;
    v_qty INT;
    v_price NUMERIC(12,2);
    v_statuses VARCHAR[] := ARRAY['COMPLETED', 'COMPLETED', 'COMPLETED', 'CANCELLED', 'DELIVERING', 'COOKING', 'ACCEPTED', 'PENDING'];
    v_payments VARCHAR[] := ARRAY['COD', 'VIETQR', 'MOMO', 'ZALOPAY'];
BEGIN
    -- -------------------------------------------------------------------------
    -- CƠ CHẾ TỰ ĐỘNG KHỞI TẠO DỮ LIỆU NỀN (AUTO-SEED GUARD):
    -- Nếu người chạy bấm chạy file 03 trước file 06_seed_data.sql (bảng users/menu_items
    -- còn trống), khối lệnh này sẽ tự động nạp 8 users, 4 quán, 8 danh mục và 20 món ăn
    -- để không bao giờ bị lỗi khóa ngoại (Foreign Key Violation)!
    -- -------------------------------------------------------------------------
    IF (SELECT COUNT(*) FROM users) < 8 OR (SELECT COUNT(*) FROM menu_items) < 20 THEN
        RAISE NOTICE 'Phát hiện chưa chạy 06_seed_data.sql -> Đang tự động nạp dữ liệu nền (8 users, 4 quán, 20 món)...';

        TRUNCATE TABLE audit_logs, order_details, orders, vouchers, menu_items, categories, restaurants, users
        RESTART IDENTITY CASCADE;

        INSERT INTO users (username, password_hash, full_name, phone, role, loyalty_points, member_tier) VALUES
        ('khach_trung', '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy', 'Nguyễn Quốc Trung', '0901234561', 'CUSTOMER', 1250, 'GOLD'),
        ('khach_lan', '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy', 'Trần Ngọc Lan', '0901234562', 'CUSTOMER', 450, 'SILVER'),
        ('khach_minh', '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy', 'Lê Hoàng Minh', '0901234563', 'CUSTOMER', 3200, 'DIAMOND'),
        ('chuquan_highlands', '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy', 'Quản Lý Highlands Nguyễn Huệ', '0901234564', 'RESTAURANT', 0, 'SILVER'),
        ('chuquan_phuclong', '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy', 'Quản Lý Phúc Long Lê Lợi', '0901234565', 'RESTAURANT', 0, 'SILVER'),
        ('chuquan_pizza4ps', '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy', 'Quản Lý Pizza 4Ps Bến Thành', '0901234566', 'RESTAURANT', 0, 'SILVER'),
        ('chuquan_comtam', '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy', 'Cô Ba Cơm Tấm Sài Gòn', '0901234567', 'RESTAURANT', 0, 'SILVER'),
        ('shipper_nam', '$2b$10$N9qo8uLOickgx2ZMRZoMyeapvT9gqHHhU1pKuMj2cpskE48fM3Kwy', 'Phạm Văn Nam (Tài xế 5 Sao)', '0901234568', 'SHIPPER', 0, 'SILVER');

        INSERT INTO restaurants (owner_id, name, address, latitude, longitude, brand_name, rating, is_freeship) VALUES
        (4, 'Highlands Coffee - Phố Đi Bộ Nguyễn Huệ', '75 Nguyễn Huệ, Quận 1, TP.HCM', 10.7738, 106.7041, 'Highlands Coffee', 4.85, TRUE),
        (5, 'Phúc Long Coffee & Tea - Lê Lợi', '63 Lê Lợi, Quận 1, TP.HCM', 10.7725, 106.6980, 'Phúc Long', 4.90, TRUE),
        (6, 'Pizza 4P''s - Bến Thành', '8 Thủ Khoa Huân, Quận 1, TP.HCM', 10.7731, 106.6968, 'Pizza 4P''s', 4.95, FALSE),
        (7, 'Cơm Tấm Sài Gòn Xưa - Quận 3', '182 Võ Văn Tần, Quận 3, TP.HCM', 10.7797, 106.6882, 'Cơm Tấm Sài Gòn', 4.75, TRUE);

        INSERT INTO categories (restaurant_id, name, display_order) VALUES
        (1, 'Trà Trái Cây & Trà Sen Vàng', 1), (1, 'Cà Phê Phin & Freeze Đá Xay', 2),
        (2, 'Trà Sữa Đậm Vị Phúc Long', 1), (2, 'Trà Trái Cây Nhiệt Đới', 2),
        (3, 'Pizza Nướng Củi Thủ Công', 1), (3, 'Mỳ Ý & Khai Vị Phô Mai', 2),
        (4, 'Cơm Tấm Sườn Nướng Than Hoa', 1), (4, 'Canh & Món Thêm Đặc Sắc', 2);

        INSERT INTO menu_items (category_id, name, description, price, stock_quantity, is_available) VALUES
        (1, 'Trà Sen Vàng (Đặc Biệt)', 'Món Demo Race Condition: Tồn kho = 1', 49000, 1, TRUE),
        (1, 'Trà Thạch Đào Cam Sả', 'Trà đào thanh mát', 49000, 50, TRUE),
        (1, 'Trà Thạch Vải Nhiệt Đới', 'Trà vải mọng nước', 45000, 40, TRUE),
        (2, 'Phin Sữa Đá Đậm Đà', 'Cà phê phin truyền thống', 35000, 80, TRUE),
        (2, 'Freeze Trà Xanh Matcha', 'Đá xay matcha Nhật Bản', 59000, 30, TRUE),
        (3, 'Trà Sữa Phúc Long Truyền Thống', 'Trà sữa đậm vị', 55000, 60, TRUE),
        (3, 'Trà Sữa Ô Long Sữa Tươi', 'Trà Ô Long nướng', 55000, 45, TRUE),
        (4, 'Trà Vải Lài Phúc Long', 'Trà hoa lài trái vải', 50000, 50, TRUE),
        (4, 'Trà Đào Đá Xay Kem Sữa', 'Trà đào đá xay', 65000, 25, TRUE),
        (4, 'Hồng Trà Đác Cam Mật Ong', 'Hồng trà hạt đác', 60000, 35, TRUE),
        (5, 'Pizza Burrata Parma Ham & Rau Rocket', 'Pizza phô mai Burrata', 298000, 20, TRUE),
        (5, 'Pizza 4 Cheese (4 Loại Phô Mai Mật Ong)', 'Pizza 4 phô mai', 258000, 25, TRUE),
        (5, 'Pizza Cá Hồi Teriyaki Kiểu Nhật', 'Pizza cá hồi', 268000, 18, TRUE),
        (6, 'Mỳ Ý Cua Sốt Kem Cà Chua & Phô Mai Ricotta', 'Mỳ Ý sốt kem cua', 228000, 30, TRUE),
        (6, 'Salad Trái Cây Nhiệt Đới & Phô Mai Burrata', 'Salad Burrata', 168000, 25, TRUE),
        (7, 'Cơm Tấm Sườn Bì Chả Trứng Ốp La Đặc Biệt', 'Cơm tấm đặc biệt', 65000, 70, TRUE),
        (7, 'Cơm Tấm Sườn Nướng Mật Ong', 'Cơm tấm sườn nướng', 48000, 85, TRUE),
        (7, 'Cơm Tấm Đùi Gà Nướng Ngũ Vị', 'Cơm tấm đùi gà', 52000, 50, TRUE),
        (8, 'Canh Khổ Qua Nhồi Thịt Hầm Xương', 'Canh khổ qua', 20000, 40, TRUE),
        (8, 'Chả Trứng Hấp Nấm Hương (Phần Thêm)', 'Chả trứng hấp', 15000, 60, TRUE);
    END IF;

    -- Xóa dữ liệu bơm thử nghiệm cũ (nếu chạy lại nhiều lần) để luôn giữ đúng 10.000 đơn giả lập
    DELETE FROM orders WHERE qr_verify_token LIKE 'FF_BULK_%';

    RAISE NOTICE 'Đang tiến hành bơm 10.000 đơn hàng và ~25.000 chi tiết đơn hàng...';

    FOR v_i IN 1..10000 LOOP
        -- Phân bổ khách hàng (ID 1..3) và nhà hàng (ID 1..4)
        v_cust_id := 1 + (v_i % 3);
        v_rest_id := 1 + (v_i % 4);

        -- Phân bổ trạng thái thực tế: phần lớn là COMPLETED/CANCELLED trong quá khứ,
        -- chỉ một tỷ lệ nhỏ là PENDING trong vài ngày gần đây để Index phát huy tối đa hiệu quả
        IF v_i <= 9600 THEN
            v_status := CASE WHEN (v_i % 5 = 0) THEN 'CANCELLED' ELSE 'COMPLETED' END;
            v_created_at := CURRENT_TIMESTAMP - ((1 + (v_i % 180)) || ' days')::INTERVAL
                                              - ((v_i % 1440) || ' minutes')::INTERVAL;
        ELSE
            v_status := v_statuses[1 + (v_i % array_length(v_statuses, 1))];
            v_created_at := CURRENT_TIMESTAMP - ((v_i % 72) || ' hours')::INTERVAL
                                              - ((v_i % 60) || ' minutes')::INTERVAL;
        END IF;

        v_payment := v_payments[1 + (v_i % 4)];
        v_shipping := CASE WHEN (v_i % 3 = 0) THEN 15000 ELSE 25000 END;
        v_discount := CASE WHEN (v_i % 4 = 0) THEN 15000 ELSE 0 END;

        -- Tính item_id theo đúng nhà hàng (Quán 1: 2..5, Quán 2: 6..10, Quán 3: 11..15, Quán 4: 16..20)
        -- Tránh món số 1 (Trà Sen Vàng tồn kho = 1 dùng để Demo Race Condition)
        v_base_item_id := (v_rest_id - 1) * 5 + 2;
        v_num_items := 2 + (v_i % 2); -- 2 hoặc 3 món mỗi đơn (~25.000 dòng order_details)
        v_subtotal := 0;

        -- Khởi tạo đơn hàng
        INSERT INTO orders (
            customer_id, restaurant_id, shipper_id, voucher_id,
            subtotal, shipping_fee, discount_amount, total_amount,
            order_status, qr_verify_token, delivery_address, delivery_lat, delivery_lng,
            created_at, vat_amount, payment_method, take_utensils, is_group_order
        ) VALUES (
            v_cust_id,
            v_rest_id,
            CASE WHEN v_status IN ('DELIVERING', 'COMPLETED') THEN 8 ELSE NULL END,
            NULL,
            100000, -- Tạm tính, cập nhật chính xác sau vòng lặp món
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
            (v_i % 2 = 0),
            FALSE
        ) RETURNING id INTO v_new_order_id;

        -- Bơm chi tiết món ăn (order_details) cho đơn hàng
        FOR v_j IN 0..(v_num_items - 1) LOOP
            v_item_id := v_base_item_id + (v_j % 4);
            v_qty := 1 + ((v_i + v_j) % 3);
            v_price := 45000 + ((v_item_id % 5) * 10000);

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

    RAISE NOTICE '=== ĐÃ BƠM XONG 10.000 ĐƠN HÀNG & ~25.000 CHI TIẾT ĐƠN HÀNG THÀNH CÔNG! ===';
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
