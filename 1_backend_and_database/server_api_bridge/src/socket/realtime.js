'use strict';
// [TV3] Sự kiện Socket.io realtime: Đặt nhóm (SCR-12, 13, 14) và GPS Shipper (MapTrackingActivity).
// Phòng đặt nhóm lưu tạm trên RAM (Map), tự xóa sau GROUP_TTL_MS. Mọi sự kiện đều dựa trên
// socket.user đã được xác thực JWT ở server.js (không tin id do client gửi lên).
//
// Android gọi: socket.emit("create_group_room", jsonObject, Ack { args -> ... })
// Ack trả về { success, message?, ...dữ liệu }.  Mọi thay đổi giỏ nhóm đều broadcast GROUP_CART_UPDATED.

const crypto = require('crypto');
const { query } = require('../config/db'); // [TV1]

const GROUP_TTL_MS = 3 * 60 * 60 * 1000; // phòng nhóm tồn tại tối đa 3 giờ
const MAX_GROUPS = 2000;
const MAX_MEMBERS = 10;
const MAX_ITEM_LINES = 100;
const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // bỏ O/0/I/1 cho dễ đọc
const LOCATION_MIN_INTERVAL_MS = 1000; // mỗi socket gửi GPS tối đa 1 lần/giây
const SHIPPER_CACHE_MS = 15000;

const groups = new Map();        // roomCode -> group
const shipperOrderCache = new Map(); // `${shipperId}:${orderId}` -> { customerId, expires }

// Dọn phòng nhóm quá hạn
setInterval(() => {
  const now = Date.now();
  for (const [code, g] of groups) if (g.expiresAt <= now) groups.delete(code);
  for (const [key, v] of shipperOrderCache) if (v.expires <= now) shipperOrderCache.delete(key);
}, 10 * 60 * 1000).unref();

function reply(cb, payload) {
  if (typeof cb === 'function') cb(payload);
}
const fail = (cb, message) => reply(cb, { success: false, message });

function newRoomCode() {
  for (let attempt = 0; attempt < 20; attempt++) {
    let code = '';
    for (let i = 0; i < 6; i++) code += CODE_CHARS[crypto.randomInt(CODE_CHARS.length)];
    if (!groups.has(code)) return code;
  }
  return null;
}

function cleanName(value, fallback) {
  if (typeof value !== 'string') return fallback;
  const name = value.trim().slice(0, 100);
  return name || fallback;
}

function snapshot(g) {
  return {
    room_code: g.code,
    restaurant_id: g.restaurantId,
    host_user_id: g.hostId,
    members: [...g.members].map(([user_id, name]) => ({ user_id, name })),
    items: g.items,
    total_quantity: g.items.reduce((sum, it) => sum + it.quantity, 0),
  };
}

function broadcast(io, g) {
  io.to(`group_${g.code}`).emit('GROUP_CART_UPDATED', snapshot(g));
}

function findGroup(roomCode) {
  if (typeof roomCode !== 'string') return null;
  const g = groups.get(roomCode.trim().toUpperCase());
  if (!g || g.expiresAt <= Date.now()) return null;
  return g;
}

// Kiểm tra 1 dòng món do thành viên gửi lên (cùng quy tắc với POST /api/orders)
function validateGroupItem(it) {
  if (!it || typeof it !== 'object') return 'Món không hợp lệ';
  if (!Number.isInteger(it.item_id) || it.item_id <= 0) return 'item_id không hợp lệ';
  if (!Number.isInteger(it.quantity) || it.quantity < 1 || it.quantity > 99) return 'Số lượng phải từ 1 đến 99';
  if (it.extra_price != null && (typeof it.extra_price !== 'number' || !Number.isFinite(it.extra_price)
      || it.extra_price < 0 || it.extra_price > 10000000)) return 'extra_price không hợp lệ';
  if (it.kitchen_note != null && (typeof it.kitchen_note !== 'string' || it.kitchen_note.length > 500)) return 'kitchen_note quá dài';
  if (it.selected_options != null && (typeof it.selected_options !== 'object'
      || JSON.stringify(it.selected_options).length > 2000)) return 'selected_options không hợp lệ';
  return null;
}

function registerRealtimeHandlers(io, socket) {
  const user = socket.user; // { id, role, restaurant_id }

  // ------------------------------------------------------------------
  // create_group_room  { restaurant_id, display_name? }  (CUSTOMER)
  // ------------------------------------------------------------------
  socket.on('create_group_room', async (data, cb) => {
    try {
      if (user.role !== 'CUSTOMER') return fail(cb, 'Chỉ khách hàng được tạo phòng đặt nhóm');
      const restaurantId = data && data.restaurant_id;
      if (!Number.isInteger(restaurantId) || restaurantId <= 0) return fail(cb, 'restaurant_id không hợp lệ');
      if (groups.size >= MAX_GROUPS) return fail(cb, 'Hệ thống đang có quá nhiều phòng nhóm, hãy thử lại sau');

      const r = await query('SELECT 1 FROM restaurants WHERE id = $1 AND is_active IS TRUE', [restaurantId]);
      if (r.rowCount === 0) return fail(cb, 'Quán không tồn tại hoặc đang đóng');

      const code = newRoomCode();
      if (!code) return fail(cb, 'Không tạo được mã phòng, hãy thử lại');

      const g = {
        code,
        restaurantId,
        hostId: user.id,
        members: new Map([[user.id, cleanName(data.display_name, `Khách ${user.id}`)]]),
        items: [],
        expiresAt: Date.now() + GROUP_TTL_MS,
      };
      groups.set(code, g);
      socket.join(`group_${code}`);
      broadcast(io, g);
      return reply(cb, { success: true, ...snapshot(g) });
    } catch (err) {
      console.error('[socket create_group_room]', err);
      return fail(cb, 'Lỗi server khi tạo phòng nhóm');
    }
  });

  // ------------------------------------------------------------------
  // join_group_room  { room_code, display_name? }  (CUSTOMER) - gọi lại để vào lại phòng khi mất kết nối
  // ------------------------------------------------------------------
  socket.on('join_group_room', (data, cb) => {
    if (user.role !== 'CUSTOMER') return fail(cb, 'Chỉ khách hàng được tham gia đặt nhóm');
    const g = findGroup(data && data.room_code);
    if (!g) return fail(cb, 'Mã phòng không tồn tại hoặc đã hết hạn');
    if (!g.members.has(user.id) && g.members.size >= MAX_MEMBERS) return fail(cb, `Phòng đã đủ ${MAX_MEMBERS} người`);

    g.members.set(user.id, cleanName(data.display_name, g.members.get(user.id) || `Khách ${user.id}`));
    socket.join(`group_${g.code}`);
    broadcast(io, g);
    return reply(cb, { success: true, ...snapshot(g) });
  });

  // ------------------------------------------------------------------
  // group_add_item  { room_code, item: { item_id, quantity, extra_price?, selected_options?, kitchen_note? } }
  // group_member_name do SERVER gắn theo tên thành viên, client không giả mạo được.
  // ------------------------------------------------------------------
  socket.on('group_add_item', (data, cb) => {
    const g = findGroup(data && data.room_code);
    if (!g) return fail(cb, 'Mã phòng không tồn tại hoặc đã hết hạn');
    if (!g.members.has(user.id)) return fail(cb, 'Bạn chưa tham gia phòng này');
    const error = validateGroupItem(data.item);
    if (error) return fail(cb, error);
    if (g.items.length >= MAX_ITEM_LINES) return fail(cb, `Giỏ nhóm tối đa ${MAX_ITEM_LINES} dòng món`);

    const it = data.item;
    const line = {
      line_id: crypto.randomUUID(),
      user_id: user.id,
      item_id: it.item_id,
      quantity: it.quantity,
      extra_price: it.extra_price || 0,
      selected_options: it.selected_options ?? null,
      kitchen_note: it.kitchen_note ?? null,
      group_member_name: g.members.get(user.id),
    };
    g.items.push(line);
    broadcast(io, g);
    return reply(cb, { success: true, line_id: line.line_id });
  });

  // ------------------------------------------------------------------
  // group_remove_item  { room_code, line_id }  (người thêm món, hoặc chủ phòng)
  // ------------------------------------------------------------------
  socket.on('group_remove_item', (data, cb) => {
    const g = findGroup(data && data.room_code);
    if (!g) return fail(cb, 'Mã phòng không tồn tại hoặc đã hết hạn');
    const index = g.items.findIndex((it) => it.line_id === (data && data.line_id));
    if (index === -1) return fail(cb, 'Không tìm thấy món trong giỏ nhóm');
    if (g.items[index].user_id !== user.id && g.hostId !== user.id) return fail(cb, 'Bạn không được xóa món của người khác');
    g.items.splice(index, 1);
    broadcast(io, g);
    return reply(cb, { success: true });
  });

  // ------------------------------------------------------------------
  // update_shipper_location  { order_id, lat, lng }  (SHIPPER, đơn đang DELIVERING và được gán cho mình)
  // -> emit SHIPPER_LOCATION_CHANGED tới room customer_<customer_id>
  // ------------------------------------------------------------------
  let lastLocationAt = 0;
  socket.on('update_shipper_location', async (data, cb) => {
    try {
      if (user.role !== 'SHIPPER') return fail(cb, 'Chỉ shipper được gửi vị trí');
      const orderId = data && data.order_id;
      const lat = data && data.lat;
      const lng = data && data.lng;
      if (!Number.isInteger(orderId) || orderId <= 0) return fail(cb, 'order_id không hợp lệ');
      if (typeof lat !== 'number' || typeof lng !== 'number' || !Number.isFinite(lat) || !Number.isFinite(lng)
          || lat < -90 || lat > 90 || lng < -180 || lng > 180) return fail(cb, 'Tọa độ không hợp lệ');

      const now = Date.now();
      if (now - lastLocationAt < LOCATION_MIN_INTERVAL_MS) return reply(cb, { success: true, throttled: true });
      lastLocationAt = now;

      const key = `${user.id}:${orderId}`;
      let cached = shipperOrderCache.get(key);
      if (!cached || cached.expires <= now) {
        const r = await query(
          "SELECT customer_id FROM orders WHERE id = $1 AND shipper_id = $2 AND order_status = 'DELIVERING'",
          [orderId, user.id]
        );
        if (r.rowCount === 0) return fail(cb, 'Đơn không tồn tại, không phải của bạn hoặc không ở trạng thái đang giao');
        cached = { customerId: r.rows[0].customer_id, expires: now + SHIPPER_CACHE_MS };
        shipperOrderCache.set(key, cached);
      }

      io.to(`customer_${cached.customerId}`).emit('SHIPPER_LOCATION_CHANGED', {
        order_id: orderId,
        lat,
        lng,
        updated_at: new Date(now).toISOString(),
      });
      return reply(cb, { success: true });
    } catch (err) {
      console.error('[socket update_shipper_location]', err);
      return fail(cb, 'Lỗi server khi cập nhật vị trí');
    }
  });
}

module.exports = { registerRealtimeHandlers };
