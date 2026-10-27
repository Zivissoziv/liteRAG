package com.literag.repository;

import com.literag.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    List<KnowledgeBase> findAllByOrderByUpdatedAtDesc();

    boolean existsByName(String name);

    long countByEmbeddingModelId(Long embeddingModelId);

    List<KnowledgeBase> findByMcpExposedTrueOrderByUpdatedAtDesc();
}
