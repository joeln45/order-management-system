package com.joel.ordermanagement.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revoke every refresh token belonging to a user (e.g. on password change). */
    @Modifying
    @Transactional
    @Query("delete from RefreshToken rt where rt.user.id = :userId")
    void deleteAllByUserId(String userId);
}
