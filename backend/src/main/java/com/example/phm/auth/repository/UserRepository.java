package com.example.phm.auth.repository;

import java.util.List;
import java.util.Optional;

import com.example.phm.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByLoginId(String loginId);

<<<<<<< HEAD
=======
    Optional<User> findByUserNameAndEmail(String userName, String email);

    boolean existsByLoginId(String loginId);

>>>>>>> feature/develop_before
    List<User> findByRoleName(String roleName);

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = CURRENT_TIMESTAMP WHERE u.userId = :userId")
    void updateLastLoginAt(@Param("userId") String userId);

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.roleName = :roleName WHERE u.userId = :userId")
    void updateRole(@Param("userId") String userId, @Param("roleName") String roleName);
<<<<<<< HEAD
=======

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.passwordHash = :passwordHash WHERE u.loginId = :loginId")
    void updatePassword(@Param("loginId") String loginId, @Param("passwordHash") String passwordHash);
>>>>>>> feature/develop_before
}
