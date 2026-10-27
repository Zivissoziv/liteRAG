package com.literag.repository;

import com.literag.entity.DocFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocFolderRepository extends JpaRepository<DocFolder, Long> {

    List<DocFolder> findByKbIdOrderBySortOrderAscIdAsc(Long kbId);

    List<DocFolder> findByKbIdAndParentIdOrderBySortOrderAscIdAsc(Long kbId, Long parentId);

    void deleteByKbId(Long kbId);
}
