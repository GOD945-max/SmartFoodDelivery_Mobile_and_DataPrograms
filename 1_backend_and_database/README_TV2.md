# TV2: SQL và API thực đơn

Chạy file 04 và 05 sau khi đã cài schema 01 và stored procedures 02 vào **database thử nghiệm**. File 01 xóa bảng hiện có; không chạy trên database cần giữ dữ liệu. File 04 dùng hàm tính khoảng cách của TV1 trong file 02.

Các route của TV2 được export tại `src/controllers/auth_menu.controller.js` và dự kiến TV3 gắn dưới `/api` trong server Express. Phụ thuộc: `express`, `pg`, `bcryptjs`, `jsonwebtoken`, `express-rate-limit`; cấu hình kết nối trong `src/config/db.js`. Thiết lập biến `JWT_SECRET` có ít nhất 32 byte trước khi đăng nhập hoặc đăng ký. Đăng ký công khai tạo tài khoản CUSTOMER; tài khoản quán và shipper cần được cấp bằng quy trình khác.

Các API gồm đăng ký/đăng nhập, danh sách quán, menu, CRUD món và voucher theo chủ quán. Xác nhận hợp đồng JSON và cách tích hợp với TV3 trước khi kết nối Android.

**Trạng thái kiểm thử:** đã kiểm tra cú pháp JavaScript và tải router với các thư viện có sẵn. Chưa xác nhận SQL, API với database thực, kiểm thử đồng thời hoặc tích hợp server: cần PostgreSQL thử nghiệm và server của TV3. Kiểm tra thêm luồng audit khi hủy, tồn kho, giới hạn 2 đơn DELIVERING cho shipper, phân quyền và báo cáo. PostgreSQL có thể trả lỗi 40001 khi giao dịch cạnh tranh; ứng dụng cần xử lý thử lại toàn bộ giao dịch.
