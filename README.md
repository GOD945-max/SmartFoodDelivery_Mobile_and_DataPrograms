# 🍔 HỆ SINH THÁI ĐẶT ĐỒ ĂN & QUẢN LÝ F&B (SMART FOOD DELIVERY)

> **Đồ Án Gộp 2 Môn:** **Database Programming** (`PostgreSQL 16` / `pgAdmin 4`) • **Mobile Application Programming** (`Android Studio Native` - MVVM)  
> **Quy mô:** 5 Thành viên (2 Database & SQL Programmability + 1 Backend API & Socket.io Bridge + 2 Mobile Android Studio)

---

## I. CẤU TRÚC THƯ MỤC DỰ ÁN (PHÂN RÕ AI CODE FILE NÀO ĐỂ KHÔNG ĐỤNG CODE)

```text
SmartFoodDelivery_Mobile_and_DataPrograms/
├── 1_backend_and_database/                   # === KHU VỰC CỦA TV1, TV2, TV3 ===
│   ├── database_pgadmin/
│   │   ├── 01_schema_tables.sql              # [TV1] Tạo 8 bảng chuẩn 3NF, PK, FK, CHECK
│   │   ├── 02_stored_procedures_acid.sql     # [TV1] SP đặt đơn ACID, SP hủy đơn hoàn kho
│   │   ├── 03_indexes_and_explain.sql        # [TV1] Tạo Index & Script bơm 10.000 dòng đo EXPLAIN
│   │   ├── 04_triggers_and_functions.sql     # [TV2] 3 Triggers, Hàm tính khoảng cách GPS, Views
│   │   ├── 05_report_procedures.sql          # [TV2] SP thống kê doanh thu & Top món bán chạy
│   │   └── 06_seed_data.sql                  # [TV1 & TV2] Dữ liệu mẫu (4 quán, 20 món, 5 voucher)
│   └── server_api_bridge/
│       └── src/
│           ├── server.js                     # [TV3] Khởi tạo Express + Socket.io Server
│           ├── config/db.js                  # [TV1] Cấu hình Pool kết nối PostgreSQL
│           ├── middlewares/auth.jwt.js       # [TV2] Xác thực JWT & Phân quyền (CUSTOMER/RESTAURANT/SHIPPER)
│           └── controllers/
│               ├── auth_menu.controller.js   # [TV2] API Đăng nhập, Danh sách quán, CRUD Món ăn & Voucher
│               └── order_report.controller.js# [TV3] API Đặt đơn, Đổi trạng thái, Quét QR, Báo cáo doanh thu
│
└── 2_mobile_android_app/                     # === KHU VỰC CỦA TV4 & TV5 (ANDROID STUDIO) ===
    └── app/src/main/java/com/group/smartfood/
        ├── data/
        │   ├── api/                          # [TV5] RetrofitClient, ApiService, AuthInterceptor
        │   └── local_room/                   # [TV4] CartDatabase, CartDao, CartItemEntity (Room DB)
        ├── utils/                            # [TV5] SocketManager.java, NotificationHelper.java
        └── ui/
            ├── auth/                         # [TV4] LoginActivity, RegisterActivity (Tự rẽ nhánh theo Role)
            ├── customer/                     # === KHU VỰC CỦA THÀNH VIÊN 4 (LUỒNG KHÁCH HÀNG) ===
            │   ├── CustomerHomeFragment      # [TV4] Danh sách quán ăn, tìm kiếm & lọc danh mục
            │   ├── RestaurantMenuActivity    # [TV4] Xem menu món ăn, chọn số lượng, thêm vào Room DB
            │   ├── CartCheckoutActivity      # [TV4] Giỏ hàng Offline, áp mã Voucher, bấm Đặt hàng
            │   └── CustomerOrdersFragment    # [TV4] Lịch sử đơn, xem tiến trình & hiện Mã QR nhận hàng
            └── partner_restaurant_shipper/   # === KHU VỰC CỦA THÀNH VIÊN 5 (CHỦ QUÁN & PHẦN CỨNG) ===
                ├── PartnerOrdersFragment     # [TV5] Chủ quán nhận đơn realtime, bấm Duyệt/Nấu/Giao
                ├── MenuManageActivity        # [TV5] Chủ quán Thêm/Sửa món ăn & Nhập thêm tồn kho
                ├── RevenueChartFragment      # [TV5] Vẽ biểu đồ doanh thu trên App (MPAndroidChart)
                ├── MapTrackingActivity       # [TV5] Định vị GPS & Vẽ đường đi giao hàng (Google Maps)
                └── QrScannerActivity         # [TV5] Mở Camera quét mã QR xác nhận giao hàng thành công
```

---

## II. QUY TRÌNH LÀM VIỆC VỚI GIT / GITHUB CHO 5 THÀNH VIÊN (CHỐNG CONFLICT 100%)

### 1. Bảng phân nhánh (Branch) & Phạm vi File cho từng thành viên

| Thành viên | Nhánh làm việc (Branch) | Phạm vi File được phép sửa & Push | Mẫu Commit Message |
| :--- | :--- | :--- | :--- |
| **TV1** *(Database Architect)* | `feature/tv1-db-schema-acid` | `01_schema_tables.sql`, `02_stored_procedures_acid.sql`, `03_indexes_and_explain.sql`, `config/db.js` | `feat(db): add 3NF schema & ACID SP` |
| **TV2** *(Triggers & Menu API)* | `feature/tv2-triggers-menu-api` | `04_triggers_and_functions.sql`, `05_report_procedures.sql`, `06_seed_data.sql`, `auth.jwt.js`, `auth_menu.controller.js` | `feat(api): add triggers & auth menu API` |
| **TV3** *(Order & Socket.io)* | `feature/tv3-order-socket-api` | `server.js`, `order_report.controller.js`, `package.json`, Postman Collection | `feat(socket): add order API & realtime` |
| **TV4** *(Mobile Khách & Room)* | `feature/tv4-mobile-customer` | `data/local_room/*`, `ui/auth/*`, `ui/customer/*` | `feat(mobile): add customer UI & Room DB` |
| **TV5** *(Mobile Quán, Maps, QR)* | `feature/tv5-mobile-partner` | `data/api/*`, `utils/*`, `ui/partner_restaurant_shipper/*` | `feat(mobile): add partner, Maps & QR` |

### 2. Bộ lệnh Git thực hành từng bước

#### Bước 1: Nhóm trưởng khởi tạo Repo & đẩy khung dự án lên GitHub
```bash
git init
git branch -M main
git add .
git commit -m "chore: init project structure and plan for 5 members"
git remote add origin https://github.com/GOD945-max/SmartFoodDelivery_Mobile_and_DataPrograms.git
git push -u origin main

# Tạo nhánh develop dùng để ghép code chung và 5 nhánh thành viên
git checkout -b develop
git push -u origin develop
```

#### Bước 2: Các thành viên (TV1 -> TV5) Clone về máy & chuyển sang nhánh riêng
```bash
git clone https://github.com/GOD945-max/SmartFoodDelivery_Mobile_and_DataPrograms.git
cd SmartFoodDelivery_Mobile_and_DataPrograms
git checkout develop
git checkout -b feature/tv1-db-schema-acid   # Đổi theo đúng nhánh của TV1..TV5 ở bảng trên
```

#### Bước 3: Quy trình Code & Push hằng ngày của từng thành viên
```bash
# 1. Kéo code mới nhất từ nhánh develop về trước khi bắt đầu làm việc
git pull origin develop

# 2. Kiểm tra các file mình đã sửa (đảm bảo đúng phạm vi được phân công)
git status

# 3. Đưa file vào vùng Staging & Commit
git add .
git commit -m "feat(tv1): hoàn thành 01_schema_tables.sql và sp_create_order"

# 4. Đẩy lên nhánh cá nhân trên GitHub và tạo Pull Request (PR) gộp vào nhánh develop
git push -u origin feature/tv1-db-schema-acid
```
