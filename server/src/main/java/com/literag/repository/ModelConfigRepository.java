package com.literag.repository;

import com.literag.entity.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModelConfigRepository extends JpaRepository<ModelConfig, Long> {

    List<ModelConfig> findByTypeOrderByIsDefaultDescIdAsc(String type);

    List<ModelConfig> findByTypeAndEnabledTrueOrderByIsDefaultDescIdAsc(String type);

    Optional<ModelConfig> findFirstByTypeAndIsDefaultTrueAndEnabledTrue(String type);

    Optional<ModelConfig> findFirstByTypeAndProviderAndEnabledTrue(String type, String provider);

    long countByType(String type);
}
