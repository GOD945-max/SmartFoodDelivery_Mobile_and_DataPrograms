# TV2: SQL và API thực đơn

Chạy file 04 và 05 sau khi đã cài schema 01 và stored procedures 02 vào **database thử nghiệm**. File 01 xóa bảng hiện có; không chạy trên database cần giữ dữ liệu. File 04 dùng hàm tính khoảng cách của TV1 trong file 02.

Các route của TV2 được export tại `src/controllers/auth_menu.controller.js` và dự kiến TV3 gắn dưới `/api` trong server Express. Phụ thuộc: `express`, `pg`, `bcryptjs`, `jsonwebtoken`, `express-rate-limit`; cấu hình kết nối trong `src/config/db.js`. Thiết lập biến `JWT_SECRET` có ít nhất 32 byte trước khi đăng nhập hoặc đăng ký. Đăng ký công khai tạo tài khoản CUSTOMER; tài khoản quán và shipper cần được cấp bằng quy trình khác.

Các API gồm đăng ký/đăng nhập, danh sách quán, menu, CRUD món và voucher theo chủ quán. Xác nhận hợp đồng JSON và cách tích hợp với TV3 trước khi kết nối Android.

Đề xuất tích hợp PR #3: mật khẩu đăng ký tối thiểu 8 ký tự; đăng nhập vẫn hỗ trợ tài khoản mẫu hiện có. Phản hồi auth giữ các trường phẳng cũ, đồng thời thêm `success` và `user` (gồm phone, loyalty_points, member_tier). Tìm quán qua `GET /api/restaurants?search=...`; gợi ý voucher qua `GET /api/vouchers/suggest?restaurant_id=1&subtotal=50000&shipping_fee=15000` (shipping_fee tùy chọn). Số tiền gợi ý là dự kiến, giao dịch đặt đơn vẫn phải kiểm tra lại. Middleware export cả `authenticateToken` và bí danh `verifyToken`. TV3 có thể gọi `jwtSecret()` khi khởi động server để phát hiện thiếu cấu hình trước khi phục vụ yêu cầu; file server/package/.env.example thuộc TV3.

Thủ tục báo cáo vẫn nhận bốn đối số như trước; có thêm cách gọi `CALL sp_get_restaurant_revenue_report(1, NULL)` cho bảy ngày gần nhất. Theo xác nhận của nhóm, báo cáo lọc theo **ngày tạo đơn**; không bổ sung trigger INSERT cho audit.

**Trạng thái kiểm thử:** đã chạy schema, thủ tục TV1, SQL TV2 và seed trong PostgreSQL PGlite độc lập; kiểm tra tuần tự audit, tồn kho, giới hạn shipper, view và báo cáo, cũng như HTTP đăng ký/đăng nhập, tìm quán và gợi ý voucher với database đó. Chưa kiểm thử đồng thời trên PostgreSQL server hoặc tích hợp với server của TV3. Cần kiểm tra thêm audit cùng thủ tục hủy của TV1, phân quyền và tranh chấp giao dịch. PostgreSQL có thể trả lỗi 40001 khi giao dịch cạnh tranh; ứng dụng cần xử lý thử lại toàn bộ giao dịch.
