package com.literag.controller;

import com.literag.common.ApiResponse;
import com.literag.dto.KnowledgeBaseDto;
import com.literag.dto.Requests;
import com.literag.service.DocumentService;
import com.literag.service.KbAccessService;
import com.literag.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;
    private final DocumentService documentService;
    private final KbAccessService accessService;

    @GetMapping
    public ApiResponse<List<KnowledgeBaseDto>> list() {
        return ApiResponse.ok(kbService.list());
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseDto> create(@Valid @RequestBody Requests.KbReq req) {
        return ApiResponse.ok(kbService.create(req));
    }

    /** 授权可选范围（角色与用户），供编辑弹窗使用；需要写权限，避免只读用户枚举账号 */
    @GetMapping("/access-options")
    public ApiResponse<Map<String, Object>> accessOptions() {
        return ApiResponse.ok(accessService.options());
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBaseDto> detail(@PathVariable Long id) {
        return ApiResponse.ok(kbService.detail(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<KnowledgeBaseDto> update(@PathVariable Long id, @RequestBody Requests.KbReq req) {
        return ApiResponse.ok(kbService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        kbService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/reindex")
    public ApiResponse<Map<String, Object>> reindex(@PathVariable Long id) {
        int count = documentService.reindexKb(id);
        return ApiResponse.ok(Map.of("count", count));
    }
}
