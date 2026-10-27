package com.literag.repository;

import com.literag.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuthTokenRepository extends JpaRepository<AuthToken, String> {

    @Modifying
    @Query("delete from AuthToken t where t.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("delete from AuthToken t where t.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
