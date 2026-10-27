package com.literag.repository;

import com.literag.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    Page<Document> findByKbId(Long kbId, Pageable pageable);

    Page<Document> findByKbIdAndStatus(Long kbId, String status, Pageable pageable);

    @Query("""
            select d from Document d
            where d.kbId = :kbId
              and (:status = '' or d.status = :status)
              and (:allDocs = true
                   or (:rootOnly = true and d.folderId is null)
                   or d.folderId in :folderIds)
              and (:keyword = '' or lower(d.name) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(d.title, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(d.tags, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<Document> search(@Param("kbId") Long kbId,
                          @Param("status") String status,
                          @Param("keyword") String keyword,
                          @Param("allDocs") boolean allDocs,
                          @Param("rootOnly") boolean rootOnly,
                          @Param("folderIds") List<Long> folderIds,
                          Pageable pageable);

    /** 每个目录直接归属的文档数，folderId 为空的行代表根目录文档 */
    @Query("""
            select d.folderId as folderId, count(d) as total
            from Document d where d.kbId = :kbId group by d.folderId
            """)
    List<FolderCount> countByFolder(@Param("kbId") Long kbId);

    List<Document> findByFolderIdIn(List<Long> folderIds);

    long countByKbIdAndFolderIdIsNull(Long kbId);

    long countByKbIdIn(List<Long> kbIds);

    long countByKbIdInAndStatus(List<Long> kbIds, String status);

    List<Document> findTop10ByKbIdInOrderByCreatedAtDesc(List<Long> kbIds);

    interface FolderCount {
        Long getFolderId();

        Long getTotal();
    }

    List<Document> findByKbIdOrderByCreatedAtDesc(Long kbId);

    List<Document> findTop10ByOrderByCreatedAtDesc();

    long countByKbId(Long kbId);

    long countByStatus(String status);

    void deleteByKbId(Long kbId);
}
