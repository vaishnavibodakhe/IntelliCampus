import React from 'react';
import RoleAuthView from './RoleAuthView';

export default function HodAuthView({ onAuthSuccess, noticeMessage = '', onSwitchRole }) {
  return (
    <RoleAuthView 
      role="HOD"
      onAuthSuccess={onAuthSuccess}
      noticeMessage={noticeMessage}
      onSwitchRole={onSwitchRole}
    />
  );
}
