package com.literag.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.literag.config.AppProperties;
import com.literag.service.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 传输层：
 * POST /mcp          —— Streamable HTTP（单次 JSON-RPC 调用，直接返回 JSON）
 * GET  /mcp/sse      —— 旧版 HTTP+SSE，先推送 endpoint 事件
 * POST /mcp/message  —— 旧版 HTTP+SSE 的消息上行通道
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class McpController {

    private final McpServerService mcp;
    private final ObjectMapper mapper;
    private final SettingsService settings;
    private final AppProperties props;
    private final Map<String, SseEmitter> sseSessions = new ConcurrentHashMap<>();

    // ---------------------------------------------------- streamable http

    @PostMapping(value = "/mcp", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> streamable(@RequestBody JsonNode body, HttpServletRequest request) {
        checkAuth(request);
        try {
            if (body.isArray()) {
                ArrayNode responses = mapper.createArrayNode();
                for (JsonNode item : body) {
                    if (!mcp.isNotification(item)) {
                        responses.add(mcp.handle(item));
                    }
                }
                if (responses.isEmpty()) {
                    return ResponseEntity.accepted().build();
                }
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(responses);
            }
            if (mcp.isNotification(body)) {
                return ResponseEntity.accepted().build();
            }
            JsonNode response = mcp.handle(body);
            String method = body.path("method").asText("");
            ResponseEntity.BodyBuilder builder = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON);
            if ("initialize".equals(method)) {
                builder.header("Mcp-Session-Id", UUID.randomUUID().toString());
            } else if (request.getHeader("Mcp-Session-Id") != null) {
                builder.header("Mcp-Session-Id", request.getHeader("Mcp-Session-Id"));
            }
            return builder.body(response);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("MCP 请求处理失败", e);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("jsonrpc", "2.0", "id", body.get("id"),
                            "error", Map.of("code", -32603, "message", String.valueOf(e.getMessage()))));
        }
    }

    /** 部分客户端会先用 GET 探测，返回 405 提示使用 POST */
    @GetMapping("/mcp")
    public ResponseEntity<Object> probe() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Map.of("error", "请使用 POST 调用 MCP Streamable HTTP 端点", "sse", "/mcp/sse"));
    }

    @DeleteMapping("/mcp")
    public ResponseEntity<Void> closeSession(@RequestHeader(value = "Mcp-Session-Id", required = false) String sessionId) {
        if (sessionId != null) {
            SseEmitter emitter = sseSessions.remove(sessionId);
            if (emitter != null) {
                emitter.complete();
            }
        }
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------- legacy sse

    @GetMapping(value = "/mcp/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sse(HttpServletRequest request) {
        checkAuth(request);
        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(0L);
        sseSessions.put(sessionId, emitter);
        emitter.onCompletion(() -> sseSessions.remove(sessionId));
        emitter.onTimeout(() -> sseSessions.remove(sessionId));
        emitter.onError(e -> sseSessions.remove(sessionId));

        String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        String endpoint = base + "/mcp/message?sessionId=" + sessionId;
        try {
            emitter.send(SseEmitter.event().name("endpoint").data(endpoint));
            log.info("MCP SSE 会话建立: {}", sessionId);
        } catch (Exception e) {
            sseSessions.remove(sessionId);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "无法建立 SSE 连接");
        }
        return emitter;
    }

    @PostMapping(value = "/mcp/message", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> message(@RequestParam String sessionId, @RequestBody JsonNode body) {
        SseEmitter emitter = sseSessions.get(sessionId);
        if (emitter == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "会话不存在或已关闭", "sessionId", sessionId));
        }
        try {
            if (!mcp.isNotification(body)) {
                JsonNode response = mcp.handle(body);
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(mapper.writeValueAsString(response), MediaType.APPLICATION_JSON));
            }
        } catch (Exception e) {
            log.warn("MCP SSE 推送失败: {}", e.getMessage());
            sseSessions.remove(sessionId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", String.valueOf(e.getMessage())));
        }
        return ResponseEntity.accepted().body(Map.of("status", "accepted"));
    }

    private void checkAuth(HttpServletRequest request) {
        String key = settings.get(SettingsService.MCP_API_KEY);
        if (key == null || key.isBlank()) {
            return;
        }
        String provided = request.getHeader("X-API-Key");
        if (provided == null || provided.isBlank()) {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                provided = auth.substring(7);
            }
        }
        if (!key.equals(provided)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "MCP 访问密钥无效");
        }
    }

    /** 供前端查看当前 SSE 会话数 */
    @GetMapping("/api/mcp/sessions")
    public Map<String, Object> sessions() {
        return Map.of("sseSessions", sseSessions.size(),
                "requireApiKey", !settings.get(SettingsService.MCP_API_KEY).isBlank(),
                "serverName", props.getMcp().getServerName(),
                "version", props.getMcp().getServerVersion());
    }
}
