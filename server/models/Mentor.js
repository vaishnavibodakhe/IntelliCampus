import mongoose from 'mongoose';
import bcrypt from 'bcryptjs';

const mentorSchema = new mongoose.Schema({
  name: {
    type: String,
    required: [true, 'Full name is required'],
    trim: true,
  },
  email: {
    type: String,
    required: [true, 'Official email is required'],
    unique: true,
    lowercase: true,
    trim: true,
    match: [/^\S+@\S+\.\S+$/, 'Please enter a valid email address'],
  },
  employeeId: {
    type: String,
    required: [true, 'Employee ID is required'],
    unique: true,
    trim: true,
    uppercase: true,
  },
  department: {
    type: String,
    required: [true, 'Department is required'],
    trim: true,
  },
  phone: {
    type: String,
    required: [true, 'Phone number is required'],
    trim: true,
  },
  specialization: {
    type: String,
    required: [true, 'Area of Expertise/Specialization is required'],
    trim: true,
  },
  passwordHash: {
    type: String,
    required: [true, 'Password hash is required'],
    select: false, // Do not return by default in queries
  },
  resetPasswordToken: {
    type: String,
    select: false,
  },
  resetPasswordExpires: {
    type: Date,
    select: false,
  },
  createdAt: {
    type: Date,
    default: Date.now,
  },
});

// Compare password helper
mentorSchema.methods.comparePassword = async function (enteredPassword) {
  return await bcrypt.compare(enteredPassword, this.passwordHash);
};

// Ensure sensitive fields are never in JSON serialization
mentorSchema.methods.toJSON = function () {
  const obj = this.toObject();
  delete obj.passwordHash;
  delete obj.resetPasswordToken;
  delete obj.resetPasswordExpires;
  delete obj.__v;
  obj.role = 'mentor';
  return obj;
};

const Mentor = mongoose.model('Mentor', mentorSchema);
export default Mentor;
