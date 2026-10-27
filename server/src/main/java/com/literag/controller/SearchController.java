package com.literag.controller;

import com.literag.common.ApiResponse;
import com.literag.dto.Requests;
import com.literag.dto.Views;
import com.literag.service.ChatService;
import com.literag.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final ChatService chatService;

    @PostMapping("/search")
    public ApiResponse<Views.SearchResult> search(@RequestBody Requests.SearchReq req) {
        return ApiResponse.ok(searchService.search(req.getKbIds(), req.getQuery(),
                req.getTopK(), req.getMinScore(), req.getDocId()));
    }

    @PostMapping("/chat/ask")
    public ApiResponse<Views.AskResult> ask(@RequestBody Requests.AskReq req) {
        return ApiResponse.ok(chatService.ask(req));
    }
}
