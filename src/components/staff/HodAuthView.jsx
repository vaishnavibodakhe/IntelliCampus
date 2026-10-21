import React from 'react';
import StaffAuthView from './StaffAuthView';

export default function HodAuthView({ onAuthSuccess, noticeMessage = '', onRoleChange }) {
  return (
    <StaffAuthView 
      initialRole="HOD"
      onAuthSuccess={onAuthSuccess}
      noticeMessage={noticeMessage}
      onRoleChange={onRoleChange}
    />
  );
}
