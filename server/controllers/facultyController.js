import crypto from 'crypto';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import Faculty from '../models/Faculty.js';
import { sendPasswordResetEmail } from '../utils/mailer.js';

// Password complexity: min 8 chars, 1 uppercase, 1 lowercase, 1 number
const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/;
const EMAIL_REGEX = /^\S+@\S+\.\S+$/;

/**
 * Register a new Faculty member
 * POST /api/faculty/register
 */
export async function registerFaculty(req, res) {
  try {
    const { name, email, employeeId, department, phone, password, confirmPassword } = req.body;

    // 1. Validate required fields
    if (!name || !email || !employeeId || !department || !phone || !password || !confirmPassword) {
      return res.status(400).json({
        success: false,
        message: 'All fields are required (Full Name, Official Email, Employee ID, Department, Phone, Password, Confirm Password).',
      });
    }

    // 2. Validate email format
    if (!EMAIL_REGEX.test(email.trim())) {
      return res.status(400).json({
        success: false,
        message: 'Please provide a valid official email address.',
      });
    }

    // 3. Validate phone format (10 digits)
    const cleanedPhone = phone.replace(/[\s\-\(\)]/g, '');
    if (!/^\d{10}$/.test(cleanedPhone)) {
      return res.status(400).json({
        success: false,
        message: 'Phone number must be exactly 10 digits.',
      });
    }

    // 4. Validate password complexity
    if (!PASSWORD_REGEX.test(password)) {
      return res.status(400).json({
        success: false,
        message: 'Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, and one number.',
      });
    }

    // 5. Validate password match
    if (password !== confirmPassword) {
      return res.status(400).json({
        success: false,
        message: 'Passwords do not match.',
      });
    }

    // 6. Check for duplicate registration (email or employeeId)
    const existingFaculty = await Faculty.findOne({
      $or: [
        { email: email.trim().toLowerCase() },
        { employeeId: employeeId.trim().toUpperCase() },
      ],
    });

    if (existingFaculty) {
      const isEmail = existingFaculty.email.toLowerCase() === email.trim().toLowerCase();
      return res.status(409).json({
        success: false,
        message: isEmail
          ? 'A faculty account with this email address is already registered.'
          : 'A faculty account with this Employee ID is already registered.',
      });
    }

    // 7. Hash password using bcrypt
    const salt = await bcrypt.genSalt(10);
    const passwordHash = await bcrypt.hash(password, salt);

    // 8. Save user to database
    const newFaculty = await Faculty.create({
      name: name.trim(),
      email: email.trim().toLowerCase(),
      employeeId: employeeId.trim().toUpperCase(),
      department: department.trim(),
      phone: cleanedPhone,
      passwordHash,
    });

    return res.status(201).json({
      success: true,
      message: 'Faculty account registered successfully. Please sign in.',
      data: newFaculty.toJSON(),
    });
  } catch (error) {
    console.error('[Faculty Register Error]:', error);
    return res.status(500).json({
      success: false,
      message: 'An unexpected server error occurred during faculty registration.',
    });
  }
}

/**
 * Login Faculty member
 * POST /api/faculty/login
 */
export async function loginFaculty(req, res) {
  try {
    const { identifier, email, employeeId, password, rememberMe } = req.body;
    const loginIdentifier = (identifier || email || employeeId || '').trim();

    if (!loginIdentifier || !password) {
      return res.status(400).json({
        success: false,
        message: 'Official Email / Employee ID and password are required.',
      });
    }

    // Find by email OR employeeId
    const faculty = await Faculty.findOne({
      $or: [
        { email: loginIdentifier.toLowerCase() },
        { employeeId: loginIdentifier.toUpperCase() },
        { employeeId: loginIdentifier },
      ],
    }).select('+passwordHash');

    if (!faculty) {
      return res.status(401).json({
        success: false,
        message: 'Account not found. Please verify your Email or Employee ID.',
      });
    }

    // Verify password with bcrypt
    const isMatch = await faculty.comparePassword(password);
    if (!isMatch) {
      return res.status(401).json({
        success: false,
        message: 'Invalid credentials. Please verify your password.',
      });
    }

    // Issue JWT with role in payload
    const secret = process.env.JWT_SECRET || 'intellicampus-super-secure-jwt-secret-key-2026';
    const expiresIn = rememberMe ? '30d' : '7d';

    const token = jwt.sign(
      {
        id: faculty._id,
        role: 'faculty',
        email: faculty.email,
        employeeId: faculty.employeeId,
        name: faculty.name,
      },
      secret,
      { expiresIn }
    );

    return res.status(200).json({
      success: true,
      message: 'Sign in to Faculty Portal successful.',
      data: {
        token,
        role: 'faculty',
        user: faculty.toJSON(),
      },
    });
  } catch (error) {
    console.error('[Faculty Login Error]:', error);
    return res.status(500).json({
      success: false,
      message: 'An unexpected server error occurred during faculty login.',
    });
  }
}

/**
 * Request password reset token and send email
 * POST /api/faculty/forgot-password
 */
export async function forgotPasswordFaculty(req, res) {
  try {
    const { email } = req.body;

    if (!email || !EMAIL_REGEX.test(email.trim())) {
      return res.status(400).json({
        success: false,
        message: 'Please provide a valid official email address.',
      });
    }

    const faculty = await Faculty.findOne({ email: email.trim().toLowerCase() });

    // Prevent user enumeration: always return generic message
    if (!faculty) {
      return res.status(200).json({
        success: true,
        message: 'If this email exists, a reset link has been sent.',
      });
    }

    // Generate secure random token
    const rawToken = crypto.randomBytes(32).toString('hex');
    const hashedToken = crypto.createHash('sha256').update(rawToken).digest('hex');

    // Token expires in 15 minutes
    faculty.resetPasswordToken = hashedToken;
    faculty.resetPasswordExpires = new Date(Date.now() + 15 * 60 * 1000);
    await faculty.save();

    const clientUrl = process.env.CLIENT_URL || 'http://localhost:5173';
    const resetUrl = `${clientUrl}/faculty/reset-password/${rawToken}`;

    // Send real email via Nodemailer
    await sendPasswordResetEmail({
      to: faculty.email,
      name: faculty.name,
      role: 'faculty',
      resetUrl,
    });

    return res.status(200).json({
      success: true,
      message: 'If this email exists, a reset link has been sent.',
    });
  } catch (error) {
    console.error('[Faculty Forgot Password Error]:', error);
    return res.status(500).json({
      success: false,
      message: 'Unable to process password reset request at this time.',
    });
  }
}

/**
 * Validate password reset token
 * GET /api/faculty/reset-password/:token
 */
export async function validateResetTokenFaculty(req, res) {
  try {
    const token = req.params.token || req.query.token;

    if (!token) {
      return res.status(400).json({
        success: false,
        message: 'No reset token provided.',
      });
    }

    const hashedToken = crypto.createHash('sha256').update(token).digest('hex');
    const faculty = await Faculty.findOne({
      resetPasswordToken: hashedToken,
      resetPasswordExpires: { $gt: new Date() },
    });

    if (!faculty) {
      return res.status(400).json({
        success: false,
        message: 'Invalid or expired password reset link. Please request a new link.',
      });
    }

    return res.status(200).json({
      success: true,
      message: 'Token is valid and active.',
      data: {
        email: faculty.email,
        role: 'faculty',
      },
    });
  } catch (error) {
    console.error('[Faculty Validate Token Error]:', error);
    return res.status(500).json({
      success: false,
      message: 'Failed to validate reset token.',
    });
  }
}

/**
 * Reset password using token
 * POST /api/faculty/reset-password/:token
 */
export async function resetPasswordFaculty(req, res) {
  try {
    const { token } = req.params;
    const { password, confirmPassword } = req.body;

    if (!token) {
      return res.status(400).json({
        success: false,
        message: 'Missing password reset token.',
      });
    }

    if (!password || !confirmPassword) {
      return res.status(400).json({
        success: false,
        message: 'New password and confirmation password are required.',
      });
    }

    if (!PASSWORD_REGEX.test(password)) {
      return res.status(400).json({
        success: false,
        message: 'Password must be at least 8 characters long with uppercase, lowercase, and numeric characters.',
      });
    }

    if (password !== confirmPassword) {
      return res.status(400).json({
        success: false,
        message: 'Passwords do not match.',
      });
    }

    const hashedToken = crypto.createHash('sha256').update(token).digest('hex');
    const faculty = await Faculty.findOne({
      resetPasswordToken: hashedToken,
      resetPasswordExpires: { $gt: new Date() },
    }).select('+passwordHash');

    if (!faculty) {
      return res.status(400).json({
        success: false,
        message: 'Password reset link is invalid or has expired. Please request a new one.',
      });
    }

    // Hash new password and clear reset tokens
    const salt = await bcrypt.genSalt(10);
    faculty.passwordHash = await bcrypt.hash(password, salt);
    faculty.resetPasswordToken = undefined;
    faculty.resetPasswordExpires = undefined;
    await faculty.save();

    return res.status(200).json({
      success: true,
      message: 'Password has been successfully updated. You may now sign in.',
    });
  } catch (error) {
    console.error('[Faculty Reset Password Error]:', error);
    return res.status(500).json({
      success: false,
      message: 'An error occurred while resetting your password.',
    });
  }
}

/**
 * Get current authenticated Faculty member profile
 * GET /api/faculty/me
 */
export async function getFacultyMe(req, res) {
  try {
    return res.status(200).json({
      success: true,
      data: req.user.toJSON(),
    });
  } catch (error) {
    console.error('[Faculty Me Error]:', error);
    return res.status(500).json({
      success: false,
      message: 'Failed to retrieve profile data.',
    });
  }
}
