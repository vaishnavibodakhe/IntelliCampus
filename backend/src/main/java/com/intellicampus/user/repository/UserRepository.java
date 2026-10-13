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
    List<User> findByRole(Role role);
    boolean existsByUserCode(String userCode);
    boolean existsByEmail(String email);
}
