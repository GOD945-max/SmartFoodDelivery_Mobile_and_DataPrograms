-- TV2: Báo cáo đơn COMPLETED theo ngày tạo đơn. Khoảng ngày bao gồm cả hai đầu.
-- CALL sp_get_restaurant_revenue_report(1, CURRENT_DATE - 6, CURRENT_DATE, NULL);

CREATE OR REPLACE PROCEDURE sp_get_restaurant_revenue_report(
    IN p_restaurant_id INT,
    IN p_from_date DATE,
    IN p_to_date DATE,
    OUT report JSONB
)
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_from_date IS NULL OR p_to_date IS NULL
       OR NOT isfinite(p_from_date) OR NOT isfinite(p_to_date)
       OR p_to_date < p_from_date OR p_to_date - p_from_date > 365 THEN
        RAISE EXCEPTION USING
            ERRCODE = '22023',
            MESSAGE = 'Khoảng báo cáo phải từ 1 đến 366 ngày';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM restaurants WHERE id = p_restaurant_id) THEN
        RAISE EXCEPTION USING ERRCODE = 'P0002', MESSAGE = 'Không tìm thấy quán';
    END IF;

    WITH completed_orders AS (
        SELECT id, created_at::DATE AS day, total_amount
        FROM orders
        WHERE restaurant_id = p_restaurant_id
          AND order_status = 'COMPLETED'
          AND created_at >= p_from_date
          AND created_at < p_to_date + 1
    ), daily_totals AS (
        SELECT day, COUNT(*) AS order_count, SUM(total_amount) AS revenue
        FROM completed_orders
        GROUP BY day
    ), daily AS (
        SELECT p_from_date + n AS date,
               COALESCE(t.order_count, 0) AS order_count,
               COALESCE(t.revenue, 0) AS revenue
        FROM generate_series(0, p_to_date - p_from_date) AS days(n)
        LEFT JOIN daily_totals t ON t.day = p_from_date + n
    ), top_items AS (
        SELECT m.id AS item_id, m.name AS item_name, SUM(d.quantity) AS quantity_sold,
               SUM(d.subtotal) AS sales
        FROM completed_orders o
        JOIN order_details d ON d.order_id = o.id
        JOIN menu_items m ON m.id = d.item_id
        GROUP BY m.id
        ORDER BY quantity_sold DESC, sales DESC, item_id
        LIMIT 5
    )
    SELECT jsonb_build_object(
        'restaurant_id', p_restaurant_id,
        'from_date', p_from_date,
        'to_date', p_to_date,
        'total_orders', (SELECT COUNT(*) FROM completed_orders),
        'total_revenue', (SELECT COALESCE(SUM(total_amount), 0) FROM completed_orders),
        'daily_revenue', (SELECT jsonb_agg(d ORDER BY d.date) FROM daily d),
        'top_items', COALESCE((
            SELECT jsonb_agg(t ORDER BY t.quantity_sold DESC, t.sales DESC, t.item_id)
            FROM top_items t
        ), '[]'::JSONB)
    ) INTO report;
END;
$$;
