import express from 'express';
import {
  registerMentor,
  loginMentor,
  forgotPasswordMentor,
  validateResetTokenMentor,
  resetPasswordMentor,
  getMentorMe,
} from '../controllers/mentorController.js';
import { loginLimiter, forgotPasswordLimiter } from '../middleware/rateLimiter.js';
import { protect } from '../middleware/authMiddleware.js';

const router = express.Router();

// Public Authentication Endpoints
router.post('/register', registerMentor);
router.post('/login', loginLimiter, loginMentor);
router.post('/forgot-password', forgotPasswordLimiter, forgotPasswordMentor);
router.get('/reset-password/:token', validateResetTokenMentor);
router.post('/reset-password/:token', resetPasswordMentor);

// Protected Mentor Endpoints
router.get('/me', protect('mentor'), getMentorMe);

export default router;
