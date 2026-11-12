/**
 * =============================================================================
 * ĐỒ ÁN GỘP 2 MÔN: DATABASE PROGRAMMING & MOBILE APPLICATION PROGRAMMING
 * ĐỀ TÀI: HỆ SINH THÁI ĐẶT ĐỒ ĂN & QUẢN LÝ F&B (SMART FOOD DELIVERY / FOODFLOW)
 * PHÂN HỆ: THÀNH VIÊN 1 (TV1 - DATABASE ARCHITECT: TRUNG)
 * FILE 05: 1_backend_and_database/server_api_bridge/src/config/db.js
 * Mô tả:
 *   - Cấu hình Connection Pool kết nối trực tiếp PostgreSQL 15/16 bằng thư viện `pg` thuần
 *   - Cung cấp hàm query(), testConnection() và 2 hàm Helper gọi thẳng Stored Procedures ACID:
 *       1. createOrderTx(orderPayload) -> Gọi CALL sp_create_order(...)
 *       2. cancelOrderTx(orderId, userId, reason) -> Gọi CALL sp_cancel_order_restore_stock(...)
 * =============================================================================
 */

const fs = require('fs');
const path = require('path');
const { Pool } = require('pg');

// Tự động tìm và nạp file .env ở thư mục server_api_bridge hoặc thư mục gốc dự án
const envCandidates = [
    path.resolve(__dirname, '../../.env'),
    path.resolve(__dirname, '../../../../.env'),
];

for (const envPath of envCandidates) {
    if (fs.existsSync(envPath)) {
        try {
            require('dotenv').config({ path: envPath });
        } catch (err) {
            // Fallback đọc .env thuần nếu chưa cài gói dotenv
            const lines = fs.readFileSync(envPath, 'utf8').split(/\r?\n/);
            for (const line of lines) {
                const match = line.match(/^\s*([\w.-]+)\s*=\s*(.*)?\s*$/);
                if (match && !process.env[match[1]]) {
                    process.env[match[1]] = (match[2] || '').replace(/^['"]|['"]$/g, '').trim();
                }
            }
        }
        break;
    }
}

const dbConfig = {
    host: process.env.DB_HOST || 'localhost',
    port: parseInt(process.env.DB_PORT || '5432', 10),
    user: process.env.DB_USER || 'postgres',
    password: process.env.DB_PASSWORD || 'postgres',
    database: process.env.DB_NAME || 'smart_food_db',
    max: 20,                        // Tối đa 20 kết nối đồng thời trong Pool
    idleTimeoutMillis: 30000,       // Đóng kết nối nhàn rỗi sau 30 giây
    connectionTimeoutMillis: 5000,  // Timeout nếu sau 5 giây không kết nối được CSDL
};

const pool = new Pool(dbConfig);

// Lắng nghe sự kiện lỗi bất ngờ trên client nhàn rỗi trong Pool
pool.on('error', (err) => {
    console.error('[PostgreSQL Pool Error] Lỗi kết nối ngầm:', err.message);
});

/**
 * Hàm thực thi câu lệnh SQL / Stored Procedure / View / Function dùng chung cho TV2 & TV3
 * @param {string} text - Câu lệnh SQL có tham số ($1, $2, ...)
 * @param {Array} params - Mảng giá trị truyền vào
 */
async function query(text, params = []) {
    const start = Date.now();
    const res = await pool.query(text, params);
    const duration = Date.now() - start;
    if (process.env.DEBUG_SQL === 'true') {
        console.log('[SQL Executed]', { text, duration: `${duration}ms`, rows: res.rowCount });
    }
    return res;
}

/**
 * Kiểm tra kết nối tới PostgreSQL & liệt kê trạng thái 8 bảng chuẩn 3NF
 */
async function testConnection() {
    let client;
    try {
        client = await pool.connect();
        const versionRes = await client.query('SELECT version(), current_database(), current_user;');
        const tablesRes = await client.query(`
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_type = 'BASE TABLE'
            ORDER BY table_name;
        `);

        console.log('=====================================================================');
        console.log('✅ [TV1 - db.js] KẾT NỐI POSTGRESQL THÀNH CÔNG!');
        console.log(`   • Database : ${versionRes.rows[0].current_database}`);
        console.log(`   • User     : ${versionRes.rows[0].current_user}`);
        console.log(`   • Host     : ${dbConfig.host}:${dbConfig.port}`);
        console.log(`   • Version  : ${versionRes.rows[0].version.split(',')[0]}`);
        console.log(`   • Các bảng hiện có (${tablesRes.rowCount} bảng):`,
            tablesRes.rows.map((r) => r.table_name).join(', ') || '(Chưa chạy 01_schema_tables.sql)');
        console.log('=====================================================================');
        return true;
    } catch (error) {
        console.error('=====================================================================');
        console.error('❌ [TV1 - db.js] LỖI KẾT NỐI POSTGRESQL:', error.message);
        console.error('👉 Hãy kiểm tra lại dịch vụ PostgreSQL, tên DB `smart_food_db` và mật khẩu trong file .env');
        console.error('=====================================================================');
        return false;
    } finally {
        if (client) client.release();
    }
}

/**
 * Helper 1: Gọi Stored Procedure `sp_create_order` (Transaction ACID chống Race Condition)
 * Dùng cho TV3 trong API `POST /api/orders`
 * @param {Object} orderPayload
 * @param {number} orderPayload.customer_id
 * @param {number} orderPayload.restaurant_id
 * @param {string|null} orderPayload.voucher_code
 * @param {string} orderPayload.delivery_address
 * @param {number} orderPayload.delivery_lat
 * @param {number} orderPayload.delivery_lng
 * @param {Array} orderPayload.items - Mảng [{ item_id, quantity, selected_options, kitchen_note }]
 * @param {string} [orderPayload.payment_method='COD']
 * @param {boolean} [orderPayload.take_utensils=false]
 * @param {boolean} [orderPayload.is_group_order=false]
 */
async function createOrderTx(orderPayload) {
    const {
        customer_id,
        restaurant_id,
        voucher_code = null,
        delivery_address,
        delivery_lat,
        delivery_lng,
        items = [],
        payment_method = 'COD',
        take_utensils = false,
        is_group_order = false,
    } = orderPayload;

    const sql = `
        CALL sp_create_order(
            $1::INT,
            $2::INT,
            $3::VARCHAR,
            $4::TEXT,
            $5::DOUBLE PRECISION,
            $6::DOUBLE PRECISION,
            $7::JSONB,
            NULL,
            NULL,
            NULL,
            $8::VARCHAR,
            $9::BOOLEAN,
            $10::BOOLEAN
        );
    `;

    const params = [
        customer_id,
        restaurant_id,
        voucher_code,
        delivery_address,
        delivery_lat,
        delivery_lng,
        JSON.stringify(items),
        payment_method,
        take_utensils,
        is_group_order,
    ];

    const result = await pool.query(sql, params);
    const row = result.rows[0] || {};

    return {
        order_id: row.o_order_id,
        total_amount: row.o_total_amount !== undefined ? Number(row.o_total_amount) : null,
        qr_verify_token: row.o_qr_verify_token,
        // Giữ cả tên gốc INOUT của PostgreSQL để tương thích 100% với mọi cách đọc của TV3
        o_order_id: row.o_order_id,
        o_total_amount: row.o_total_amount,
        o_qr_verify_token: row.o_qr_verify_token,
    };
}

/**
 * Helper 2: Gọi Stored Procedure `sp_cancel_order_restore_stock` (Hủy đơn hoàn trả tồn kho & Voucher)
 * @param {number} orderId - ID đơn hàng cần hủy
 * @param {number|null} [userId=null] - ID người thực hiện hủy
 * @param {string} [reason='Khách hàng yêu cầu hủy đơn'] - Lý do hủy đơn
 */
async function cancelOrderTx(orderId, userId = null, reason = 'Khách hàng yêu cầu hủy đơn') {
    const sql = `CALL sp_cancel_order_restore_stock($1::INT, $2::INT, $3::TEXT);`;
    await pool.query(sql, [orderId, userId, reason]);
    return {
        success: true,
        order_id: orderId,
        new_status: 'CANCELLED',
        message: 'Đã hủy đơn hàng, hoàn trả tồn kho và lượt dùng Voucher thành công!',
    };
}

module.exports = {
    pool,
    query,
    testConnection,
    createOrderTx,
    cancelOrderTx,
};

// Cho phép chạy trực tiếp `node src/config/db.js` để kiểm tra kết nối nhanh
if (require.main === module) {
    testConnection().then((ok) => {
        pool.end();
        process.exit(ok ? 0 : 1);
    });
}
