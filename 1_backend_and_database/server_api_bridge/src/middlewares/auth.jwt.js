'use strict';

const jwt = require('jsonwebtoken');

const roles = new Set(['CUSTOMER', 'RESTAURANT', 'SHIPPER']);
const tokenOptions = { issuer: 'smartfood-api', audience: 'smartfood-mobile' };

function jwtSecret() {
    const secret = process.env.JWT_SECRET;
    if (!secret || Buffer.byteLength(secret) < 32) {
        throw new Error('JWT_SECRET phải có ít nhất 32 byte');
    }
    return secret;
}

function signAccessToken(user) {
    return jwt.sign(
        { role: user.role, restaurant_id: user.restaurant_id },
        jwtSecret(),
        { ...tokenOptions, algorithm: 'HS256', subject: String(user.id), expiresIn: '1h' }
    );
}

function authenticateToken(req, res, next) {
    const match = /^Bearer ([^\s]+)$/i.exec(req.get('authorization') || '');
    if (!match) return res.status(401).json({ message: 'Cần Bearer token hợp lệ' });

    let secret;
    try {
        secret = jwtSecret();
    } catch (error) {
        return next(error);
    }

    let claims;
    try {
        claims = jwt.verify(match[1], secret, { ...tokenOptions, algorithms: ['HS256'] });
    } catch {
        return res.status(401).json({ message: 'Token không hợp lệ hoặc đã hết hạn' });
    }

    const id = Number(claims.sub);
    if (!Number.isInteger(id) || id < 1 || id > 2147483647 || !roles.has(claims.role)) {
        return res.status(401).json({ message: 'Token không hợp lệ' });
    }

    req.user = { id, role: claims.role, restaurant_id: claims.restaurant_id };
    return next();
}

function authorizeRoles(...allowedRoles) {
    return (req, res, next) => {
        if (!req.user) return res.status(401).json({ message: 'Cần đăng nhập' });
        if (!allowedRoles.includes(req.user.role)) {
            return res.status(403).json({ message: 'Tài khoản không có quyền thực hiện' });
        }
        return next();
    };
}

module.exports = { authenticateToken, authorizeRoles, signAccessToken, jwtSecret };
