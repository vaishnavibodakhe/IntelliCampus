import jwt from 'jsonwebtoken';
import Faculty from '../models/Faculty.js';
import Mentor from '../models/Mentor.js';

export function protect(requiredRole) {
  return async (req, res, next) => {
    let token = null;

    if (req.headers.authorization && req.headers.authorization.startsWith('Bearer ')) {
      token = req.headers.authorization.split(' ')[1];
    }

    if (!token) {
      return res.status(401).json({
        success: false,
        message: 'Authentication required. No token provided.',
      });
    }

    try {
      const secret = process.env.JWT_SECRET || 'intellicampus-super-secure-jwt-secret-key-2026';
      const decoded = jwt.verify(token, secret);

      // Verify role if required
      if (requiredRole) {
        const decodedRole = (decoded.role || '').toLowerCase();
        const expectedRole = requiredRole.toLowerCase();

        if (decodedRole !== expectedRole) {
          return res.status(403).json({
            success: false,
            message: `Forbidden: Access requires ${requiredRole.toUpperCase()} credentials. Your role is ${decoded.role?.toUpperCase() || 'UNKNOWN'}.`,
          });
        }
      }

      // Look up user in the correct database collection
      let user = null;
      if (decoded.role?.toLowerCase() === 'faculty') {
        user = await Faculty.findById(decoded.id);
      } else if (decoded.role?.toLowerCase() === 'mentor') {
        user = await Mentor.findById(decoded.id);
      }

      if (!user) {
        return res.status(401).json({
          success: false,
          message: 'Account associated with this token no longer exists.',
        });
      }

      req.user = user;
      req.tokenRole = decoded.role?.toLowerCase();
      next();
    } catch (err) {
      if (err.name === 'TokenExpiredError') {
        return res.status(401).json({
          success: false,
          message: 'Session expired. Please sign in again.',
        });
      }
      return res.status(401).json({
        success: false,
        message: 'Invalid authorization token.',
      });
    }
  };
}
