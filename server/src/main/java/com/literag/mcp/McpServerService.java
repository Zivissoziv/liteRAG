package com.literag.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.literag.config.AppProperties;
import com.literag.dto.KnowledgeBaseDto;
import com.literag.dto.Requests;
import com.literag.dto.Views;
import com.literag.service.ChatService;
import com.literag.service.DocumentService;
import com.literag.service.KnowledgeBaseService;
import com.literag.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 原生 MCP（Model Context Protocol）服务端实现，不依赖任何第三方 MCP SDK。
 * 支持 2024-11-05 / 2025-03-26 / 2025-06-18 三个协议版本，
 * 传输层同时兼容 Streamable HTTP 与旧版 HTTP+SSE。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerService {

    private static final List<String> SUPPORTED_PROTOCOLS =
            List.of("2025-06-18", "2025-03-26", "2024-11-05");

    private final ObjectMapper mapper;
    private final SearchService searchService;
    private final ChatService chatService;
    private final KnowledgeBaseService kbService;
    private final DocumentService documentService;
    private final AppProperties props;

    public boolean isNotification(JsonNode request) {
        JsonNode id = request.get("id");
        return id == null || id.isNull() || id.isMissingNode();
    }

    public JsonNode handle(JsonNode request) {
        String method = request.path("method").asText("");
        JsonNode id = request.get("id");
        log.debug("MCP 请求: {}", method);

        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id != null && !id.isNull()) {
            response.set("id", id);
        }
        try {
            JsonNode result = dispatch(method, request.path("params"));
            response.set("result", result == null ? mapper.createObjectNode() : result);
        } catch (McpException e) {
            response.set("error", errorNode(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            log.error("MCP 处理失败: {}", method, e);
            response.set("error", errorNode(-32603, e.getMessage() == null ? "内部错误" : e.getMessage()));
        }
        return response;
    }

    private JsonNode dispatch(String method, JsonNode params) {
        return switch (method) {
            case "initialize" -> initialize(params);
            case "ping" -> mapper.createObjectNode();
            case "tools/list" -> toolsList();
            case "tools/call" -> callTool(params);
            case "resources/list" -> resourcesList();
            case "resources/templates/list" -> resourceTemplates();
            case "resources/read" -> readResource(params);
            case "prompts/list" -> {
                ObjectNode result = mapper.createObjectNode();
                result.set("prompts", mapper.createArrayNode());
                yield result;
            }
            case "completion/complete" -> mapper.createObjectNode();
            case "notifications/initialized", "notifications/cancelled" -> mapper.createObjectNode();
            default -> throw new McpException(-32601, "不支持的方法: " + method);
        };
    }

    // ------------------------------------------------------------- handshake

    private JsonNode initialize(JsonNode params) {
        String requested = params.path("protocolVersion").asText("2025-06-18");
        String negotiated = SUPPORTED_PROTOCOLS.contains(requested) ? requested : SUPPORTED_PROTOCOLS.get(0);

        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", negotiated);

        ObjectNode capabilities = result.putObject("capabilities");
        ObjectNode tools = capabilities.putObject("tools");
        tools.put("listChanged", false);
        ObjectNode resources = capabilities.putObject("resources");
        resources.put("subscribe", false);
        resources.put("listChanged", false);
        capabilities.putObject("prompts").put("listChanged", false);

        ObjectNode info = result.putObject("serverInfo");
        info.put("name", props.getMcp().getServerName());
        info.put("version", props.getMcp().getServerVersion());
        info.put("title", "LiteRAG 知识库");
        result.put("instructions", """
                这是 LiteRAG 的向量知识库服务。使用建议：
                1. 先调用 list_knowledge_bases 了解有哪些知识库及其 id；
                2. 用 search_knowledge 做语义检索，参数 query 为自然语言问题，可选 kb_ids 限定范围；
                3. 需要基于资料生成答案时调用 ask_knowledge；
                4. 需要原文细节时调用 get_document 获取文档全文。
                检索结果会返回相似度得分，得分低于 0.3 通常意味着召回质量较差，建议换一种问法。
                """);
        return result;
    }

    // ----------------------------------------------------------------- tools

    private JsonNode toolsList() {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode tools = result.putArray("tools");
        tools.add(tool("search_knowledge", "在 LiteRAG 知识库中做语义检索，返回最相关的文本片段、来源文档与相似度得分。",
                """
                        {"type":"object","properties":{
                          "query":{"type":"string","description":"检索内容，建议使用完整的自然语言问句"},
                          "kb_ids":{"type":"array","items":{"type":"integer"},"description":"限定检索的知识库 id 列表，省略则检索全部"},
                          "top_k":{"type":"integer","description":"返回片段数量，默认 6，最大 50"},
                          "min_score":{"type":"number","description":"相似度阈值 0~1，低于该值的片段会被丢弃，默认 0"}
                        },"required":["query"]}"""));
        tools.add(tool("ask_knowledge", "基于知识库的检索增强问答：先检索相关片段，再交给大模型归纳出带引用的答案。",
                """
                        {"type":"object","properties":{
                          "question":{"type":"string","description":"要提问的问题"},
                          "kb_ids":{"type":"array","items":{"type":"integer"},"description":"限定知识库 id 列表"},
                          "top_k":{"type":"integer","description":"参与回答的片段数量，默认 6"}
                        },"required":["question"]}"""));
        tools.add(tool("list_knowledge_bases", "列出全部知识库及其文档数、片段数与所用 Embedding 模型。",
                """
                        {"type":"object","properties":{}}"""));
        tools.add(tool("list_documents", "列出指定知识库下的文档清单，可按目录或关键字过滤。",
                """
                        {"type":"object","properties":{
                          "kb_id":{"type":"integer","description":"知识库 id"},
                          "keyword":{"type":"string","description":"按文件名/标签模糊过滤"},
                          "folder":{"type":"string","description":"按目录名或路径模糊过滤，例如 手册 或 手册 / 接口"},
                          "limit":{"type":"integer","description":"最多返回条数，默认 50"}
                        },"required":["kb_id"]}"""));
        tools.add(tool("get_document", "获取文档的解析全文，可按字符区间截取，用于引用原文细节。",
                """
                        {"type":"object","properties":{
                          "doc_id":{"type":"integer","description":"文档 id"},
                          "offset":{"type":"integer","description":"起始字符位置，默认 0"},
                          "limit":{"type":"integer","description":"返回字符数，默认 4000，最大 20000"}
                        },"required":["doc_id"]}"""));
        return result;
    }

    private ObjectNode tool(String name, String description, String inputSchema) {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", name);
        node.put("description", description);
        try {
            node.set("inputSchema", mapper.readTree(inputSchema));
        } catch (Exception e) {
            node.putObject("inputSchema").put("type", "object");
        }
        return node;
    }

    private JsonNode callTool(JsonNode params) {
        String name = params.path("name").asText("");
        JsonNode args = params.path("arguments");
        ObjectNode result = mapper.createObjectNode();
        ArrayNode content = result.putArray("content");
        try {
            String text = executeTool(name, args == null || args.isMissingNode() ? mapper.createObjectNode() : args);
            content.addObject().put("type", "text").put("text", text);
            result.put("isError", false);
        } catch (Exception e) {
            content.addObject().put("type", "text").put("text", "调用失败：" + (e.getMessage() == null ? e.toString() : e.getMessage()));
            result.put("isError", true);
        }
        return result;
    }

    private String executeTool(String name, JsonNode args) {
        return switch (name) {
            case "search_knowledge" -> searchKnowledge(args);
            case "ask_knowledge" -> askKnowledge(args);
            case "list_knowledge_bases" -> listKnowledgeBases();
            case "list_documents" -> listDocuments(args);
            case "get_document" -> getDocument(args);
            default -> throw new McpException(-32602, "未知工具: " + name);
        };
    }

    private String searchKnowledge(JsonNode args) {
        String query = args.path("query").asText("");
        if (query.isBlank()) {
            throw new McpException(-32602, "query 不能为空");
        }
        Integer topK = args.hasNonNull("top_k") ? args.get("top_k").asInt() : null;
        Double minScore = args.hasNonNull("min_score") ? args.get("min_score").asDouble() : null;
        Views.SearchResult result = searchService.search(readKbIds(args), query, topK, minScore, null);
        if (result.getHits().isEmpty()) {
            return "未检索到相关内容（知识库：" + describeKbScope(readKbIds(args))
                    + "）。建议换用更宽泛的问法，或确认文档已完成向量化。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("检索完成，共 ").append(result.getHits().size()).append(" 条相关片段（向量后端 ")
                .append(backendLabel(result.getBackend())).append("，耗时 ").append(result.getTookMs()).append(" ms）：\n\n");
        int i = 1;
        for (Views.ChunkHit hit : result.getHits()) {
            sb.append("[").append(i++).append("] 《").append(hit.getDocName()).append("》");
            if (hit.getKbName() != null) {
                sb.append("　知识库「").append(hit.getKbName()).append("」");
            }
            sb.append("　片段 #").append(hit.getSeq())
                    .append("　相似度 ").append(String.format("%.4f", hit.getScore()))
                    .append("　doc_id=").append(hit.getDocId()).append("\n");
            sb.append(hit.getContent() == null ? "" : hit.getContent().trim()).append("\n\n");
        }
        sb.append("如需更多原文细节，可调用 get_document 并传入对应的 doc_id。");
        return sb.toString();
    }

    private String askKnowledge(JsonNode args) {
        String question = args.path("question").asText("");
        if (question.isBlank()) {
            throw new McpException(-32602, "question 不能为空");
        }
        Requests.AskReq req = new Requests.AskReq();
        req.setQuestion(question);
        req.setKbIds(readKbIds(args));
        req.setTopK(args.hasNonNull("top_k") ? args.get("top_k").asInt() : null);
        Views.AskResult result = chatService.ask(req);
        StringBuilder sb = new StringBuilder(result.getAnswer());
        sb.append("\n\n---\n");
        sb.append("模型：").append(result.isLlmUsed() ? result.getModel() : "未配置对话模型（仅返回检索结果）")
                .append("；耗时 ").append(result.getTookMs()).append(" ms；引用片段 ")
                .append(result.getCitations() == null ? 0 : result.getCitations().size()).append(" 条");
        if (result.getCitations() != null && !result.getCitations().isEmpty()) {
            sb.append("\n\n引用来源：");
            int i = 1;
            for (Views.ChunkHit hit : result.getCitations()) {
                sb.append("\n[").append(i++).append("] 《").append(hit.getDocName())
                        .append("》片段 #").append(hit.getSeq())
                        .append("（相似度 ").append(String.format("%.3f", hit.getScore())).append("）");
            }
        }
        return sb.toString();
    }

    private String listKnowledgeBases() {
        List<KnowledgeBaseDto> list = kbService.list();
        if (list.isEmpty()) {
            return "当前还没有创建任何知识库。";
        }
        StringBuilder sb = new StringBuilder("共 ").append(list.size()).append(" 个知识库：\n\n");
        for (KnowledgeBaseDto kb : list) {
            sb.append("- kb_id=").append(kb.getId())
                    .append(" | ").append(kb.getName())
                    .append(" | 文档 ").append(kb.getDocCount()).append(" 篇")
                    .append(" / 片段 ").append(kb.getChunkCount()).append(" 个")
                    .append(" | Embedding: ").append(kb.getEmbeddingModelName() == null ? "-" : kb.getEmbeddingModelName())
                    .append(" | MCP: ").append(Boolean.TRUE.equals(kb.getMcpExposed()) ? "已开放" : "未开放");
            if (kb.getDescription() != null && !kb.getDescription().isBlank()) {
                sb.append("\n  说明：").append(kb.getDescription());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private String listDocuments(JsonNode args) {
        long kbId = args.path("kb_id").asLong(0);
        if (kbId <= 0) {
            throw new McpException(-32602, "kb_id 必填且必须为正整数");
        }
        KnowledgeBaseDto kb = kbService.detail(kbId);
        int limit = args.hasNonNull("limit") ? Math.min(Math.max(args.get("limit").asInt(), 1), 200) : 50;
        String keyword = args.path("keyword").asText(null);
        String folder = args.path("folder").asText("").trim();
        List<com.literag.dto.DocumentDto> docs = documentService.listBrief(kbId, keyword, limit);
        if (!folder.isEmpty()) {
            String needle = folder.replace(" / ", "/").toLowerCase();
            docs = docs.stream()
                    .filter(d -> d.getFolderPath() != null
                            && d.getFolderPath().replace(" / ", "/").toLowerCase().contains(needle))
                    .toList();
        }
        if (docs.isEmpty()) {
            return "知识库「" + kb.getName() + "」下暂时没有匹配的文档。";
        }
        StringBuilder sb = new StringBuilder("知识库「").append(kb.getName()).append("」共 ")
                .append(docs.size()).append(" 篇文档（最多展示 ").append(limit).append(" 篇）：\n\n");
        for (com.literag.dto.DocumentDto d : docs) {
            sb.append("- doc_id=").append(d.getId())
                    .append(" | ").append(d.getName())
                    .append(" | ").append(d.getSourceType())
                    .append(" | 状态 ").append(d.getStatus())
                    .append(" | 片段 ").append(d.getChunkCount()).append(" 个");
            if (d.getFolderPath() != null && !d.getFolderPath().isBlank()) {
                sb.append(" | 目录 ").append(d.getFolderPath());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private String getDocument(JsonNode args) {
        long docId = args.path("doc_id").asLong(0);
        if (docId <= 0) {
            throw new McpException(-32602, "doc_id 必填且必须为正整数");
        }
        var doc = documentService.detail(docId, true);
        String content = doc.getContent() == null ? "" : doc.getContent();
        int offset = args.hasNonNull("offset") ? Math.max(0, args.get("offset").asInt()) : 0;
        int limit = args.hasNonNull("limit") ? Math.min(Math.max(args.get("limit").asInt(), 200), 20000) : 4000;
        if (offset >= content.length()) {
            return "offset 超出文档长度（总长度 " + content.length() + " 字符）。";
        }
        int end = Math.min(content.length(), offset + limit);
        StringBuilder sb = new StringBuilder();
        sb.append("《").append(doc.getName()).append("》\n")
                .append("doc_id=").append(doc.getId())
                .append(" | 知识库：").append(doc.getKbName())
                .append(" | 类型：").append(doc.getSourceType())
                .append(" | 目录：").append(doc.getFolderPath() == null ? "根目录" : doc.getFolderPath())
                .append(" | 总字数：").append(doc.getCharCount())
                .append(" | 分块：").append(doc.getChunkCount())
                .append("\n字符区间：").append(offset).append(" - ").append(end)
                .append("（共 ").append(content.length()).append("）\n\n");
        sb.append(content, offset, end);
        if (end < content.length()) {
            sb.append("\n\n……（后续内容可调整 offset=").append(end).append(" 继续读取）");
        }
        return sb.toString();
    }

    private List<Long> readKbIds(JsonNode args) {
        List<Long> ids = new ArrayList<>();
        JsonNode node = args.path("kb_ids");
        if (node.isArray()) {
            node.forEach(n -> {
                if (n.canConvertToLong()) {
                    ids.add(n.asLong());
                }
            });
        } else if (node.canConvertToLong()) {
            ids.add(node.asLong());
        }
        return ids;
    }

    private String backendLabel(String backend) {
        if (backend == null) {
            return "-";
        }
        return switch (backend) {
            case "qdrant" -> "Qdrant";
            case "local" -> "本地向量库";
            default -> backend;
        };
    }

    private String describeKbScope(List<Long> kbIds) {
        return kbIds == null || kbIds.isEmpty() ? "全部知识库" : "kb_ids=" + kbIds;
    }

    // ------------------------------------------------------------- resources

    private JsonNode resourcesList() {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode resources = result.putArray("resources");
        ObjectNode index = resources.addObject();
        index.put("uri", "literag://knowledge-bases");
        index.put("name", "知识库索引");
        index.put("description", "全部知识库的清单与统计信息");
        index.put("mimeType", "application/json");
        for (KnowledgeBaseDto kb : kbService.list()) {
            ObjectNode node = resources.addObject();
            node.put("uri", "literag://kb/" + kb.getId());
            node.put("name", kb.getName());
            node.put("description", kb.getDescription() == null ? "知识库" : kb.getDescription());
            node.put("mimeType", "application/json");
        }
        return result;
    }

    private JsonNode resourceTemplates() {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode templates = result.putArray("resourceTemplates");
        ObjectNode t1 = templates.addObject();
        t1.put("uriTemplate", "literag://kb/{kbId}/documents");
        t1.put("name", "知识库文档清单");
        t1.put("mimeType", "application/json");
        ObjectNode t2 = templates.addObject();
        t2.put("uriTemplate", "literag://doc/{docId}");
        t2.put("name", "文档全文");
        t2.put("mimeType", "text/markdown");
        return result;
    }

    private JsonNode readResource(JsonNode params) {
        String uri = params.path("uri").asText("");
        String text;
        String mime = "text/plain";
        if ("literag://knowledge-bases".equals(uri)) {
            mime = "application/json";
            text = toJson(kbService.list());
        } else if (uri.startsWith("literag://kb/") && uri.endsWith("/documents")) {
            long kbId = parseId(uri.replace("literag://kb/", "").replace("/documents", ""));
            mime = "application/json";
            text = toJson(documentService.listBrief(kbId, null, 200));
        } else if (uri.startsWith("literag://kb/")) {
            long kbId = parseId(uri.substring("literag://kb/".length()));
            mime = "application/json";
            text = toJson(Map.of("knowledgeBase", kbService.detail(kbId),
                    "documents", documentService.listBrief(kbId, null, 200)));
        } else if (uri.startsWith("literag://doc/")) {
            long docId = parseId(uri.substring("literag://doc/".length()));
            var doc = documentService.detail(docId, true);
            mime = "text/markdown";
            text = doc.getContent() == null ? "" : doc.getContent();
        } else {
            throw new McpException(-32602, "未知资源: " + uri);
        }
        ObjectNode result = mapper.createObjectNode();
        ArrayNode contents = result.putArray("contents");
        ObjectNode item = contents.addObject();
        item.put("uri", uri);
        item.put("mimeType", mime);
        item.put("text", text);
        return result;
    }

    private long parseId(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception e) {
            throw new McpException(-32602, "非法的资源 id: " + raw);
        }
    }

    private String toJson(Object value) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private ObjectNode errorNode(int code, String message) {
        ObjectNode error = mapper.createObjectNode();
        error.put("code", code);
        error.put("message", message == null ? "unknown error" : message);
        return error;
    }

    static class McpException extends RuntimeException {
        private final int code;

        McpException(int code, String message) {
            super(message);
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }
}
