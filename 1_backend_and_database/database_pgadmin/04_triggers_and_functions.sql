-- TV2: Audit đơn, tồn kho, giới hạn shipper và view thực đơn.
-- Chạy sau 01_schema_tables.sql và 02_stored_procedures_acid.sql.
-- Dùng fn_calculate_distance từ file 02. Không ghi bản tạo mới vì
-- sp_create_order của TV1 đã ghi trạng thái PENDING.
-- Người thao tác để NULL khi backend chưa cung cấp danh tính.

BEGIN;

CREATE OR REPLACE FUNCTION fn_audit_order_status()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    -- Chỉ so với bản ghi gần nhất: không xóa các lần chuyển trạng thái
    -- giống nhau nhưng xảy ra lại sau một trạng thái trung gian.
    IF NOT EXISTS (
        SELECT 1
        FROM (
            SELECT old_status, new_status
            FROM audit_logs
            WHERE order_id = NEW.id
            ORDER BY id DESC
            LIMIT 1
        ) AS latest
        WHERE latest.old_status IS NOT DISTINCT FROM OLD.order_status
          AND latest.new_status IS NOT DISTINCT FROM NEW.order_status
    ) THEN
        INSERT INTO audit_logs (order_id, old_status, new_status)
        VALUES (NEW.id, OLD.order_status, NEW.order_status);
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_audit_order_status ON orders;
CREATE TRIGGER trg_audit_order_status
AFTER UPDATE OF order_status ON orders
FOR EACH ROW
WHEN (OLD.order_status IS DISTINCT FROM NEW.order_status)
EXECUTE FUNCTION fn_audit_order_status();

CREATE OR REPLACE FUNCTION fn_auto_toggle_menu_availability()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.stock_quantity = 0 THEN
        NEW.is_available := FALSE;
    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.stock_quantity = 0 THEN
            NEW.is_available := TRUE;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_auto_toggle_menu_availability ON menu_items;
CREATE TRIGGER trg_auto_toggle_menu_availability
BEFORE INSERT OR UPDATE OF stock_quantity, is_available ON menu_items
FOR EACH ROW
EXECUTE FUNCTION fn_auto_toggle_menu_availability();

CREATE INDEX IF NOT EXISTS idx_tv2_orders_delivering_shipper
ON orders (shipper_id)
WHERE order_status = 'DELIVERING' AND shipper_id IS NOT NULL;

CREATE OR REPLACE FUNCTION fn_prevent_shipper_overload()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF OLD.order_status = 'DELIVERING' AND OLD.shipper_id = NEW.shipper_id THEN
            RETURN NULL;
        END IF;
    END IF;

    -- Ghi phiên bản mới của cùng dòng shipper để tuần tự hóa việc nhận đơn.
    -- READ COMMITTED đếm theo snapshot mới. REPEATABLE READ có thể trả 40001
    -- khi phiên bản đã đổi, khi đó ứng dụng phải thử lại cả giao dịch.
    UPDATE users
    SET role = role
    WHERE id = NEW.shipper_id AND role = 'SHIPPER';

    IF NOT FOUND THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'Đơn đang giao phải được gán cho tài khoản SHIPPER';
    END IF;

    IF (SELECT COUNT(*) FROM orders
        WHERE shipper_id = NEW.shipper_id AND order_status = 'DELIVERING') > 2 THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'trg_prevent_shipper_overload',
            MESSAGE = 'Shipper chỉ được giao tối đa 2 đơn cùng lúc';
    END IF;

    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_prevent_shipper_overload ON orders;
CREATE TRIGGER trg_prevent_shipper_overload
AFTER INSERT OR UPDATE OF order_status, shipper_id ON orders
FOR EACH ROW
WHEN (NEW.order_status = 'DELIVERING' AND NEW.shipper_id IS NOT NULL)
EXECUTE FUNCTION fn_prevent_shipper_overload();

CREATE OR REPLACE VIEW v_restaurant_menu_details AS
SELECT r.id AS restaurant_id, r.name AS restaurant_name,
       c.id AS category_id, c.name AS category_name, c.display_order,
       m.id AS item_id, m.name AS item_name, m.description, m.price,
       m.stock_quantity, m.image_url, m.is_available, m.calories,
       m.dietary_tags, m.options_config
FROM restaurants r
JOIN categories c ON c.restaurant_id = r.id
LEFT JOIN menu_items m ON m.category_id = c.id;

COMMIT;
