-- =============================================================================
-- ĐỒ ÁN GỘP 2 MÔN: DATABASE PROGRAMMING & MOBILE APPLICATION PROGRAMMING
-- ĐỀ TÀI: HỆ SINH THÁI ĐẶT ĐỒ ĂN & QUẢN LÝ F&B (SMART FOOD DELIVERY / FOODFLOW)
-- PHÂN HỆ: THÀNH VIÊN 1 (TV1 - DATABASE ARCHITECT: TRUNG)
-- FILE 01: 01_schema_tables.sql (Thiết kế 8 Bảng Chuẩn 3NF, PK, FK, CHECK)
-- Hệ quản trị CSDL: PostgreSQL 15 / 16 (pgAdmin 4)
-- =============================================================================
--
-- GIẢI THÍCH THIẾT KẾ CHUẨN HÓA CƠ SỞ DỮ LIỆU (1NF -> 2NF -> 3NF):
-- 1. Chuẩn 1NF (First Normal Form):
--    - Toàn bộ các cột giao dịch cốt lõi đều mang giá trị nguyên tố (atomic), không có
--      nhóm lặp dư thừa. Các trường cấu hình UI/UX mở rộng (saved_addresses, options_config,
--      selected_options) được lưu dưới dạng JSONB có cấu trúc của PostgreSQL để phục vụ
--      30 màn hình Mobile (SCR-01..SCR-30) mà không ảnh hưởng tới toàn vẹn quan hệ.
--    - Mỗi bảng đều có Khóa chính (PRIMARY KEY) định danh duy nhất từng bản ghi.
--
-- 2. Chuẩn 2NF (Second Normal Form):
--    - Thỏa mãn 1NF và mọi thuộc tính không khóa đều phụ thuộc hàm đầy đủ vào khóa chính.
--    - Chi tiết đơn hàng được tách riêng ra bảng `order_details` (liên kết `orders` và
--      `menu_items`), thông tin danh mục tách ra bảng `categories`, thông tin quán tách ra
--      bảng `restaurants`.
--
-- 3. Chuẩn 3NF (Third Normal Form) & Historical Transaction Snapshot:
--    - Thỏa mãn 2NF và không tồn tại phụ thuộc bắc cầu (Transitive Dependency) đối với
--      dữ liệu danh mục tĩnh (예: tên quán, địa chỉ quán chỉ lưu ở `restaurants`, không lặp
--      lại trong `menu_items` hay `orders`).
--    - LƯU Ý HỌC THUẬT QUAN TRỌNG VỀ `unit_price`, `subtotal`, `total_amount` TRONG
--      `order_details` VÀ `orders`:
--      Các cột này là "Historical Transaction Snapshot" (Bản chụp lịch sử giao dịch tại
--      thời điểm đặt hàng), KHÔNG vi phạm chuẩn 3NF. Lý do: Giá món ăn (`menu_items.price`)
--      và thông tin khuyến mãi (`vouchers`) có thể thay đổi theo thời gian (ví dụ: hôm nay
--      Trà Sen Vàng giá 49.000đ, tuần sau chủ quán tăng lên 55.000đ). Nếu không lưu cứng
--      `unit_price` và `subtotal` tại đúng thời điểm giao dịch được xác lập, mọi báo cáo
--      kế toán và lịch sử hóa đơn cũ sẽ bị tính sai lệch khi giá gốc thay đổi.
-- =============================================================================

SET client_encoding = 'UTF8';

-- Xóa các bảng cũ (nếu có) theo thứ tự ngược khóa ngoại để chạy lại script nhiều lần an toàn
DROP TABLE IF EXISTS
    audit_logs,
    order_details,
    orders,
    vouchers,
    menu_items,
    categories,
    restaurants,
    users
CASCADE;

-- =============================================================================
-- BẢNG 1: users (Quản lý đa vai trò: Khách hàng, Chủ quán, Tài xế Shipper)
-- =============================================================================
CREATE TABLE users (
    -- Cột cốt lõi
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) UNIQUE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER', 'RESTAURANT', 'SHIPPER')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Cột mở rộng hỗ trợ Mobile UI/UX (SCR-05, SCR-07, SCR-26, SCR-27, SCR-28)
    loyalty_points INT DEFAULT 0 CHECK (loyalty_points >= 0),
    member_tier VARCHAR(20) DEFAULT 'SILVER' CHECK (member_tier IN ('SILVER', 'GOLD', 'DIAMOND')),
    saved_addresses JSONB DEFAULT '[]'::jsonb,
    favorite_restaurant_ids INT[] DEFAULT '{}'
);

COMMENT ON TABLE users IS 'Bảng người dùng đa vai trò (CUSTOMER, RESTAURANT, SHIPPER) chuẩn 3NF';
COMMENT ON COLUMN users.loyalty_points IS 'Điểm thưởng tích lũy FoodFlow Points (SCR-27)';
COMMENT ON COLUMN users.member_tier IS 'Hạng thành viên: SILVER, GOLD, DIAMOND';

-- =============================================================================
-- BẢNG 2: restaurants (Thông tin Cửa hàng / Quán ăn & Tọa độ GPS thực tế)
-- =============================================================================
CREATE TABLE restaurants (
    -- Cột cốt lõi
    id SERIAL PRIMARY KEY,
    owner_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    address TEXT NOT NULL,
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90.0 AND 90.0),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180.0 AND 180.0),
    open_time TIME DEFAULT '07:00:00',
    close_time TIME DEFAULT '22:00:00',
    is_active BOOLEAN DEFAULT TRUE,

    -- Cột mở rộng hỗ trợ Mobile UI/UX (SCR-06, SCR-08, SCR-09, SCR-19)
    brand_name VARCHAR(100) DEFAULT NULL,
    cover_image_url TEXT DEFAULT NULL,
    rating NUMERIC(3,2) DEFAULT 4.50 CHECK (rating >= 1.0 AND rating <= 5.0),
    is_freeship BOOLEAN DEFAULT FALSE,
    prep_time_minutes INT DEFAULT 15 CHECK (prep_time_minutes > 0)
);

COMMENT ON TABLE restaurants IS 'Bảng thông tin quán ăn và tọa độ GPS phục vụ tính khoảng cách Haversine';

-- =============================================================================
-- BẢNG 3: categories (Danh mục thực đơn của từng quán)
-- =============================================================================
CREATE TABLE categories (
    -- Cột cốt lõi
    id SERIAL PRIMARY KEY,
    restaurant_id INT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,

    -- Cột mở rộng hỗ trợ Sticky Category Tabs (SCR-09)
    display_order INT DEFAULT 0
);

COMMENT ON TABLE categories IS 'Danh mục món ăn theo từng nhà hàng';

-- =============================================================================
-- BẢNG 4: menu_items (Món ăn, Giá bán & Tồn kho kiểm soát Race Condition)
-- =============================================================================
CREATE TABLE menu_items (
    -- Cột cốt lõi
    id SERIAL PRIMARY KEY,
    category_id INT NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    price NUMERIC(12,2) NOT NULL CHECK (price > 0),
    stock_quantity INT NOT NULL CHECK (stock_quantity >= 0),
    image_url TEXT,
    is_available BOOLEAN DEFAULT TRUE,

    -- Cột mở rộng hỗ trợ Mobile UI/UX (SCR-02, SCR-06, SCR-10)
    calories INT DEFAULT 250 CHECK (calories >= 0),
    dietary_tags VARCHAR[] DEFAULT '{}',
    options_config JSONB DEFAULT '{}'::jsonb
);

COMMENT ON TABLE menu_items IS 'Bảng món ăn với ràng buộc CHECK (price > 0, stock_quantity >= 0) chống âm kho';
COMMENT ON COLUMN menu_items.options_config IS 'Cấu hình JSONB cho Bottom Sheet chọn Size S/M/L, % Đường/Đá, Topping (SCR-10)';

-- =============================================================================
-- BẢNG 5: vouchers (Mã giảm giá & Giới hạn số lượt sử dụng)
-- =============================================================================
CREATE TABLE vouchers (
    -- Cột cốt lõi
    id SERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    discount_percent INT NOT NULL CHECK (discount_percent > 0 AND discount_percent <= 100),
    max_discount_amount NUMERIC(12,2) NOT NULL CHECK (max_discount_amount >= 0),
    min_order_amount NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (min_order_amount >= 0),
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    usage_limit INT NOT NULL CHECK (usage_limit > 0),
    used_count INT NOT NULL DEFAULT 0 CHECK (used_count >= 0 AND used_count <= usage_limit),

    -- Cột mở rộng hỗ trợ Ví Voucher & Đổi điểm (SCR-17, SCR-27)
    voucher_type VARCHAR(20) DEFAULT 'DISCOUNT' CHECK (voucher_type IN ('DISCOUNT', 'FREESHIP')),
    restaurant_id INT NULL REFERENCES restaurants(id) ON DELETE SET NULL,
    points_cost INT DEFAULT 0 CHECK (points_cost >= 0),

    -- Ràng buộc toàn bảng: Ngày kết thúc phải lớn hơn ngày bắt đầu
    CONSTRAINT chk_vouchers_valid_date_range CHECK (end_date > start_date)
);

COMMENT ON TABLE vouchers IS 'Bảng mã khuyến mãi với CHECK kiểm soát chặt chẽ hạn dùng và số lượt sử dụng';

-- =============================================================================
-- BẢNG 6: orders (Đơn đặt hàng, Tọa độ giao hàng & Mã xác thực QR)
-- =============================================================================
CREATE TABLE orders (
    -- Cột cốt lõi
    id SERIAL PRIMARY KEY,
    customer_id INT NOT NULL REFERENCES users(id),
    restaurant_id INT NOT NULL REFERENCES restaurants(id),
    shipper_id INT NULL REFERENCES users(id),
    voucher_id INT NULL REFERENCES vouchers(id),
    subtotal NUMERIC(12,2) NOT NULL CHECK (subtotal >= 0),
    shipping_fee NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (shipping_fee >= 0),
    discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    total_amount NUMERIC(12,2) NOT NULL CHECK (total_amount >= 0),
    order_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (order_status IN ('PENDING', 'ACCEPTED', 'COOKING', 'DELIVERING', 'COMPLETED', 'CANCELLED')),
    qr_verify_token VARCHAR(100) UNIQUE NOT NULL,
    delivery_address TEXT NOT NULL,
    delivery_lat DOUBLE PRECISION NOT NULL,
    delivery_lng DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Cột mở rộng hỗ trợ Mobile UI/UX (SCR-12..18, SCR-23, SCR-25)
    vat_amount NUMERIC(12,2) DEFAULT 0 CHECK (vat_amount >= 0),
    payment_method VARCHAR(20) DEFAULT 'COD' CHECK (payment_method IN ('COD', 'VIETQR', 'MOMO', 'ZALOPAY')),
    take_utensils BOOLEAN DEFAULT FALSE,
    scheduled_time TIMESTAMP NULL,
    is_group_order BOOLEAN DEFAULT FALSE,
    group_bill_split JSONB DEFAULT NULL,
    restaurant_rating INT NULL CHECK (restaurant_rating BETWEEN 1 AND 5),
    shipper_rating INT NULL CHECK (shipper_rating BETWEEN 1 AND 5),
    review_comment TEXT NULL
);

COMMENT ON TABLE orders IS 'Bảng đơn hàng trung tâm lưu Historical Transaction Snapshot và mã QR giao hàng';

-- =============================================================================
-- BẢNG 7: order_details (Chi tiết từng món trong đơn hàng)
-- =============================================================================
CREATE TABLE order_details (
    -- Cột cốt lõi
    id SERIAL PRIMARY KEY,
    order_id INT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    item_id INT NOT NULL REFERENCES menu_items(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price > 0),
    subtotal NUMERIC(12,2) NOT NULL CHECK (subtotal > 0),

    -- Cột mở rộng hỗ trợ Tùy biến món & Đặt lại nhanh Re-order (SCR-10, SCR-13, SCR-24)
    selected_options JSONB DEFAULT '{}'::jsonb,
    kitchen_note TEXT DEFAULT NULL,
    group_member_name VARCHAR(100) DEFAULT NULL
);

COMMENT ON TABLE order_details IS 'Chi tiết đơn hàng (lưu unit_price và subtotal tại thời điểm đặt hàng chuẩn 3NF)';

-- =============================================================================
-- BẢNG 8: audit_logs (Lịch sử truy vết tự động mọi thay đổi trạng thái đơn hàng)
-- =============================================================================
CREATE TABLE audit_logs (
    -- Cột cốt lõi
    id SERIAL PRIMARY KEY,
    order_id INT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    old_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NOT NULL,
    changed_by_user_id INT NULL REFERENCES users(id),
    note TEXT,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE audit_logs IS 'Bảng ghi vết tự động lịch sử chuyển trạng thái đơn hàng (phục vụ Trigger & Timeline Mobile)';

-- Thông báo hoàn tất tạo lược đồ 8 bảng
DO $$
BEGIN
    RAISE NOTICE '=== [TV1 - TRUNG] ĐÃ KHỞI TẠO THÀNH CÔNG 8 BẢNG CHUẨN 3NF TRONG 01_schema_tables.sql ===';
END $$;
