package com.literag.controller;

import com.literag.common.ApiResponse;
import com.literag.common.PageResult;
import com.literag.dto.DocumentDto;
import com.literag.dto.Requests;
import com.literag.dto.Views;
import com.literag.entity.Document;
import com.literag.preview.PreviewService;
import com.literag.service.DocumentService;
import com.literag.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final StorageService storageService;
    private final PreviewService previewService;

    @PostMapping("/kb/{kbId}/documents/upload")
    public ApiResponse<Map<String, Object>> upload(@PathVariable Long kbId,
                                                   @RequestParam("files") MultipartFile[] files,
                                                   @RequestParam(value = "strategy", required = false) String strategy,
                                                   @RequestParam(required = false) Long folderId,
                                                   @RequestParam(required = false) String tags) {
        return ApiResponse.ok(documentService.upload(kbId, files, folderId, tags, strategy));
    }

    @GetMapping("/kb/{kbId}/documents")
    public ApiResponse<PageResult<DocumentDto>> list(@PathVariable Long kbId,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Long folderId,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(documentService.list(kbId, status, keyword, folderId, page, size));
    }

    @PostMapping("/documents/{id}/move")
    public ApiResponse<DocumentDto> move(@PathVariable Long id, @RequestBody Requests.MoveReq req) {
        return ApiResponse.ok(documentService.move(id, req.getFolderId()));
    }

    @PostMapping("/kb/{kbId}/wiki")
    public ApiResponse<DocumentDto> createWiki(@PathVariable Long kbId, @RequestBody Requests.WikiReq req) {
        return ApiResponse.ok(documentService.createWiki(kbId, req));
    }

    @GetMapping("/documents/{id}")
    public ApiResponse<DocumentDto> detail(@PathVariable Long id,
                                           @RequestParam(defaultValue = "true") boolean content) {
        return ApiResponse.ok(documentService.detail(id, content));
    }

    @PutMapping("/documents/{id}/wiki")
    public ApiResponse<DocumentDto> updateWiki(@PathVariable Long id, @RequestBody Requests.WikiReq req) {
        return ApiResponse.ok(documentService.updateWiki(id, req));
    }

    @DeleteMapping("/documents/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/documents/{id}/reindex")
    public ApiResponse<Void> reindex(@PathVariable Long id) {
        documentService.reindex(id);
        return ApiResponse.ok();
    }

    @GetMapping("/documents/{id}/chunks")
    public ApiResponse<PageResult<Views.ChunkView>> chunks(@PathVariable Long id,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(documentService.chunks(id, page, size));
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Document doc = documentService.require(id);
        Path path = existingFile(doc);
        if (path == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, inlineName(doc, "attachment"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(path));
    }

    /** 原始文件，按真实类型内联返回，供浏览器原生预览（PDF 等） */
    @GetMapping("/documents/{id}/raw")
    public ResponseEntity<Resource> raw(@PathVariable Long id) {
        Document doc = documentService.require(id);
        Path path = existingFile(doc);
        if (path == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, inlineName(doc, "inline"))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(previewService.mediaType(doc.getFileExt()))
                .body(new FileSystemResource(path));
    }

    /** pptx 版式预览：按幻灯片坐标渲染成 HTML，供 iframe 直接展示 */
    @GetMapping(value = "/documents/{id}/slides", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> slides(@PathVariable Long id) {
        Document doc = documentService.require(id);
        if (existingFile(doc) == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(previewService.slidesHtml(doc));
    }

    private Path existingFile(Document doc) {
        if (doc.getFilePath() == null || doc.getFilePath().isBlank()) {
            return null;
        }
        Path path = storageService.resolve(doc.getFilePath());
        return path.toFile().exists() ? path : null;
    }

    private String inlineName(Document doc, String disposition) {
        String filename = URLEncoder.encode(doc.getName() == null ? "document" : doc.getName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return disposition + "; filename*=UTF-8''" + filename;
    }
}
