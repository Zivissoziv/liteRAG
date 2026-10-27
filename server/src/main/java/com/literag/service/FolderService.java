package com.literag.service;

import com.literag.common.BizException;
import com.literag.dto.FolderDto;
import com.literag.dto.Requests;
import com.literag.entity.DocFolder;
import com.literag.entity.Document;
import com.literag.repository.DocFolderRepository;
import com.literag.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识库目录：多级树维护、路径解析与文档归属。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FolderService {

    /** 目录层级上限，避免过深难以浏览 */
    private static final int MAX_DEPTH = 6;

    private final DocFolderRepository repository;
    private final DocumentRepository documentRepository;
    private final KbAccessService accessService;

    // -------------------------------------------------------------------- query

    public List<FolderDto> tree(Long kbId) {
        accessService.assertCanView(kbId);
        List<DocFolder> all = repository.findByKbIdOrderBySortOrderAscIdAsc(kbId);
        Map<Long, Integer> counts = directCounts(kbId);
        Map<Long, FolderDto> nodes = new LinkedHashMap<>();
        for (DocFolder f : all) {
            nodes.put(f.getId(), FolderDto.builder()
                    .id(f.getId())
                    .kbId(f.getKbId())
                    .parentId(f.getParentId())
                    .name(f.getName())
                    .docCount(counts.getOrDefault(f.getId(), 0))
                    .totalCount(0)
                    .createdAt(f.getCreatedAt())
                    .children(new ArrayList<>())
                    .build());
        }
        List<FolderDto> roots = new ArrayList<>();
        for (DocFolder f : all) {
            FolderDto node = nodes.get(f.getId());
            FolderDto parent = f.getParentId() == null ? null : nodes.get(f.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        for (FolderDto root : roots) {
            rollup(root, "", 1);
        }
        return roots;
    }

    /** 递归补全路径、层级与含子目录的文档总数 */
    private int rollup(FolderDto node, String parentPath, int depth) {
        node.setDepth(depth);
        node.setPath(parentPath.isEmpty() ? node.getName() : parentPath + " / " + node.getName());
        int total = node.getDocCount() == null ? 0 : node.getDocCount();
        for (FolderDto child : node.getChildren()) {
            total += rollup(child, node.getPath(), depth + 1);
        }
        node.setTotalCount(total);
        return total;
    }

    public DocFolder require(Long id) {
        return repository.findById(id).orElseThrow(() -> new BizException(404, "目录不存在: " + id));
    }

    public Long requireInKb(Long kbId, Long folderId) {
        if (folderId == null || folderId <= 0) {
            return null;
        }
        DocFolder folder = require(folderId);
        if (!folder.getKbId().equals(kbId)) {
            throw new BizException("目录不属于当前知识库");
        }
        return folder.getId();
    }

    /** 目录 id 到完整路径的映射，供文档列表一次性带出所属目录 */
    public Map<Long, String> pathMap(Long kbId) {
        if (kbId == null) {
            return Map.of();
        }
        List<DocFolder> all = repository.findByKbIdOrderBySortOrderAscIdAsc(kbId);
        Map<Long, DocFolder> byId = new HashMap<>();
        all.forEach(f -> byId.put(f.getId(), f));
        Map<Long, String> paths = new HashMap<>();
        for (DocFolder f : all) {
            paths.put(f.getId(), buildPath(f, byId));
        }
        return paths;
    }

    public String pathOf(Long folderId) {
        if (folderId == null) {
            return null;
        }
        DocFolder folder = repository.findById(folderId).orElse(null);
        if (folder == null) {
            return null;
        }
        return pathMap(folder.getKbId()).get(folderId);
    }

    private String buildPath(DocFolder folder, Map<Long, DocFolder> byId) {
        Deque<String> names = new ArrayDeque<>();
        DocFolder cur = folder;
        int guard = 0;
        while (cur != null && guard++ <= MAX_DEPTH + 2) {
            names.addFirst(cur.getName());
            cur = cur.getParentId() == null ? null : byId.get(cur.getParentId());
        }
        return String.join(" / ", names);
    }

    /** 当前目录及全部子目录的 id，用于按目录（含子目录）筛选文档 */
    public List<Long> subtreeIds(DocFolder root) {
        List<DocFolder> all = repository.findByKbIdOrderBySortOrderAscIdAsc(root.getKbId());
        Set<Long> ids = new HashSet<>();
        ids.add(root.getId());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (DocFolder f : all) {
                if (f.getParentId() != null && ids.contains(f.getParentId()) && ids.add(f.getId())) {
                    changed = true;
                }
            }
        }
        return new ArrayList<>(ids);
    }

    public Map<Long, Integer> directCounts(Long kbId) {
        Map<Long, Integer> counts = new HashMap<>();
        for (DocumentRepository.FolderCount row : documentRepository.countByFolder(kbId)) {
            if (row.getFolderId() != null) {
                counts.put(row.getFolderId(), row.getTotal() == null ? 0 : row.getTotal().intValue());
            }
        }
        return counts;
    }

    public long rootDocCount(Long kbId) {
        return documentRepository.countByKbIdAndFolderIdIsNull(kbId);
    }

    // ------------------------------------------------------------------- mutate

    @Transactional
    public FolderDto create(Long kbId, Requests.FolderReq req) {
        accessService.assertCanView(kbId);
        String name = normalizeName(req.getName());
        Long parentId = requireInKb(kbId, req.getParentId());
        if (parentId != null) {
            ensureDepth(kbId, parentId);
        }
        ensureUnique(kbId, parentId, name, null);
        DocFolder folder = new DocFolder();
        folder.setKbId(kbId);
        folder.setParentId(parentId);
        folder.setName(name);
        folder.setSortOrder(nextSort(kbId, parentId));
        repository.save(folder);
        return toDto(folder);
    }

    @Transactional
    public FolderDto update(Long id, Requests.FolderReq req) {
        DocFolder folder = require(id);
        accessService.assertCanView(folder.getKbId());
        if (req.getName() != null && !req.getName().isBlank()) {
            String name = normalizeName(req.getName());
            ensureUnique(folder.getKbId(), folder.getParentId(), name, folder.getId());
            folder.setName(name);
        }
        if (req.getParentId() != null) {
            Long parentId = requireInKb(folder.getKbId(), req.getParentId());
            if (parentId != null) {
                if (parentId.equals(folder.getId()) || subtreeIds(folder).contains(parentId)) {
                    throw new BizException("不能把目录移动到自身或子目录下");
                }
                ensureDepth(folder.getKbId(), parentId);
            }
            folder.setParentId(parentId);
        }
        repository.save(folder);
        return toDto(folder);
    }

    /** 删除目录及其子目录，其中的文档上移到被删目录的上一级，不丢数据 */
    @Transactional
    public int delete(Long id) {
        DocFolder folder = require(id);
        accessService.assertCanView(folder.getKbId());
        List<Long> ids = subtreeIds(folder);
        Long target = folder.getParentId();
        List<Document> docs = documentRepository.findByFolderIdIn(ids);
        for (Document doc : docs) {
            doc.setFolderId(target);
        }
        if (!docs.isEmpty()) {
            documentRepository.saveAll(docs);
        }
        repository.deleteAllById(ids);
        return docs.size();
    }

    public FolderDto toDto(DocFolder folder) {
        return FolderDto.builder()
                .id(folder.getId())
                .kbId(folder.getKbId())
                .parentId(folder.getParentId())
                .name(folder.getName())
                .path(pathOf(folder.getId()))
                .docCount(directCounts(folder.getKbId()).getOrDefault(folder.getId(), 0))
                .totalCount(0)
                .createdAt(folder.getCreatedAt())
                .children(new ArrayList<>())
                .build();
    }

    // -------------------------------------------------------------------- utils

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new BizException("目录名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 60) {
            throw new BizException("目录名称不能超过 60 个字符");
        }
        if (trimmed.contains("/")) {
            throw new BizException("目录名称不能包含斜杠");
        }
        return trimmed;
    }

    private void ensureUnique(Long kbId, Long parentId, String name, Long excludeId) {
        boolean exists = repository.findByKbIdOrderBySortOrderAscIdAsc(kbId).stream()
                .anyMatch(f -> !f.getId().equals(excludeId)
                        && name.equalsIgnoreCase(f.getName())
                        && (parentId == null ? f.getParentId() == null : parentId.equals(f.getParentId())));
        if (exists) {
            throw new BizException("同级目录下已存在同名目录: " + name);
        }
    }

    private void ensureDepth(Long kbId, Long parentId) {
        int depth = 0;
        Map<Long, DocFolder> byId = new HashMap<>();
        repository.findByKbIdOrderBySortOrderAscIdAsc(kbId).forEach(f -> byId.put(f.getId(), f));
        DocFolder cur = byId.get(parentId);
        while (cur != null && depth <= MAX_DEPTH) {
            depth++;
            cur = cur.getParentId() == null ? null : byId.get(cur.getParentId());
        }
        if (depth >= MAX_DEPTH) {
            throw new BizException("目录层级最多 " + MAX_DEPTH + " 级");
        }
    }

    private int nextSort(Long kbId, Long parentId) {
        int max = 0;
        for (DocFolder f : repository.findByKbIdAndParentIdOrderBySortOrderAscIdAsc(kbId, parentId)) {
            if (f.getSortOrder() != null && f.getSortOrder() > max) {
                max = f.getSortOrder();
            }
        }
        return max + 1;
    }
}
