package com.literag.repository;

import com.literag.entity.DocChunk;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DocChunkRepository extends JpaRepository<DocChunk, Long> {

    List<DocChunk> findByDocIdOrderBySeqAsc(Long docId);

    Page<DocChunk> findByDocIdOrderBySeqAsc(Long docId, Pageable pageable);

    List<DocChunk> findByVectorIdIn(List<String> vectorIds);

    @Transactional
    void deleteByDocId(Long docId);

    @Transactional
    void deleteByKbId(Long kbId);

    @Transactional
    void deleteByVectorIdIn(List<String> vectorIds);

    long countByKbId(Long kbId);

    long countByKbIdIn(List<Long> kbIds);

    long countByDocId(Long docId);

    @Query("select c from DocChunk c where c.kbId in :kbIds and c.embedding is not null")
    List<DocChunk> findIndexedByKbIds(@Param("kbIds") List<Long> kbIds);
}
