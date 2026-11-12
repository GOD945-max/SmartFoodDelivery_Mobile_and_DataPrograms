-- =============================================================================
-- ĐỒ ÁN GỘP 2 MÔN: DATABASE PROGRAMMING & MOBILE APPLICATION PROGRAMMING
-- ĐỀ TÀI: HỆ SINH THÁI ĐẶT ĐỒ ĂN & QUẢN LÝ F&B (SMART FOOD DELIVERY / FOODFLOW)
-- PHÂN HỆ: THÀNH VIÊN 1 (TV1 - DATABASE ARCHITECT: TRUNG)
-- FILE 02: 02_stored_procedures_acid.sql
-- Nội dung:
--   1. Hàm tính khoảng cách GPS Haversine: fn_calculate_distance
--   2. Stored Procedure Đặt đơn hàng ACID chống Race Condition: sp_create_order
--   3. Stored Procedure Hủy đơn hoàn trả tồn kho & lượt Voucher: sp_cancel_order_restore_stock
--   4. Hàm gợi ý mã giảm giá có lợi nhất (SCR-17): fn_suggest_best_voucher
-- =============================================================================

SET client_encoding = 'UTF8';

-- =============================================================================
-- 1. HÀM TÍNH KHOẢNG CÁCH ĐƯỜNG CHIM BAY THEO CÔNG THỨC HAVERSINE (ĐƠN VỊ: KM)
-- Lưu ý tích hợp: Được khai báo với CREATE OR REPLACE ngay đầu file 02 để sp_create_order
-- chạy độc lập không phụ thuộc thứ tự nạp file 04 của TV2, và tương thích 100% với TV2.
-- =============================================================================
CREATE OR REPLACE FUNCTION fn_calculate_distance(
    lat1 DOUBLE PRECISION,
    lon1 DOUBLE PRECISION,
    lat2 DOUBLE PRECISION,
    lon2 DOUBLE PRECISION
)
RETURNS DOUBLE PRECISION
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
    v_earth_radius_km CONSTANT DOUBLE PRECISION := 6371.0;
    v_dlat DOUBLE PRECISION;
    v_dlon DOUBLE PRECISION;
    v_cos_val DOUBLE PRECISION;
    v_distance DOUBLE PRECISION;
BEGIN
    IF lat1 IS NULL OR lon1 IS NULL OR lat2 IS NULL OR lon2 IS NULL THEN
        RETURN 0.0;
    END IF;

    -- Nếu 2 tọa độ trùng nhau tuyệt đối
    IF lat1 = lat2 AND lon1 = lon2 THEN
        RETURN 0.0;
    END IF;

    -- Công thức lượng giác cầu Haversine / Spherical Law of Cosines
    -- Dùng LEAST(1.0, GREATEST(-1.0, ...)) để tránh lỗi sai số dấu phẩy động ngoài miền [-1, 1] của ACOS
    v_cos_val := SIN(RADIANS(lat1)) * SIN(RADIANS(lat2))
               + COS(RADIANS(lat1)) * COS(RADIANS(lat2)) * COS(RADIANS(lon2 - lon1));

    v_cos_val := LEAST(1.0, GREATEST(-1.0, v_cos_val));
    v_distance := v_earth_radius_km * ACOS(v_cos_val);

    RETURN ROUND(v_distance::NUMERIC, 2)::DOUBLE PRECISION;
END;
$$;

COMMENT ON FUNCTION fn_calculate_distance IS 'Hàm tính khoảng cách GPS (km) bằng công thức Haversine phục vụ tính phí ship và lọc quán gần bạn';

-- =============================================================================
-- 2. STORED PROCEDURE ĐẶT ĐƠN HÀNG GIAO DỊCH ACID (sp_create_order)
-- Giải quyết bài toán cốt lõi:
--   - Chống Race Condition (2 khách cùng bấm đặt món ăn cuối cùng ở cùng 1 mili-giây)
--     bằng khóa bi quan Row-level Locking: SELECT ... FOR UPDATE OF m
--   - Chống Deadlock bằng cách luôn sắp xếp danh sách món theo item_id tăng dần trước khi khóa
--   - Kiểm tra và trừ tồn kho, tự động tắt is_available = FALSE nếu tồn kho về 0
--   - Kiểm tra, khóa dòng và cộng lượt dùng Voucher (FOR UPDATE)
--   - Tự động ROLLBACK toàn bộ giao dịch nếu bất kỳ món nào hết hàng hoặc Voucher không hợp lệ
-- =============================================================================
CREATE OR REPLACE PROCEDURE sp_create_order(
    IN p_customer_id INT,
    IN p_restaurant_id INT,
    IN p_voucher_code VARCHAR,
    IN p_delivery_address TEXT,
    IN p_delivery_lat DOUBLE PRECISION,
    IN p_delivery_lng DOUBLE PRECISION,
    IN p_items JSONB,
    INOUT o_order_id INT DEFAULT NULL,
    INOUT o_total_amount NUMERIC(12,2) DEFAULT NULL,
    INOUT o_qr_verify_token VARCHAR DEFAULT NULL,
    IN p_payment_method VARCHAR DEFAULT 'COD',
    IN p_take_utensils BOOLEAN DEFAULT FALSE,
    IN p_is_group_order BOOLEAN DEFAULT FALSE
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_rest_lat DOUBLE PRECISION;
    v_rest_lng DOUBLE PRECISION;
    v_rest_active BOOLEAN;
    v_is_freeship BOOLEAN;
    v_distance DOUBLE PRECISION;

    v_item_record RECORD;
    v_item_id INT;
    v_quantity INT;
    v_item_name VARCHAR(150);
    v_price NUMERIC(12,2);
    v_stock INT;
    v_is_avail BOOLEAN;
    v_item_rest_id INT;

    v_subtotal NUMERIC(12,2) := 0;
    v_shipping_fee NUMERIC(12,2) := 0;
    v_discount_amount NUMERIC(12,2) := 0;
    v_vat_amount NUMERIC(12,2) := 0;

    v_voucher vouchers%ROWTYPE;
    v_voucher_id INT := NULL;
BEGIN
    -- -------------------------------------------------------------------------
    -- BƯỚC 1: KIỂM TRA DỮ LIỆU ĐẦU VÀO & TRẠNG THÁI NHÀ HÀNG
    -- -------------------------------------------------------------------------
    IF p_items IS NULL OR jsonb_typeof(p_items) <> 'array' OR jsonb_array_length(p_items) = 0 THEN
        RAISE EXCEPTION 'Giỏ hàng trống hoặc định dạng danh sách món ăn (p_items) không hợp lệ!';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM users WHERE id = p_customer_id) THEN
        RAISE EXCEPTION 'Khách hàng (ID = %) không tồn tại trong hệ thống!', p_customer_id;
    END IF;

    SELECT latitude, longitude, is_active, is_freeship
    INTO v_rest_lat, v_rest_lng, v_rest_active, v_is_freeship
    FROM restaurants
    WHERE id = p_restaurant_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Quán ăn (ID = %) không tồn tại!', p_restaurant_id;
    END IF;

    IF NOT v_rest_active THEN
        RAISE EXCEPTION 'Quán ăn hiện đang đóng cửa hoặc tạm ngưng nhận đơn!';
    END IF;

    -- -------------------------------------------------------------------------
    -- BƯỚC 2: KHÓA DÒNG MÓN ĂN (SELECT ... FOR UPDATE), KIỂM TRA & TRỪ TỒN KHO
    -- Lưu ý: Sắp xếp theo item_id ASC và gom nhóm số lượng nếu cùng item_id để chống Deadlock
    -- -------------------------------------------------------------------------
    FOR v_item_record IN
        SELECT
            (elem->>'item_id')::INT AS item_id,
            SUM((elem->>'quantity')::INT) AS total_qty
        FROM jsonb_array_elements(p_items) AS elem
        GROUP BY (elem->>'item_id')::INT
        ORDER BY (elem->>'item_id')::INT ASC
    LOOP
        v_item_id := v_item_record.item_id;
        v_quantity := v_item_record.total_qty;

        IF v_quantity IS NULL OR v_quantity <= 0 THEN
            RAISE EXCEPTION 'Số lượng đặt của món ID % phải lớn hơn 0!', v_item_id;
        END IF;

        -- KHÓA DÒNG BI QUAN (PESSIMISTIC ROW-LEVEL LOCK) TRÊN BẢNG menu_items
        SELECT m.name, m.price, m.stock_quantity, m.is_available, c.restaurant_id
        INTO v_item_name, v_price, v_stock, v_is_avail, v_item_rest_id
        FROM menu_items m
        JOIN categories c ON m.category_id = c.id
        WHERE m.id = v_item_id
        FOR UPDATE OF m;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Món ăn có ID % không tồn tại trong thực đơn!', v_item_id;
        END IF;

        IF v_item_rest_id <> p_restaurant_id THEN
            RAISE EXCEPTION 'Món "%" (ID: %) không thuộc quán ăn đang đặt (ID quán: %)!',
                v_item_name, v_item_id, p_restaurant_id;
        END IF;

        IF NOT v_is_avail OR v_stock < v_quantity THEN
            RAISE EXCEPTION 'Món "%" không đủ tồn kho (còn %, đặt %)',
                v_item_name, v_stock, v_quantity;
        END IF;

        -- Trừ tồn kho ngay trong Transaction; nếu tồn kho về 0 thì tự động khóa món (is_available = FALSE)
        UPDATE menu_items
        SET stock_quantity = stock_quantity - v_quantity,
            is_available = CASE
                WHEN (stock_quantity - v_quantity) = 0 THEN FALSE
                ELSE is_available
            END
        WHERE id = v_item_id;

        -- Cộng dồn tổng tiền món ăn (Subtotal)
        v_subtotal := v_subtotal + (v_price * v_quantity);
    END LOOP;

    -- -------------------------------------------------------------------------
    -- BƯỚC 3: TÍNH KHOẢNG CÁCH GPS HAVERSINE & PHÍ GIAO HÀNG (SHIPPING FEE)
    -- Quy tắc: <= 2km phí 15.000đ; mỗi km tiếp theo (làm tròn lên) cộng thêm 5.000đ
    -- -------------------------------------------------------------------------
    v_distance := fn_calculate_distance(v_rest_lat, v_rest_lng, p_delivery_lat, p_delivery_lng);

    IF v_distance <= 2.0 THEN
        v_shipping_fee := 15000;
    ELSE
        v_shipping_fee := 15000 + CEIL(v_distance - 2.0) * 5000;
    END IF;

    -- -------------------------------------------------------------------------
    -- BƯỚC 4: XÁC THỰC & ÁP DỤNG VOUCHER (CÓ KHÓA DÒNG FOR UPDATE CHỐNG VƯỢT LƯỢT DÙNG)
    -- -------------------------------------------------------------------------
    IF p_voucher_code IS NOT NULL AND TRIM(p_voucher_code) <> '' THEN
        SELECT *
        INTO v_voucher
        FROM vouchers
        WHERE UPPER(code) = UPPER(TRIM(p_voucher_code))
        FOR UPDATE;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Mã giảm giá "%" không tồn tại!', TRIM(p_voucher_code);
        END IF;

        IF CURRENT_TIMESTAMP < v_voucher.start_date OR CURRENT_TIMESTAMP > v_voucher.end_date THEN
            RAISE EXCEPTION 'Mã giảm giá "%" đã hết hạn hoặc chưa đến thời gian áp dụng!', v_voucher.code;
        END IF;

        IF v_voucher.used_count >= v_voucher.usage_limit THEN
            RAISE EXCEPTION 'Mã giảm giá "%" đã hết lượt sử dụng (tối đa % lượt)!',
                v_voucher.code, v_voucher.usage_limit;
        END IF;

        IF v_subtotal < v_voucher.min_order_amount THEN
            RAISE EXCEPTION 'Đơn hàng chưa đạt giá trị tối thiểu %đ để áp dụng mã "%"! (Hiện tại: %đ)',
                v_voucher.min_order_amount, v_voucher.code, v_subtotal;
        END IF;

        IF v_voucher.restaurant_id IS NOT NULL AND v_voucher.restaurant_id <> p_restaurant_id THEN
            RAISE EXCEPTION 'Mã giảm giá "%" chỉ áp dụng riêng cho quán ăn khác!', v_voucher.code;
        END IF;

        -- Tính số tiền được giảm theo loại Voucher (FREESHIP hoặc DISCOUNT)
        IF v_voucher.voucher_type = 'FREESHIP' THEN
            v_discount_amount := LEAST(v_shipping_fee, v_voucher.max_discount_amount);
        ELSE
            v_discount_amount := LEAST(
                ROUND((v_subtotal * v_voucher.discount_percent) / 100.0, 2),
                v_voucher.max_discount_amount
            );
        END IF;

        -- Tăng số lượt đã dùng của Voucher
        UPDATE vouchers
        SET used_count = used_count + 1
        WHERE id = v_voucher.id;

        v_voucher_id := v_voucher.id;
    END IF;

    -- -------------------------------------------------------------------------
    -- BƯỚC 5: TÍNH TỔNG TIỀN, SINH MÃ QR GIAO HÀNG & TẠO BẢN GHI ĐƠN HÀNG (orders)
    -- -------------------------------------------------------------------------
    v_vat_amount := ROUND(v_subtotal * 0.08, 2);
    o_total_amount := GREATEST(0, v_subtotal + v_shipping_fee - v_discount_amount);
    o_qr_verify_token := 'FF_QR_' || UPPER(SUBSTRING(MD5(RANDOM()::TEXT || CLOCK_TIMESTAMP()::TEXT) FROM 1 FOR 12));

    INSERT INTO orders (
        customer_id,
        restaurant_id,
        shipper_id,
        voucher_id,
        subtotal,
        shipping_fee,
        discount_amount,
        total_amount,
        order_status,
        qr_verify_token,
        delivery_address,
        delivery_lat,
        delivery_lng,
        vat_amount,
        payment_method,
        take_utensils,
        is_group_order
    ) VALUES (
        p_customer_id,
        p_restaurant_id,
        NULL,
        v_voucher_id,
        v_subtotal,
        v_shipping_fee,
        v_discount_amount,
        o_total_amount,
        'PENDING',
        o_qr_verify_token,
        p_delivery_address,
        p_delivery_lat,
        p_delivery_lng,
        v_vat_amount,
        COALESCE(p_payment_method, 'COD'),
        COALESCE(p_take_utensils, FALSE),
        COALESCE(p_is_group_order, FALSE)
    )
    RETURNING id INTO o_order_id;

    -- -------------------------------------------------------------------------
    -- BƯỚC 6: LƯU CHI TIẾT ĐƠN HÀNG (order_details) VỚI HISTORICAL SNAPSHOT PRICE
    -- -------------------------------------------------------------------------
    FOR v_item_record IN
        SELECT
            (elem->>'item_id')::INT AS item_id,
            (elem->>'quantity')::INT AS quantity,
            COALESCE(elem->'selected_options', '{}'::jsonb) AS selected_options,
            elem->>'kitchen_note' AS kitchen_note,
            elem->>'group_member_name' AS group_member_name
        FROM jsonb_array_elements(p_items) AS elem
    LOOP
        SELECT price INTO v_price
        FROM menu_items
        WHERE id = v_item_record.item_id;

        INSERT INTO order_details (
            order_id,
            item_id,
            quantity,
            unit_price,
            subtotal,
            selected_options,
            kitchen_note,
            group_member_name
        ) VALUES (
            o_order_id,
            v_item_record.item_id,
            v_item_record.quantity,
            v_price,
            v_price * v_item_record.quantity,
            v_item_record.selected_options,
            v_item_record.kitchen_note,
            v_item_record.group_member_name
        );
    END LOOP;

    -- -------------------------------------------------------------------------
    -- BƯỚC 7: GHI NHẬN LỊCH SỬ KHỞI TẠO ĐƠN VÀO audit_logs (CHỐNG TRÙNG VỚI TRIGGER TV2)
    -- -------------------------------------------------------------------------
    IF NOT EXISTS (
        SELECT 1 FROM audit_logs
        WHERE order_id = o_order_id AND new_status = 'PENDING'
    ) THEN
        INSERT INTO audit_logs (
            order_id,
            old_status,
            new_status,
            changed_by_user_id,
            note
        ) VALUES (
            o_order_id,
            NULL,
            'PENDING',
            p_customer_id,
            'Khách hàng tạo đơn hàng mới thành công (Giao dịch ACID)'
        );
    END IF;

    -- Cộng điểm thưởng tích lũy cho khách hàng (1 điểm cho mỗi 10.000đ)
    UPDATE users
    SET loyalty_points = loyalty_points + FLOOR(o_total_amount / 10000.0)::INT
    WHERE id = p_customer_id;

END;
$$;

COMMENT ON PROCEDURE sp_create_order IS 'Stored Procedure đặt đơn hàng ACID: khóa FOR UPDATE chống âm kho, áp voucher, tính ship GPS và sinh mã QR';

-- =============================================================================
-- 3. STORED PROCEDURE HỦY ĐƠN HÀNG & HOÀN TRẢ TỒN KHO + VOUCHER (sp_cancel_order_restore_stock)
-- Đặc tả SCR-22: Chỉ cho phép hủy khi đơn ở trạng thái PENDING hoặc ACCEPTED (trước khi quán nấu).
-- Khi hủy: Tự động cộng trả lại tồn kho cho từng món, bật lại is_available = TRUE,
-- và hoàn trả 1 lượt dùng của mã giảm giá (nếu đơn có dùng Voucher).
-- =============================================================================
CREATE OR REPLACE PROCEDURE sp_cancel_order_restore_stock(
    IN p_order_id INT,
    IN p_user_id INT DEFAULT NULL,
    IN p_reason TEXT DEFAULT 'Khách hàng yêu cầu hủy đơn'
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_order orders%ROWTYPE;
    v_detail RECORD;
BEGIN
    -- Khóa dòng đơn hàng cần hủy
    SELECT *
    INTO v_order
    FROM orders
    WHERE id = p_order_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Đơn hàng (ID = %) không tồn tại!', p_order_id;
    END IF;

    -- Kiểm tra trạng thái hợp lệ để hủy (SCR-22: chỉ hủy trước khi bếp nấu)
    IF v_order.order_status NOT IN ('PENDING', 'ACCEPTED') THEN
        RAISE EXCEPTION 'Không thể hủy đơn ở trạng thái % (Quán đã nấu hoặc đang giao)',
            v_order.order_status;
    END IF;

    -- Duyệt qua từng món trong đơn hàng (theo thứ tự item_id ASC) để hoàn trả tồn kho
    FOR v_detail IN
        SELECT item_id, SUM(quantity) AS total_qty
        FROM order_details
        WHERE order_id = p_order_id
        GROUP BY item_id
        ORDER BY item_id ASC
    LOOP
        -- Khóa dòng món ăn trước khi cộng trả kho
        PERFORM 1 FROM menu_items WHERE id = v_detail.item_id FOR UPDATE;

        UPDATE menu_items
        SET stock_quantity = stock_quantity + v_detail.total_qty,
            is_available = TRUE
        WHERE id = v_detail.item_id;
    END LOOP;

    -- Hoàn trả lượt dùng Voucher nếu đơn hàng có áp mã giảm giá
    IF v_order.voucher_id IS NOT NULL THEN
        UPDATE vouchers
        SET used_count = GREATEST(0, used_count - 1)
        WHERE id = v_order.voucher_id;
    END IF;

    -- Cập nhật trạng thái đơn sang CANCELLED
    UPDATE orders
    SET order_status = 'CANCELLED'
    WHERE id = p_order_id;

    -- Ghi vết vào bảng audit_logs (kiểm tra IF NOT EXISTS để không trùng nếu Trigger TV2 đã tự ghi)
    IF NOT EXISTS (
        SELECT 1 FROM audit_logs
        WHERE order_id = p_order_id AND new_status = 'CANCELLED'
    ) THEN
        INSERT INTO audit_logs (
            order_id,
            old_status,
            new_status,
            changed_by_user_id,
            note
        ) VALUES (
            p_order_id,
            v_order.order_status,
            'CANCELLED',
            COALESCE(p_user_id, v_order.customer_id),
            COALESCE(p_reason, 'Hủy đơn hàng và hoàn trả tồn kho tự động')
        );
    ELSE
        UPDATE audit_logs
        SET changed_by_user_id = COALESCE(changed_by_user_id, p_user_id, v_order.customer_id),
            note = COALESCE(p_reason, note)
        WHERE order_id = p_order_id AND new_status = 'CANCELLED';
    END IF;
END;
$$;

COMMENT ON PROCEDURE sp_cancel_order_restore_stock IS 'Stored Procedure hủy đơn hàng ACID: hoàn trả tồn kho món ăn, bật lại is_available và hoàn lượt dùng Voucher';

-- =============================================================================
-- 4. HÀM GỢI Ý VOUCHER TỐI ƯU NHẤT CHO GIỎ HÀNG (fn_suggest_best_voucher - SCR-17)
-- Giúp App Android tự động tính số tiền tiết kiệm dự kiến của từng Voucher và xếp mã
-- có lợi nhất lên đầu danh sách.
-- =============================================================================
CREATE OR REPLACE FUNCTION fn_suggest_best_voucher(
    p_restaurant_id INT,
    p_subtotal NUMERIC,
    p_shipping_fee NUMERIC DEFAULT 15000
)
RETURNS TABLE (
    voucher_id INT,
    code VARCHAR(50),
    voucher_type VARCHAR(20),
    discount_percent INT,
    max_discount_amount NUMERIC(12,2),
    min_order_amount NUMERIC(12,2),
    remaining_uses INT,
    is_eligible BOOLEAN,
    estimated_saving NUMERIC(12,2),
    note TEXT
)
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
    RETURN QUERY
    SELECT
        v.id AS voucher_id,
        v.code,
        v.voucher_type,
        v.discount_percent,
        v.max_discount_amount,
        v.min_order_amount,
        (v.usage_limit - v.used_count) AS remaining_uses,
        (
            CURRENT_TIMESTAMP BETWEEN v.start_date AND v.end_date
            AND v.used_count < v.usage_limit
            AND p_subtotal >= v.min_order_amount
            AND (v.restaurant_id IS NULL OR v.restaurant_id = p_restaurant_id)
        ) AS is_eligible,
        CASE
            WHEN NOT (
                CURRENT_TIMESTAMP BETWEEN v.start_date AND v.end_date
                AND v.used_count < v.usage_limit
                AND p_subtotal >= v.min_order_amount
                AND (v.restaurant_id IS NULL OR v.restaurant_id = p_restaurant_id)
            ) THEN 0::NUMERIC(12,2)
            WHEN v.voucher_type = 'FREESHIP' THEN LEAST(p_shipping_fee, v.max_discount_amount)::NUMERIC(12,2)
            ELSE LEAST(ROUND((p_subtotal * v.discount_percent) / 100.0, 2), v.max_discount_amount)::NUMERIC(12,2)
        END AS estimated_saving,
        CASE
            WHEN CURRENT_TIMESTAMP > v.end_date THEN 'Mã đã hết hạn'
            WHEN v.used_count >= v.usage_limit THEN 'Đã hết lượt sử dụng'
            WHEN v.restaurant_id IS NOT NULL AND v.restaurant_id <> p_restaurant_id THEN 'Chỉ áp dụng cho quán khác'
            WHEN p_subtotal < v.min_order_amount THEN 'Mua thêm ' || (v.min_order_amount - p_subtotal)::TEXT || 'đ để dùng mã này'
            ELSE 'Có thể áp dụng ngay'
        END AS note
    FROM vouchers v
    WHERE v.restaurant_id IS NULL OR v.restaurant_id = p_restaurant_id
    ORDER BY is_eligible DESC, estimated_saving DESC, v.end_date ASC;
END;
$$;

COMMENT ON FUNCTION fn_suggest_best_voucher IS 'Hàm gợi ý và xếp hạng mã giảm giá có lợi nhất cho màn hình Ví Voucher (SCR-17)';

DO $$
BEGIN
    RAISE NOTICE '=== [TV1 - TRUNG] ĐÃ KHỞI TẠO THÀNH CÔNG SP ACID & FUNCTIONS TRONG 02_stored_procedures_acid.sql ===';
END $$;
