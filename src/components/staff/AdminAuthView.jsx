import React from 'react';
import RoleAuthView from './RoleAuthView';

export default function AdminAuthView({ onAuthSuccess, noticeMessage = '', onSwitchRole }) {
  return (
    <RoleAuthView 
      role="ADMIN"
      onAuthSuccess={onAuthSuccess}
      noticeMessage={noticeMessage}
      onSwitchRole={onSwitchRole}
    />
  );
}
