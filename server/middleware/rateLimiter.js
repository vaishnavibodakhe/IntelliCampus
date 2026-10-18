import rateLimit from 'express-rate-limit';

/**
 * Rate limiter for sensitive authentication endpoints (Login)
 * Allows max 10 requests per 15 minutes per IP
 */
export const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 15,
  standardHeaders: true,
  legacyHeaders: false,
  message: {
    success: false,
    message: 'Too many login attempts from this IP address. Please wait 15 minutes before trying again.',
  },
});

/**
 * Rate limiter for password reset requests to prevent email spamming
 * Allows max 5 requests per 15 minutes per IP
 */
export const forgotPasswordLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 5,
  standardHeaders: true,
  legacyHeaders: false,
  message: {
    success: false,
    message: 'Too many password reset requests from this IP address. Please wait 15 minutes.',
  },
});
