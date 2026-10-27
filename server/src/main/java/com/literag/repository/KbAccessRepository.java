package com.literag.repository;

import com.literag.entity.KbAccess;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KbAccessRepository extends JpaRepository<KbAccess, Long> {

    List<KbAccess> findByKbId(Long kbId);

    void deleteByKbId(Long kbId);
}
