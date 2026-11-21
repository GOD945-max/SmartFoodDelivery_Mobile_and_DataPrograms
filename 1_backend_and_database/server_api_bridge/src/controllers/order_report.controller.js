// [TV3] API Đặt đơn, Đổi trạng thái, Quét QR, Báo cáo doanh thu
const express = require('express');
const crypto = require('crypto');
const { query, createOrderTx, cancelOrderTx } = require('../config/db'); // [TV1] db.js

const router = express.Router();

// Parse JSON ngay trong router của TV3 (giới hạn 16kb) để không ghi đè giới hạn riêng của router TV2
router.use(express.json({ limit: '16kb' }));

// =====================================================================
// XÁC THỰC & PHÂN QUYỀN: dùng middleware của TV2 (req.user = { id, role, restaurant_id })
// =====================================================================
const { authenticateToken: authRequired, authorizeRoles: requireRole } = require('../middlewares/auth.jwt');

// =====================================================================
// HÀM DÙNG CHUNG
// =====================================================================

// Lấy chi tiết 1 đơn (kèm danh sách món + tên khách) để trả về app và bắn socket
async function getOrderDetail(orderId) {
  const { rows } = await query(
    `SELECT o.id, o.customer_id, o.restaurant_id, o.shipper_id, o.order_status,
            o.subtotal, o.shipping_fee, o.discount_amount, o.total_amount,
            o.qr_verify_token, o.delivery_address, o.delivery_lat, o.delivery_lng,
            o.created_at, u.full_name AS customer_name, u.phone AS customer_phone
       FROM orders o
       JOIN users u ON u.id = o.customer_id
      WHERE o.id = $1`,
    [orderId]
  );
  if (rows.length === 0) return null;

  const items = await query(
    `SELECT od.item_id, m.name, od.quantity, od.unit_price, od.subtotal
       FROM order_details od
       JOIN menu_items m ON m.id = od.item_id
      WHERE od.order_id = $1
      ORDER BY od.id`,
    [orderId]
  );
  return { ...rows[0], items: items.rows };
}

// Kiểm tra & chuẩn hóa mảng món từ app gửi lên. Trả về chuỗi lỗi hoặc null.
function validateItems(items) {
  if (!Array.isArray(items) || items.length === 0) return 'Giỏ hàng đang trống';
  if (items.length > 50) return 'Một đơn tối đa 50 món';
  const seen = new Set();
  for (const it of items) {
    if (!Number.isInteger(it.item_id) || it.item_id <= 0) return 'item_id không hợp lệ';
    if (!Number.isInteger(it.quantity) || it.quantity < 1 || it.quantity > 99) {
      return 'Số lượng mỗi món phải từ 1 đến 99';
    }
    if (it.kitchen_note != null && typeof it.kitchen_note !== 'string') return 'kitchen_note phải là chuỗi';
    if (seen.has(it.item_id)) return 'Món bị trùng trong giỏ, hãy gộp số lượng';
    seen.add(it.item_id);
  }
  return null;
}

// =====================================================================
// PHẦN 2: POST /api/orders  (Khách đặt hàng)
// Body: {
//   restaurant_id, voucher_code?, delivery_address, delivery_lat, delivery_lng,
//   payment_method?, items: [{ item_id, quantity, kitchen_note? }]
// }
// =====================================================================
router.post('/orders', authRequired, requireRole('CUSTOMER'), async (req, res) => {
  const customerId = req.user.id; // LẤY TỪ JWT, KHÔNG tin body
  const {
    restaurant_id,
    voucher_code,
    delivery_address,
    delivery_lat,
    delivery_lng,
    payment_method,
    take_utensils,
    is_group_order,
    group_bill_split,
    items,
  } = req.body || {};

  // ---- 1. Validate đầu vào ----
  if (!Number.isInteger(restaurant_id) || restaurant_id <= 0) {
    return res.status(400).json({ success: false, message: 'restaurant_id không hợp lệ' });
  }
  if (typeof delivery_address !== 'string' || delivery_address.trim() === '') {
    return res.status(400).json({ success: false, message: 'Vui lòng nhập địa chỉ giao hàng' });
  }
  if (typeof delivery_lat !== 'number' || typeof delivery_lng !== 'number'
      || delivery_lat < -90 || delivery_lat > 90 || delivery_lng < -180 || delivery_lng > 180) {
    return res.status(400).json({ success: false, message: 'Tọa độ giao hàng không hợp lệ' });
  }
  const itemsError = validateItems(items);
  if (itemsError) return res.status(400).json({ success: false, message: itemsError });

  for (const [name, value] of [['take_utensils', take_utensils], ['is_group_order', is_group_order]]) {
    if (value != null && typeof value !== 'boolean') {
      return res.status(400).json({ success: false, message: `${name} phải là true/false` });
    }
  }
  if (group_bill_split != null && (typeof group_bill_split !== 'object' || Array.isArray(group_bill_split))) {
    return res.status(400).json({ success: false, message: 'group_bill_split phải là một đối tượng JSON' });
  }

  const voucher = voucher_code && String(voucher_code).trim() !== '' ? String(voucher_code).trim() : null;
  const payment = ['VIETQR', 'COD'].includes(payment_method) ? payment_method : 'COD'; // mặc định giống createOrderTx
  // Chỉ chuyển đúng các field SP cần, bỏ field lạ do client gửi thêm
  const cleanItems = items.map((i) => ({
    item_id: i.item_id,
    quantity: i.quantity,
    selected_options: i.selected_options ?? null,
    kitchen_note: i.kitchen_note ?? null,
  }));

  try {
    // ---- 2. Gọi helper của TV1: CALL sp_create_order (Transaction ACID nằm trong SP) ----
    const created = await createOrderTx({
      customer_id: customerId,
      restaurant_id,
      voucher_code: voucher,
      delivery_address: delivery_address.trim(),
      delivery_lat,
      delivery_lng,
      items: cleanItems,
      payment_method: payment,
      take_utensils: take_utensils === true,
      is_group_order: is_group_order === true,
      group_bill_split: group_bill_split ?? null,
    });

    // ---- 3. Lấy chi tiết đơn vừa tạo (created.order_id, created.qr_verify_token từ INOUT của SP) ----
    const order = await getOrderDetail(created.order_id);
    if (!order) {
      return res.status(500).json({ success: false, message: 'Đặt hàng xong nhưng không đọc được đơn vừa tạo' });
    }

    // ---- 4. DB đã COMMIT xong -> mới bắn Socket cho Chủ quán ----
    const io = req.app.get('io');
    io.to(`restaurant_${order.restaurant_id}`).emit('NEW_ORDER_ALERT', {
      order_id: order.id,
      customer_name: order.customer_name,
      total_amount: order.total_amount,
      items: order.items.map((i) => ({ name: i.name, quantity: i.quantity })),
      created_at: order.created_at,
    });

    // ---- 5. Trả kết quả cho app khách (kèm qr_verify_token để hiện Mã QR) ----
    return res.status(201).json({ success: true, message: 'Đặt hàng thành công', data: order });
  } catch (err) {
    // RAISE EXCEPTION trong SP (hết hàng, voucher sai, quán đóng cửa...) có mã P0001
    if (err.code === 'P0001') {
      return res.status(400).json({ success: false, message: err.message });
    }
    console.error('[POST /orders]', err);
    return res.status(500).json({ success: false, message: 'Lỗi server khi đặt hàng' });
  }
});

// =====================================================================
// PHẦN 3a: PATCH /api/orders/:id/status  (Chủ quán Nhận đơn / Nấu / Giao)
// Body: { new_status, shipper_id? }   (shipper_id chỉ dùng khi chuyển sang DELIVERING)
// Máy trạng thái: PENDING -> ACCEPTED -> COOKING -> DELIVERING
// COMPLETED do API verify-qr xử lý, CANCELLED do API cancel xử lý.
// =====================================================================
const NEXT_STATUS = {
  PENDING: 'ACCEPTED',
  ACCEPTED: 'COOKING',
  COOKING: 'DELIVERING',
};

// Chủ quán chỉ được thao tác với đơn thuộc quán của mình (kiểm tra bằng DB, không tin JWT)
async function findOrderOfOwner(orderId, ownerId) {
  const { rows } = await query(
    `SELECT o.id, o.customer_id, o.restaurant_id, o.order_status
       FROM orders o
       JOIN restaurants r ON r.id = o.restaurant_id
      WHERE o.id = $1 AND r.owner_id = $2`,
    [orderId, ownerId]
  );
  return rows[0] || null;
}

router.patch('/orders/:id/status', authRequired, requireRole('RESTAURANT'), async (req, res) => {
  const orderId = Number(req.params.id);
  const { new_status, shipper_id } = req.body || {};

  if (!Number.isInteger(orderId) || orderId <= 0) {
    return res.status(400).json({ success: false, message: 'Mã đơn không hợp lệ' });
  }
  if (shipper_id != null && (!Number.isInteger(shipper_id) || shipper_id <= 0)) {
    return res.status(400).json({ success: false, message: 'shipper_id không hợp lệ' });
  }

  try {
    const order = await findOrderOfOwner(orderId, req.user.id);
    if (!order) {
      return res.status(404).json({ success: false, message: 'Không tìm thấy đơn hàng của quán bạn' });
    }

    const oldStatus = order.order_status;
    if (NEXT_STATUS[oldStatus] !== new_status) {
      return res.status(409).json({
        success: false,
        message: `Không thể chuyển từ ${oldStatus} sang ${new_status}`,
        allowed_next: NEXT_STATUS[oldStatus] || null,
      });
    }

    // UPDATE có điều kiện order_status = oldStatus để chặn 2 thao tác bấm cùng lúc
    // (Trigger 1 của TV2 tự ghi audit_logs, Trigger 3 chặn shipper quá tải)
    const upd = await query(
      `UPDATE orders
          SET order_status = $1,
              shipper_id = CASE WHEN $1 = 'DELIVERING' THEN COALESCE($4, shipper_id) ELSE shipper_id END
        WHERE id = $2 AND order_status = $3
        RETURNING id`,
      [new_status, orderId, oldStatus, shipper_id ?? null]
    );
    if (upd.rowCount === 0) {
      return res.status(409).json({ success: false, message: 'Đơn vừa được cập nhật bởi thao tác khác, hãy tải lại' });
    }

    // DB đã cập nhật xong -> bắn Socket báo máy Khách (và Shipper nếu có)
    const io = req.app.get('io');
    const payload = {
      order_id: orderId,
      old_status: oldStatus,
      new_status,
      changed_at: new Date().toISOString(),
    };
    io.to(`customer_${order.customer_id}`).emit('ORDER_STATUS_CHANGED', payload);
    if (new_status === 'DELIVERING' && shipper_id) {
      io.to(`shipper_${shipper_id}`).emit('ORDER_STATUS_CHANGED', payload);
    }

    return res.json({ success: true, message: 'Cập nhật trạng thái thành công', data: payload });
  } catch (err) {
    // Trigger 3 (shipper quá tải) dùng RAISE EXCEPTION -> P0001
    if (err.code === 'P0001') {
      return res.status(409).json({ success: false, message: err.message });
    }
    // shipper_id không tồn tại (vi phạm khóa ngoại)
    if (err.code === '23503') {
      return res.status(400).json({ success: false, message: 'Shipper không tồn tại' });
    }
    console.error('[PATCH /orders/:id/status]', err);
    return res.status(500).json({ success: false, message: 'Lỗi server khi đổi trạng thái' });
  }
});

// =====================================================================
// PHẦN 3b: POST /api/orders/:id/cancel  (Hủy đơn, hoàn kho + hoàn voucher)
// Body: { reason? }
// - Khách: chỉ hủy đơn của chính mình
// - Chủ quán: từ chối đơn thuộc quán mình
// - Chỉ hủy được khi đơn đang PENDING hoặc ACCEPTED
// =====================================================================
const CANCELLABLE = ['PENDING', 'ACCEPTED'];

router.post('/orders/:id/cancel', authRequired, requireRole('CUSTOMER', 'RESTAURANT'), async (req, res) => {
  const orderId = Number(req.params.id);
  const reason = String((req.body && req.body.reason) || '').trim().slice(0, 255)
    || (req.user.role === 'CUSTOMER' ? 'Khách hủy đơn' : 'Quán từ chối đơn');

  if (!Number.isInteger(orderId) || orderId <= 0) {
    return res.status(400).json({ success: false, message: 'Mã đơn không hợp lệ' });
  }

  try {
    // Tìm đơn và kiểm tra quyền sở hữu theo role
    let order;
    if (req.user.role === 'CUSTOMER') {
      const r = await query(
        'SELECT id, customer_id, restaurant_id, order_status FROM orders WHERE id = $1 AND customer_id = $2',
        [orderId, req.user.id]
      );
      order = r.rows[0];
    } else {
      order = await findOrderOfOwner(orderId, req.user.id);
    }
    if (!order) {
      return res.status(404).json({ success: false, message: 'Không tìm thấy đơn hàng' });
    }

    const oldStatus = order.order_status;
    if (!CANCELLABLE.includes(oldStatus)) {
      return res.status(409).json({
        success: false,
        message: `Đơn đang ở trạng thái ${oldStatus}, không thể hủy`,
      });
    }

    // SP của TV1: hủy đơn + cộng trả tồn kho + hoàn 1 lượt voucher (trong 1 Transaction)
    await cancelOrderTx(orderId, req.user.id, reason); // helper của TV1

    // DB đã commit -> bắn Socket cho cả Khách và Quán
    const io = req.app.get('io');
    const payload = {
      order_id: orderId,
      old_status: oldStatus,
      new_status: 'CANCELLED',
      reason,
      changed_at: new Date().toISOString(),
    };
    io.to(`customer_${order.customer_id}`).emit('ORDER_STATUS_CHANGED', payload);
    io.to(`restaurant_${order.restaurant_id}`).emit('ORDER_STATUS_CHANGED', payload);

    return res.json({ success: true, message: 'Đã hủy đơn, tồn kho và voucher đã được hoàn lại', data: payload });
  } catch (err) {
    if (err.code === 'P0001') {
      return res.status(400).json({ success: false, message: err.message });
    }
    console.error('[POST /orders/:id/cancel]', err);
    return res.status(500).json({ success: false, message: 'Lỗi server khi hủy đơn' });
  }
});

// =====================================================================
// PHẦN 4a: POST /api/orders/:id/verify-qr  (Quét QR xác nhận giao thành công)
// Body: { qr_token }
// - Shipper chỉ quét được đơn được gán cho mình; Chủ quán quét được đơn của quán mình
// - Chỉ quét khi đơn đang DELIVERING; đơn đã COMPLETED thì không quét lại được
// =====================================================================
function sameToken(a, b) {
  const x = Buffer.from(String(a));
  const y = Buffer.from(String(b));
  return x.length === y.length && crypto.timingSafeEqual(x, y); // chống dò token theo thời gian phản hồi
}

router.post('/orders/:id/verify-qr', authRequired, requireRole('SHIPPER', 'RESTAURANT'), async (req, res) => {
  const orderId = Number(req.params.id);
  const qrToken = req.body && req.body.qr_token;

  if (!Number.isInteger(orderId) || orderId <= 0) {
    return res.status(400).json({ success: false, message: 'Mã đơn không hợp lệ' });
  }
  if (typeof qrToken !== 'string' || qrToken.trim() === '') {
    return res.status(400).json({ success: false, message: 'Thiếu mã QR' });
  }

  try {
    const r = await query(
      `SELECT o.id, o.customer_id, o.restaurant_id, o.shipper_id, o.order_status,
              o.qr_verify_token, rest.owner_id
         FROM orders o
         JOIN restaurants rest ON rest.id = o.restaurant_id
        WHERE o.id = $1`,
      [orderId]
    );
    const order = r.rows[0];
    if (!order) {
      return res.status(404).json({ success: false, message: 'Không tìm thấy đơn hàng' });
    }

    // Kiểm tra quyền: đúng shipper được gán, hoặc đúng chủ quán
    const allowed = req.user.role === 'SHIPPER'
      ? order.shipper_id === req.user.id
      : order.owner_id === req.user.id;
    if (!allowed) {
      return res.status(403).json({ success: false, message: 'Bạn không có quyền xác nhận đơn này' });
    }

    if (order.order_status === 'COMPLETED') {
      return res.status(409).json({ success: false, message: 'Đơn này đã được xác nhận giao thành công trước đó' });
    }
    if (order.order_status !== 'DELIVERING') {
      return res.status(409).json({
        success: false,
        message: `Đơn đang ở trạng thái ${order.order_status}, chưa thể xác nhận giao hàng`,
      });
    }
    if (!sameToken(qrToken.trim(), order.qr_verify_token)) {
      return res.status(400).json({ success: false, message: 'Mã QR không đúng với đơn hàng này' });
    }

    // UPDATE có điều kiện để 2 lần quét cùng lúc chỉ thành công 1 lần (Trigger 1 ghi audit_logs)
    const upd = await query(
      `UPDATE orders SET order_status = 'COMPLETED'
        WHERE id = $1 AND order_status = 'DELIVERING'
        RETURNING id`,
      [orderId]
    );
    if (upd.rowCount === 0) {
      return res.status(409).json({ success: false, message: 'Đơn vừa được xác nhận bởi thao tác khác' });
    }

    const io = req.app.get('io');
    const payload = {
      order_id: orderId,
      old_status: 'DELIVERING',
      new_status: 'COMPLETED',
      changed_at: new Date().toISOString(),
    };
    io.to(`customer_${order.customer_id}`).emit('ORDER_STATUS_CHANGED', payload);
    io.to(`restaurant_${order.restaurant_id}`).emit('ORDER_STATUS_CHANGED', payload);

    return res.json({ success: true, message: 'Xác nhận giao hàng thành công', data: payload });
  } catch (err) {
    console.error('[POST /orders/:id/verify-qr]', err);
    return res.status(500).json({ success: false, message: 'Lỗi server khi xác nhận QR' });
  }
});

// =====================================================================
// PHẦN 4b: GET /api/restaurants/:id/report?from=YYYY-MM-DD&to=YYYY-MM-DD
// Trả doanh thu theo ngày + Top món bán chạy cho MPAndroidChart (chỉ tính đơn COMPLETED).
// Mặc định: 7 ngày gần nhất.
// =====================================================================
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

function toDateStr(d) {
  return d.toISOString().slice(0, 10);
}

// Gom việc truy vấn báo cáo vào 1 hàm để dễ thay bằng sp_get_restaurant_revenue_report của TV2
async function fetchRevenueReport(restaurantId, from, to) {
  const daily = await query(
    `SELECT to_char(o.created_at::date, 'YYYY-MM-DD') AS day,
            COUNT(*)::int AS orders,
            COALESCE(SUM(o.total_amount), 0) AS revenue
       FROM orders o
      WHERE o.restaurant_id = $1
        AND o.order_status = 'COMPLETED'
        AND o.created_at >= $2::date
        AND o.created_at <  ($3::date + 1)
      GROUP BY o.created_at::date
      ORDER BY o.created_at::date`,
    [restaurantId, from, to]
  );

  const top = await query(
    `SELECT m.id AS item_id, m.name,
            SUM(od.quantity)::int AS quantity_sold,
            COALESCE(SUM(od.subtotal), 0) AS revenue
       FROM order_details od
       JOIN orders o     ON o.id = od.order_id
       JOIN menu_items m ON m.id = od.item_id
      WHERE o.restaurant_id = $1
        AND o.order_status = 'COMPLETED'
        AND o.created_at >= $2::date
        AND o.created_at <  ($3::date + 1)
      GROUP BY m.id, m.name
      ORDER BY quantity_sold DESC, revenue DESC
      LIMIT 5`,
    [restaurantId, from, to]
  );

  const dailyRevenue = daily.rows.map((r) => ({ day: r.day, orders: r.orders, revenue: Number(r.revenue) }));
  return {
    daily_revenue: dailyRevenue,
    top_items: top.rows.map((r) => ({ ...r, revenue: Number(r.revenue) })),
    total_orders: dailyRevenue.reduce((s, d) => s + d.orders, 0),
    total_revenue: dailyRevenue.reduce((s, d) => s + d.revenue, 0),
  };
}

router.get('/restaurants/:id/report', authRequired, requireRole('RESTAURANT'), async (req, res) => {
  const restaurantId = Number(req.params.id);
  if (!Number.isInteger(restaurantId) || restaurantId <= 0) {
    return res.status(400).json({ success: false, message: 'Mã quán không hợp lệ' });
  }

  const today = new Date();
  const weekAgo = new Date(Date.now() - 6 * 24 * 3600 * 1000);
  const from = req.query.from || toDateStr(weekAgo);
  const to = req.query.to || toDateStr(today);

  if (!DATE_RE.test(from) || !DATE_RE.test(to) || isNaN(Date.parse(from)) || isNaN(Date.parse(to))) {
    return res.status(400).json({ success: false, message: 'Ngày phải có dạng YYYY-MM-DD' });
  }
  if (from > to) {
    return res.status(400).json({ success: false, message: 'from không được lớn hơn to' });
  }
  if ((Date.parse(to) - Date.parse(from)) / 86400000 > 366) {
    return res.status(400).json({ success: false, message: 'Khoảng thời gian tối đa 366 ngày' });
  }

  try {
    // Chống xem trộm doanh thu: quán phải thuộc chủ quán đang đăng nhập
    const own = await query('SELECT name FROM restaurants WHERE id = $1 AND owner_id = $2', [
      restaurantId,
      req.user.id,
    ]);
    if (own.rowCount === 0) {
      return res.status(403).json({ success: false, message: 'Bạn không có quyền xem báo cáo của quán này' });
    }

    const report = await fetchRevenueReport(restaurantId, from, to);
    return res.json({
      success: true,
      data: { restaurant_id: restaurantId, restaurant_name: own.rows[0].name, from, to, ...report },
    });
  } catch (err) {
    console.error('[GET /restaurants/:id/report]', err);
    return res.status(500).json({ success: false, message: 'Lỗi server khi lấy báo cáo' });
  }
});

module.exports = { router, authRequired, requireRole, getOrderDetail };
