'use strict';

const express = require('express');
const bcrypt = require('bcryptjs');
const { rateLimit } = require('express-rate-limit');
const { query } = require('../config/db');
const { authenticateToken, authorizeRoles, signAccessToken, jwtSecret } =
    require('../middlewares/auth.jwt');

const router = express.Router();
const ownerOnly = [authenticateToken, authorizeRoles('RESTAURANT')];
const json = express.json({ limit: '16kb' });
const authJson = express.json({ limit: '2kb', inflate: false });
const bcryptCost = 12;
const dummyHash = bcrypt.hashSync('unused login timing value', bcryptCost);

function authLimiter(options) {
    return rateLimit({
        windowMs: 15 * 60 * 1000,
        standardHeaders: 'draft-8',
        legacyHeaders: false,
        message: { message: 'Quá nhiều yêu cầu. Hãy thử lại sau.' },
        ...options,
    });
}

const loginIpLimit = authLimiter({ limit: 30, identifier: 'login-ip' });
const loginAccountLimit = authLimiter({
    limit: 10,
    identifier: 'login-account',
    skipSuccessfulRequests: true,
    skip: (req) => !validUsername(req.body?.username),
    keyGenerator: (req) => req.body.username.toLowerCase(),
});
const registerIpLimit = authLimiter({ limit: 5, identifier: 'register-ip' });

class ApiError extends Error {
    constructor(status, message) {
        super(message);
        this.status = status;
    }
}

function route(handler) {
    return (req, res, next) => Promise.resolve(handler(req, res)).catch(next);
}

function requireJson(req, res, next) {
    if (!req.is('application/json')) {
        return res.status(415).json({ message: 'Cần Content-Type application/json' });
    }
    return next();
}

function validUsername(value) {
    return typeof value === 'string' && /^[a-zA-Z0-9_.-]{3,50}$/.test(value);
}

function validPassword(value, minCharacters) {
    return typeof value === 'string' && Buffer.byteLength(value) <= 72 &&
        !value.includes('\0') && [...value].length >= minCharacters;
}

function requireId(value) {
    if (!/^\d+$/.test(value) || !integer(1)(Number(value))) {
        throw new ApiError(400, 'ID không hợp lệ');
    }
    return Number(value);
}

function validatedFields(body, rules, required = []) {
    if (!body || typeof body !== 'object' || Array.isArray(body)) {
        throw new ApiError(400, 'Cần một đối tượng JSON');
    }
    const names = Object.keys(body);
    if (names.length === 0 || required.some((name) => !Object.hasOwn(body, name))) {
        throw new ApiError(400, 'Thiếu trường bắt buộc');
    }
    for (const name of names) {
        if (!Object.hasOwn(rules, name) || !rules[name](body[name])) {
            throw new ApiError(400, 'Dữ liệu hoặc trường không hợp lệ');
        }
    }
    return names;
}

const nonEmpty = (max) => (value) => typeof value === 'string' &&
    value.trim().length > 0 && value.length <= max && !value.includes('\0');
const nullableText = (max) => (value) => value === null ||
    (typeof value === 'string' && value.length <= max && !value.includes('\0'));
const integer = (min, max = 2147483647) => (value) =>
    Number.isInteger(value) && value >= min && value <= max;
const money = (min) => (value) => Number.isFinite(value) && value >= min &&
    value <= 9999999999.99 && /^\d+(\.\d{1,2})?$/.test(String(value));
const date = (value) => typeof value === 'string' && /^(?!0000)\d{4}-\d{2}-\d{2}$/.test(value) &&
    Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value;

// Columns always come from validatedFields, which only accepts hardcoded rule names.
function writeFields(body, rules, required) {
    const names = validatedFields(body, rules, required);
    const params = names.map((name, index) => '$' + (index + 3));
    return { names, params, values: names.map((name) => body[name]) };
}

function authResponse(res, status, user) {
    res.set('Cache-Control', 'no-store');
    return res.status(status).json({
        token: signAccessToken(user),
        user_id: user.id,
        username: user.username,
        full_name: user.full_name,
        role: user.role,
        restaurant_id: user.restaurant_id,
    });
}

router.post('/auth/login', loginIpLimit, requireJson, authJson, loginAccountLimit, route(async (req, res) => {
    validatedFields(req.body, { username: validUsername, password: (v) => validPassword(v, 1) },
        ['username', 'password']);
    const { username, password } = req.body;
    const { rows: [user] } = await query(
        "SELECT u.id, u.username, u.password_hash, u.full_name, u.role, " +
        "(SELECT MIN(r.id) FROM restaurants r WHERE r.owner_id = u.id AND u.role = 'RESTAURANT') AS restaurant_id " +
        "FROM users u WHERE u.username = $1",
        [username]
    );
    const matches = await bcrypt.compare(password, user?.password_hash || dummyHash);
    if (!user || !matches) throw new ApiError(401, 'Sai tên đăng nhập hoặc mật khẩu');
    return authResponse(res, 200, user);
}));

router.post('/auth/register', registerIpLimit, requireJson, authJson, route(async (req, res) => {
    validatedFields(req.body, {
        username: validUsername,
        password: (v) => validPassword(v, 15),
        full_name: nonEmpty(100),
        phone: (v) => typeof v === 'string' && /^\+?\d{8,15}$/.test(v),
    }, ['username', 'password', 'full_name']);
    const { username, password, full_name: fullName, phone = null } = req.body;
    jwtSecret();
    const passwordHash = await bcrypt.hash(password, bcryptCost);
    const { rows: [user] } = await query(
        "INSERT INTO users (username, password_hash, full_name, phone, role) " +
        "VALUES ($1, $2, $3, $4, 'CUSTOMER') ON CONFLICT DO NOTHING " +
        "RETURNING id, username, full_name, role, NULL::int AS restaurant_id",
        [username, passwordHash, fullName.trim(), phone]
    );
    if (!user) throw new ApiError(409, 'Không thể đăng ký với thông tin đã cung cấp');
    return authResponse(res, 201, user);
}));

router.get('/restaurants', route(async (req, res) => {
    const { lat, lng } = req.query;
    let coordinates = [null, null];
    if (lat !== undefined || lng !== undefined) {
        const validCoordinate = (v, max) => typeof v === 'string' &&
            /^-?\d+(\.\d+)?$/.test(v) && Math.abs(Number(v)) <= max;
        if (!validCoordinate(lat, 90) || !validCoordinate(lng, 180)) {
            throw new ApiError(400, 'Cần cặp tọa độ lat và lng hợp lệ');
        }
        coordinates = [Number(lat), Number(lng)];
    }
    const { rows } = await query(
        'SELECT id, name, address, latitude, longitude, open_time, close_time, brand_name, ' +
        'cover_image_url, rating::float AS rating, is_freeship, prep_time_minutes, ' +
        'CASE WHEN $1::float IS NOT NULL THEN fn_calculate_distance($1, $2, latitude, longitude) END AS distance_km ' +
        'FROM restaurants WHERE is_active IS TRUE ORDER BY distance_km NULLS LAST, id',
        coordinates
    );
    return res.json(rows);
}));

router.get('/restaurants/:id/menu', route(async (req, res) => {
    const id = requireId(req.params.id);
    const { rows } = await query(
        'SELECT m.category_id, m.category_name, m.display_order, m.item_id, m.item_name, ' +
        'm.description, m.price::float AS price, m.stock_quantity, m.image_url, ' +
        'm.is_available, m.calories, m.dietary_tags, m.options_config ' +
        'FROM restaurants r LEFT JOIN v_restaurant_menu_details m ON m.restaurant_id = r.id ' +
        'WHERE r.id = $1 AND r.is_active IS TRUE ORDER BY m.display_order, m.category_id, m.item_id',
        [id]
    );
    if (rows.length === 0) throw new ApiError(404, 'Không tìm thấy quán');
    const categories = new Map();
    for (const { category_id, category_name, display_order, item_id, item_name, ...item } of rows) {
        if (category_id === null) continue;
        if (!categories.has(category_id)) {
            categories.set(category_id, { id: category_id, name: category_name, display_order, items: [] });
        }
        if (item_id !== null) categories.get(category_id).items.push({ id: item_id, name: item_name, ...item });
    }
    return res.json({ restaurant_id: id, categories: [...categories.values()] });
}));

const menuRules = {
    category_id: integer(1),
    name: nonEmpty(150),
    description: nullableText(2000),
    price: money(0.01),
    stock_quantity: integer(0),
    image_url: nullableText(2000),
    is_available: (v) => typeof v === 'boolean',
    calories: integer(0),
    dietary_tags: (v) => Array.isArray(v) && v.length <= 30 && v.every(nonEmpty(50)),
    options_config: (v) => v !== null && typeof v === 'object' && !Array.isArray(v),
};
const menuColumns = 'm.id, m.category_id, m.name, m.description, m.price::float AS price, ' +
    'm.stock_quantity, m.image_url, m.is_available, m.calories, m.dietary_tags, m.options_config';
const menuOwner = 'c.id = m.category_id AND r.id = c.restaurant_id AND r.owner_id = $2';

router.post('/restaurants/:id/menu-items', ...ownerOnly, requireJson, json, route(async (req, res) => {
    const required = ['category_id', 'name', 'price', 'stock_quantity'];
    const { names, params, values } = writeFields(req.body, menuRules, required);
    const { rows: [item] } = await query(
        'INSERT INTO menu_items AS m (' + names.join(', ') + ') SELECT ' + params.join(', ') +
        ' FROM categories c JOIN restaurants r ON r.id = c.restaurant_id ' +
        'WHERE c.id = ' + params[names.indexOf('category_id')] + ' AND r.id = $1 AND r.owner_id = $2 ' +
        'RETURNING ' + menuColumns,
        [requireId(req.params.id), req.user.id, ...values]
    );
    if (!item) throw new ApiError(404, 'Không tìm thấy quán hoặc danh mục');
    return res.status(201).json(item);
}));

router.put('/menu-items/:id', ...ownerOnly, requireJson, json, route(async (req, res) => {
    const { names, params, values } = writeFields(req.body, menuRules);
    const categoryParam = params[names.indexOf('category_id')];
    const categoryCheck = categoryParam ?
        ' AND EXISTS (SELECT 1 FROM categories target WHERE target.id = ' + categoryParam +
        ' AND target.restaurant_id = c.restaurant_id)' : '';
    const { rows: [item] } = await query(
        'UPDATE menu_items m SET ' + names.map((name, index) => name + ' = ' + params[index]).join(', ') +
        ' FROM categories c, restaurants r WHERE m.id = $1 AND ' + menuOwner +
        categoryCheck + ' RETURNING ' + menuColumns,
        [requireId(req.params.id), req.user.id, ...values]
    );
    if (!item) throw new ApiError(404, 'Không tìm thấy món hoặc danh mục');
    return res.json(item);
}));

router.delete('/menu-items/:id', ...ownerOnly, route(async (req, res) => {
    const { rowCount } = await query(
        'DELETE FROM menu_items m USING categories c, restaurants r WHERE m.id = $1 AND ' + menuOwner,
        [requireId(req.params.id), req.user.id]
    );
    if (!rowCount) throw new ApiError(404, 'Không tìm thấy món');
    return res.sendStatus(204);
}));

const voucherRules = {
    code: (v) => typeof v === 'string' && /^[A-Z0-9_-]{3,50}$/.test(v),
    discount_percent: integer(1, 100),
    max_discount_amount: money(0),
    min_order_amount: money(0),
    start_date: date,
    end_date: date,
    usage_limit: integer(1),
    voucher_type: (v) => v === 'DISCOUNT' || v === 'FREESHIP',
    points_cost: integer(0),
};
const voucherColumns = 'v.id, v.restaurant_id, v.code, v.discount_percent, v.max_discount_amount::float AS max_discount_amount, ' +
    'v.min_order_amount::float AS min_order_amount, v.start_date::date::text AS start_date, ' +
    'v.end_date::date::text AS end_date, v.usage_limit, v.used_count, v.voucher_type, v.points_cost';

router.get('/restaurants/:id/vouchers', ...ownerOnly, route(async (req, res) => {
    const { rows: [result] } = await query(
        'SELECT COALESCE(jsonb_agg(to_jsonb(owned) ORDER BY owned.id) FILTER (WHERE owned.id IS NOT NULL), ' +
        "'[]'::jsonb) AS vouchers FROM restaurants r LEFT JOIN LATERAL (SELECT " + voucherColumns +
        ' FROM vouchers v WHERE v.restaurant_id = r.id) owned ON TRUE ' +
        'WHERE r.id = $1 AND r.owner_id = $2 GROUP BY r.id',
        [requireId(req.params.id), req.user.id]
    );
    if (!result) throw new ApiError(404, 'Không tìm thấy quán');
    return res.json(result.vouchers);
}));

router.post('/restaurants/:id/vouchers', ...ownerOnly, requireJson, json, route(async (req, res) => {
    const required = ['code', 'discount_percent', 'max_discount_amount', 'start_date', 'end_date', 'usage_limit'];
    const { names, params, values } = writeFields(req.body, voucherRules, required);
    const { rows: [voucher] } = await query(
        'INSERT INTO vouchers AS v (restaurant_id, ' + names.join(', ') + ') SELECT r.id, ' +
        params.join(', ') + ' FROM restaurants r WHERE r.id = $1 AND r.owner_id = $2 RETURNING ' + voucherColumns,
        [requireId(req.params.id), req.user.id, ...values]
    );
    if (!voucher) throw new ApiError(404, 'Không tìm thấy quán');
    return res.status(201).json(voucher);
}));

router.put('/vouchers/:id', ...ownerOnly, requireJson, json, route(async (req, res) => {
    const { names, params, values } = writeFields(req.body, voucherRules);
    const { rows: [voucher] } = await query(
        'UPDATE vouchers v SET ' + names.map((name, index) => name + ' = ' + params[index]).join(', ') +
        ' FROM restaurants r WHERE v.id = $1 AND r.id = v.restaurant_id AND r.owner_id = $2 ' +
        'RETURNING ' + voucherColumns,
        [requireId(req.params.id), req.user.id, ...values]
    );
    if (!voucher) throw new ApiError(404, 'Không tìm thấy voucher');
    return res.json(voucher);
}));

router.delete('/vouchers/:id', ...ownerOnly, route(async (req, res) => {
    const { rowCount } = await query(
        'DELETE FROM vouchers v USING restaurants r WHERE v.id = $1 AND r.id = v.restaurant_id AND r.owner_id = $2',
        [requireId(req.params.id), req.user.id]
    );
    if (!rowCount) throw new ApiError(404, 'Không tìm thấy voucher');
    return res.sendStatus(204);
}));

router.use((error, req, res, next) => {
    if (res.headersSent) return next(error);
    if (error instanceof ApiError) return res.status(error.status).json({ message: error.message });
    if (error.type === 'entity.parse.failed') return res.status(400).json({ message: 'JSON không hợp lệ' });
    if (error.status === 413) return res.status(413).json({ message: 'Nội dung yêu cầu quá lớn' });
    if (error.status === 415) return res.status(415).json({ message: 'Định dạng nội dung không được hỗ trợ' });
    if (error.code === '23503' || error.code === '23505') {
        return res.status(409).json({ message: 'Dữ liệu bị trùng hoặc đang được đơn hàng sử dụng' });
    }
    if (error.code === '23514' || error.code === '22007' || error.code === '22008') {
        return res.status(400).json({ message: 'Dữ liệu không thỏa mãn ràng buộc' });
    }
    console.error('[TV2 API]', req.method, typeof error.code === 'string' ? error.code : 'INTERNAL');
    return res.status(500).json({ message: 'Lỗi hệ thống' });
});

module.exports = router;
