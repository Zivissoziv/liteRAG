package com.literag.controller;

import com.literag.common.ApiResponse;
import com.literag.dto.FolderDto;
import com.literag.dto.Requests;
import com.literag.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    /** 目录树，含每级目录的文档数 */
    @GetMapping("/kb/{kbId}/folders")
    public ApiResponse<List<FolderDto>> tree(@PathVariable Long kbId) {
        return ApiResponse.ok(folderService.tree(kbId));
    }

    @PostMapping("/kb/{kbId}/folders")
    public ApiResponse<FolderDto> create(@PathVariable Long kbId, @RequestBody Requests.FolderReq req) {
        return ApiResponse.ok(folderService.create(kbId, req));
    }

    @PutMapping("/folders/{id}")
    public ApiResponse<FolderDto> update(@PathVariable Long id, @RequestBody Requests.FolderReq req) {
        return ApiResponse.ok(folderService.update(id, req));
    }

    @DeleteMapping("/folders/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id) {
        int moved = folderService.delete(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("movedDocs", moved);
        return ApiResponse.ok(result);
    }
}
