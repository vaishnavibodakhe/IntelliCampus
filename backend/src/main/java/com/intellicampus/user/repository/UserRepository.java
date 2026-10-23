package com.intellicampus.user.repository;

import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUserCode(String userCode);
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    List<User> findByRole(Role role);
    List<User> findByStatusAndCreatedAtBefore(com.intellicampus.user.model.UserStatus status, java.time.LocalDateTime cutoff);
    boolean existsByRole(Role role);
    long countByRole(Role role);
    boolean existsByUserCode(String userCode);
    boolean existsByUserCodeAndRole(String userCode, Role role);
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<User> findByRoleIn(List<Role> roles);
    List<User> findByDepartmentAndRoleAndStatus(com.intellicampus.academic.model.Department department, Role role, com.intellicampus.user.model.UserStatus status);
    boolean existsByRoleAndStatus(Role role, com.intellicampus.user.model.UserStatus status);
    Optional<User> findFirstByRole(Role role);
    Optional<User> findFirstByRoleAndStatus(Role role, com.intellicampus.user.model.UserStatus status);
}
