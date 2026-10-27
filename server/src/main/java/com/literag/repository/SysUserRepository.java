package com.literag.repository;

import com.literag.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRoleAndEnabledTrue(String role);

    List<SysUser> findAllByOrderByIdAsc();
}
