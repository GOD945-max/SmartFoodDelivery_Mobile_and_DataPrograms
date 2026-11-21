// [TV3] Khởi tạo Express + Socket.io Server
require('dotenv').config();

const http = require('http');
const express = require('express');
const cors = require('cors');
const jwt = require('jsonwebtoken');
const { Server } = require('socket.io');
const { jwtSecret } = require('./middlewares/auth.jwt'); // [TV2]
const { testConnection } = require('./config/db'); // [TV1]

jwtSecret(); // fail-fast: báo lỗi ngay khi khởi động nếu JWT_SECRET thiếu hoặc < 32 byte

const app = express();
const server = http.createServer(app);

// ---------- Socket.io ----------
const io = new Server(server, {
  cors: { origin: '*' },
});

// Xác thực JWT khi client (Android) kết nối socket, dùng ĐÚNG cấu hình token của TV2
// (HS256, issuer, audience; id người dùng nằm trong claim `sub`).
// Android gửi token qua: IO.Options.auth = { token: "<JWT>" }
const TOKEN_OPTIONS = { issuer: 'smartfood-api', audience: 'smartfood-mobile', algorithms: ['HS256'] };
const VALID_ROLES = ['CUSTOMER', 'RESTAURANT', 'SHIPPER'];

io.use((socket, next) => {
  try {
    const token = socket.handshake.auth?.token || socket.handshake.query?.token;
    if (!token) return next(new Error('UNAUTHORIZED: thiếu token'));

    const claims = jwt.verify(token, jwtSecret(), TOKEN_OPTIONS);
    const id = Number(claims.sub);
    if (!Number.isInteger(id) || id < 1 || !VALID_ROLES.includes(claims.role)) {
      return next(new Error('UNAUTHORIZED: token không hợp lệ'));
    }
    socket.user = { id, role: claims.role, restaurant_id: claims.restaurant_id };
    next();
  } catch (err) {
    next(new Error('UNAUTHORIZED: token không hợp lệ hoặc đã hết hạn'));
  }
});

io.on('connection', (socket) => {
  const { id, role, restaurant_id } = socket.user;

  if (role === 'RESTAURANT' && restaurant_id) socket.join(`restaurant_${restaurant_id}`);
  if (role === 'CUSTOMER') socket.join(`customer_${id}`);
  if (role === 'SHIPPER') socket.join(`shipper_${id}`);

  const rooms = [...socket.rooms].filter((r) => r !== socket.id);
  console.log(`[socket] +${role} #${id} vào room: ${rooms.join(', ')}`);
  socket.emit('JOINED', { rooms });

  socket.on('disconnect', (reason) => {
    console.log(`[socket] -${role} #${id} rời (${reason})`);
  });
});

// Controller lấy io bằng: req.app.get('io')
app.set('io', io);

// ---------- Express ----------
// KHÔNG dùng express.json() toàn cục: router TV2 và TV3 tự parse JSON với giới hạn riêng
app.use(cors());

app.get('/health', (req, res) => {
  res.json({ status: 'ok', time: new Date().toISOString() });
});

// Router của TV2: Đăng nhập, Danh sách quán, Menu, Voucher (module.exports = router)
const authMenu = require('./controllers/auth_menu.controller');
app.use('/api', authMenu);

// Router của TV3: Đặt đơn, Đổi trạng thái, Hủy đơn, Quét QR, Báo cáo
const orderReport = require('./controllers/order_report.controller');
app.use('/api', orderReport.router);

app.use((req, res) => {
  res.status(404).json({ success: false, message: 'Không tìm thấy API' });
});

app.use((err, req, res, next) => {
  if (res.headersSent) return next(err);
  if (err.type === 'entity.parse.failed') {
    return res.status(400).json({ success: false, message: 'JSON không hợp lệ' });
  }
  if (err.status === 413) {
    return res.status(413).json({ success: false, message: 'Nội dung yêu cầu quá lớn' });
  }
  console.error('[error]', err);
  res.status(500).json({ success: false, message: 'Lỗi server' });
});

const PORT = process.env.PORT || 3000;
// 0.0.0.0 để emulator Android truy cập được qua http://10.0.2.2:3000
server.listen(PORT, '0.0.0.0', () => {
  console.log(`Server chạy tại http://localhost:${PORT}`);
  testConnection(); // in trạng thái kết nối PostgreSQL và danh sách bảng
});

module.exports = { app, server, io };
