import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import dotenv from 'dotenv';
import { connectDB } from './config/db.js';
import facultyRoutes from './routes/facultyRoutes.js';
import mentorRoutes from './routes/mentorRoutes.js';

// Load environment configuration
dotenv.config();

const app = express();
const PORT = process.env.PORT || 5000;
const CLIENT_URL = process.env.CLIENT_URL || 'http://localhost:5173';

// Connect to Database
connectDB().catch((err) => {
  console.error('[Fatal DB Init Error]:', err.message);
});

// Security & Header Middlewares
app.use(helmet());

// CORS configuration (allow client origin)
app.use(
  cors({
    origin: [CLIENT_URL, 'http://localhost:5173', 'http://localhost:3000'],
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization'],
  })
);

// Body Parsers
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// Health Check Endpoint
app.get('/api/health', (req, res) => {
  res.status(200).json({
    success: true,
    message: 'IntelliCampus Backend API is operational',
    timestamp: new Date().toISOString(),
  });
});

// API Routes
app.use('/api/faculty', facultyRoutes);
app.use('/api/mentor', mentorRoutes);

// Compatibility aliases for /api/auth/faculty/* and /api/auth/mentor/*
app.use('/api/auth/faculty', facultyRoutes);
app.use('/api/auth/mentor', mentorRoutes);

// Logout endpoint for compatibility
app.post('/api/auth/logout', (req, res) => {
  res.status(200).json({
    success: true,
    message: 'Session terminated. Token removed.',
  });
});

// 404 Route Handler
app.use((req, res) => {
  res.status(404).json({
    success: false,
    message: `API endpoint '${req.method} ${req.originalUrl}' was not found.`,
  });
});

// Centralised Error Handling Middleware
app.use((err, req, res, next) => {
  console.error('[Server Error]:', err);

  const statusCode = err.statusCode || 500;
  const message = err.message || 'An unexpected internal server error occurred.';

  res.status(statusCode).json({
    success: false,
    message,
    ...(process.env.NODE_ENV === 'development' ? { stack: err.stack } : {}),
  });
});

// Start Server
app.listen(PORT, () => {
  console.log(`\n=============================================================`);
  console.log(`🚀 IntelliCampus Server listening on port ${PORT}`);
  console.log(`🔗 Health Check: http://localhost:${PORT}/api/health`);
  console.log(`🌐 Allowed Client: ${CLIENT_URL}`);
  console.log(`=============================================================\n`);
});

export default app;
