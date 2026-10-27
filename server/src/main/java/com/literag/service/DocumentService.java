package com.literag.service;

import com.literag.common.BizException;
import com.literag.common.PageResult;
import com.literag.dto.DocumentDto;
import com.literag.dto.Requests;
import com.literag.dto.Views;
import com.literag.entity.DocChunk;
import com.literag.entity.DocFolder;
import com.literag.entity.Document;
import com.literag.entity.KnowledgeBase;
import com.literag.repository.DocChunkRepository;
import com.literag.repository.DocumentRepository;
import com.literag.repository.KnowledgeBaseRepository;
import com.literag.parser.ParserRegistry;
import com.literag.vector.VectorStoreManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository repository;
    private final DocChunkRepository chunkRepository;
    private final KnowledgeBaseRepository kbRepository;
    private final KnowledgeBaseService kbService;
    private final FolderService folderService;
    private final IngestService ingestService;
    private final StorageService storage;
    private final ParserRegistry parserRegistry;
    private final VectorStoreManager vectorStoreManager;
    private final KbAccessService accessService;

    // ------------------------------------------------------------------ upload

    /** 注意：此处不使用事务，保证文档保存立即提交后异步线程能读到 */
    public Map<String, Object> upload(Long kbId, MultipartFile[] files, Long folderId, String tags, String strategy) {
        accessService.assertCanView(kbId);
        KnowledgeBase kb = kbRepository.findById(kbId)
                .orElseThrow(() -> new BizException(404, "知识库不存在"));
        Long targetFolder = folderService.requireInKb(kbId, folderId);
        String chunkStrategy = normalizeStrategy(strategy);
        List<DocumentDto> uploaded = new ArrayList<>();
        List<Map<String, String>> rejected = new ArrayList<>();
        if (files == null || files.length == 0) {
            throw new BizException("请选择要上传的文件");
        }
        if (files.length > 20) {
            throw new BizException("单次最多上传 20 个文件");
        }
        for (MultipartFile file : files) {
            String original = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
            String ext = extension(original);
            if (file.isEmpty()) {
                rejected.add(Map.of("name", original, "reason", "文件为空"));
                continue;
            }
            if (!parserRegistry.supported(ext)) {
                rejected.add(Map.of("name", original, "reason", "不支持的类型 ." + ext));
                continue;
            }
            try (InputStream in = file.getInputStream()) {
                String relative = storage.save(kbId, ext, in);
                Document doc = new Document();
                doc.setKbId(kbId);
                doc.setName(original);
                doc.setTitle(stripExtension(original));
                doc.setSourceType(Document.TYPE_UPLOAD);
                doc.setFileExt(ext);
                doc.setFilePath(relative);
                doc.setFileSize(file.getSize());
                doc.setFolderId(targetFolder);
                doc.setTags(tags);
                doc.setChunkStrategy(chunkStrategy);
                doc.setStatus(Document.STATUS_PENDING);
                repository.save(doc);
                ingestService.ingestAsync(doc.getId());
                uploaded.add(toDto(doc, false));
            } catch (Exception e) {
                log.error("上传处理失败 {}", original, e);
                rejected.add(Map.of("name", original, "reason", e.getMessage() == null ? "处理失败" : e.getMessage()));
            }
        }
        kbService.refreshCounts(kbId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uploaded", uploaded);
        result.put("rejected", rejected);
        return result;
    }

    // ------------------------------------------------------------------- query

    public PageResult<DocumentDto> list(Long kbId, String status, String keyword, Long folderId, int page, int size) {
        accessService.assertCanView(kbId);
        PageRequest pageable = PageRequest.of(Math.max(0, page - 1), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        String kw = keyword == null || keyword.isBlank() ? "" : keyword.trim();
        String st = status == null || status.isBlank() ? "" : status;
        boolean allDocs = folderId == null;
        boolean rootOnly = folderId != null && folderId <= 0;
        List<Long> folderIds = List.of(-1L);
        if (!allDocs && !rootOnly) {
            DocFolder folder = folderService.require(folderId);
            if (!folder.getKbId().equals(kbId)) {
                throw new BizException("目录不属于当前知识库");
            }
            folderIds = folderService.subtreeIds(folder);
        }
        Page<Document> result = repository.search(kbId, st, kw, allDocs, rootOnly, folderIds, pageable);
        Map<Long, String> paths = folderService.pathMap(kbId);
        List<DocumentDto> list = result.getContent().stream().map(d -> toDto(d, false, paths)).toList();
        return new PageResult<>(list, result.getTotalElements(), page, size);
    }

    public Document require(Long id) {
        Document doc = repository.findById(id).orElseThrow(() -> new BizException(404, "文档不存在: " + id));
        // 文档继承所属知识库的可见性，避免越权读取原文或分块
        accessService.assertCanView(doc.getKbId());
        return doc;
    }

    /** 供 MCP / 资源接口使用的简要列表 */
    public List<DocumentDto> listBrief(Long kbId, String keyword, int limit) {
        accessService.assertCanView(kbId);
        PageRequest pageable = PageRequest.of(0, Math.min(Math.max(limit, 1), 200),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Map<Long, String> paths = folderService.pathMap(kbId);
        return repository.search(kbId, "", keyword == null || keyword.isBlank() ? "" : keyword.trim(),
                        true, false, List.of(-1L), pageable)
                .getContent().stream().map(d -> toDto(d, false, paths)).toList();
    }

    public DocumentDto detail(Long id, boolean withContent) {
        return toDto(require(id), withContent);
    }

    public PageResult<Views.ChunkView> chunks(Long docId, int page, int size) {
        require(docId);
        PageRequest pageable = PageRequest.of(Math.max(0, page - 1), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.ASC, "seq"));
        Page<DocChunk> chunks = chunkRepository.findByDocIdOrderBySeqAsc(docId, pageable);
        List<Views.ChunkView> list = chunks.getContent().stream().map(c -> Views.ChunkView.builder()
                .id(c.getId())
                .vectorId(c.getVectorId())
                .seq(c.getSeq())
                .charCount(c.getCharCount())
                .dim(c.getDim())
                .content(c.getContent())
                .build()).toList();
        return new PageResult<>(list, chunks.getTotalElements(), page, size);
    }

    // ------------------------------------------------------------------ mutate

    @Transactional
    public void delete(Long id) {
        Document doc = require(id);
        chunkRepository.deleteByDocId(id);
        try {
            KnowledgeBase kb = kbRepository.findById(doc.getKbId()).orElse(null);
            if (kb != null && kb.getCollectionName() != null) {
                vectorStoreManager.active().deleteByDoc(kb.getCollectionName(), id);
            }
        } catch (Exception e) {
            log.warn("删除向量失败 docId={}: {}", id, e.getMessage());
        }
        storage.delete(doc.getFilePath());
        repository.delete(doc);
        kbService.refreshCounts(doc.getKbId());
    }

    public void reindex(Long id) {
        Document doc = require(id);
        doc.setStatus(Document.STATUS_PENDING);
        doc.setErrorMsg(null);
        repository.save(doc);
        ingestService.ingestAsync(id);
    }

    /** 调整文档所属目录；只改归属，不影响已生成的向量 */
    @Transactional
    public DocumentDto move(Long id, Long folderId) {
        Document doc = require(id);
        doc.setFolderId(folderService.requireInKb(doc.getKbId(), folderId));
        repository.save(doc);
        return toDto(doc, false);
    }

    public int reindexKb(Long kbId) {
        accessService.assertCanView(kbId);
        kbRepository.findById(kbId).orElseThrow(() -> new BizException(404, "知识库不存在"));
        List<Document> docs = repository.findByKbIdOrderByCreatedAtDesc(kbId);
        for (Document doc : docs) {
            doc.setStatus(Document.STATUS_PENDING);
            doc.setErrorMsg(null);
            repository.save(doc);
            ingestService.ingestAsync(doc.getId());
        }
        return docs.size();
    }

    public DocumentDto createWiki(Long kbId, Requests.WikiReq req) {
        accessService.assertCanView(kbId);
        kbRepository.findById(kbId).orElseThrow(() -> new BizException(404, "知识库不存在"));
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BizException("标题不能为空");
        }
        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new BizException("内容不能为空");
        }
        Document doc = new Document();
        doc.setKbId(kbId);
        doc.setName(req.getTitle().trim() + ".md");
        doc.setTitle(req.getTitle().trim());
        doc.setSourceType(Document.TYPE_WIKI);
        doc.setFileExt("md");
        doc.setFileSize((long) req.getContent().length());
        doc.setContent(req.getContent());
        doc.setFolderId(folderService.requireInKb(kbId, req.getFolderId()));
        doc.setCategory(req.getCategory());
        doc.setTags(req.getTags());
        doc.setChunkStrategy(normalizeStrategy(req.getChunkStrategy()));
        doc.setCharCount(req.getContent().length());
        doc.setStatus(Document.STATUS_PENDING);
        repository.save(doc);
        ingestService.ingestAsync(doc.getId());
        return toDto(doc, true);
    }

    public DocumentDto updateWiki(Long id, Requests.WikiReq req) {
        Document doc = require(id);
        if (!Document.TYPE_WIKI.equals(doc.getSourceType())) {
            throw new BizException("仅 Wiki 页面支持在线编辑");
        }
        if (req.getTitle() != null && !req.getTitle().isBlank()) {
            doc.setTitle(req.getTitle().trim());
            doc.setName(req.getTitle().trim() + ".md");
        }
        if (req.getContent() != null) {
            doc.setContent(req.getContent());
            doc.setCharCount(req.getContent().length());
            doc.setFileSize((long) req.getContent().length());
        }
        if (req.getCategory() != null) {
            doc.setCategory(req.getCategory());
        }
        if (req.getFolderId() != null) {
            doc.setFolderId(folderService.requireInKb(doc.getKbId(), req.getFolderId()));
        }
        if (req.getTags() != null) {
            doc.setTags(req.getTags());
        }
        doc.setVer(doc.getVer() == null ? 2 : doc.getVer() + 1);
        doc.setStatus(Document.STATUS_PENDING);
        doc.setErrorMsg(null);
        repository.save(doc);
        ingestService.ingestAsync(id);
        return toDto(doc, true);
    }

    // ------------------------------------------------------------------- utils

    public DocumentDto toDto(Document doc, boolean withContent) {
        return toDto(doc, withContent, null);
    }

    /** 策略归一化：空 → AUTO，非法值 → AUTO */
    public static String normalizeStrategy(String strategy) {
        if (strategy == null || strategy.isBlank()) {
            return Document.STRATEGY_AUTO;
        }
        String v = strategy.trim().toUpperCase();
        return switch (v) {
            case Document.STRATEGY_FIXED, Document.STRATEGY_HEADING, Document.STRATEGY_TABLE -> v;
            default -> Document.STRATEGY_AUTO;
        };
    }

    private DocumentDto toDto(Document doc, boolean withContent, Map<Long, String> folderPaths) {
        String kbName = doc.getKbId() == null ? null
                : kbRepository.findById(doc.getKbId()).map(KnowledgeBase::getName).orElse(null);
        String folderPath = null;
        if (doc.getFolderId() != null) {
            folderPath = folderPaths != null ? folderPaths.get(doc.getFolderId())
                    : folderService.pathOf(doc.getFolderId());
        }
        return DocumentDto.builder()
                .id(doc.getId())
                .kbId(doc.getKbId())
                .kbName(kbName)
                .folderId(doc.getFolderId())
                .folderPath(folderPath)
                .name(doc.getName())
                .title(doc.getTitle())
                .sourceType(doc.getSourceType())
                .fileExt(doc.getFileExt())
                .fileSize(doc.getFileSize())
                .category(doc.getCategory())
                .tags(doc.getTags())
                .status(doc.getStatus())
                .chunkStrategy(doc.getChunkStrategy())
                .errorMsg(doc.getErrorMsg())
                .charCount(doc.getCharCount())
                .chunkCount(doc.getChunkCount())
                .ver(doc.getVer())
                .createdBy(doc.getCreatedBy())
                .content(withContent ? doc.getContent() : null)
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    private String extension(String name) {
        int idx = name.lastIndexOf('.');
        return idx < 0 ? "" : name.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private String stripExtension(String name) {
        int idx = name.lastIndexOf('.');
        return idx <= 0 ? name : name.substring(0, idx);
    }
}
