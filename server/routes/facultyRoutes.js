import express from 'express';
import {
  registerFaculty,
  loginFaculty,
  forgotPasswordFaculty,
  validateResetTokenFaculty,
  resetPasswordFaculty,
  getFacultyMe,
} from '../controllers/facultyController.js';
import { loginLimiter, forgotPasswordLimiter } from '../middleware/rateLimiter.js';
import { protect } from '../middleware/authMiddleware.js';

const router = express.Router();

// Public Authentication Endpoints
router.post('/register', registerFaculty);
router.post('/login', loginLimiter, loginFaculty);
router.post('/forgot-password', forgotPasswordLimiter, forgotPasswordFaculty);
router.get('/reset-password/:token', validateResetTokenFaculty);
router.post('/reset-password/:token', resetPasswordFaculty);

// Protected Faculty Endpoints
router.get('/me', protect('faculty'), getFacultyMe);

export default router;
