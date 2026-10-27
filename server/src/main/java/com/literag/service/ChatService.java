package com.literag.service;

import com.literag.ai.EmbeddingService;
import com.literag.dto.Requests;
import com.literag.dto.Views;
import com.literag.entity.ModelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String SYSTEM_PROMPT = """
            你是 LiteRAG 知识库问答助手。请严格依据【参考资料】回答用户问题，不要编造资料之外的事实。
            要求：
            1. 若资料足以回答，给出结构化、准确的中文答案，并在相应句子后标注来源编号，如 [1]、[2]。
            2. 若资料不足，明确说明缺少什么信息，不要凭空推测。
            3. 不要重复大段原文，用总结与归纳的方式表达。
            """;

    private final SearchService searchService;
    private final ModelConfigService modelConfigService;
    private final EmbeddingService embeddingService;
    private final SettingsService settings;

    public Views.AskResult ask(Requests.AskReq req) {
        long start = System.currentTimeMillis();
        if (req.getQuestion() == null || req.getQuestion().isBlank()) {
            throw new com.literag.common.BizException("问题不能为空");
        }
        Views.SearchResult search = searchService.search(req.getKbIds(), req.getQuestion(), req.getTopK(), 0.0, null);
        List<Views.ChunkHit> hits = search.getHits();

        if (hits.isEmpty()) {
            return Views.AskResult.builder()
                    .answer("知识库中没有检索到与该问题相关的内容。可以尝试更换问法、扩大检索范围，或先上传相关文档。")
                    .backend(search.getBackend())
                    .llmUsed(false)
                    .tookMs(System.currentTimeMillis() - start)
                    .citations(List.of())
                    .build();
        }

        String context = buildContext(hits, settings.getInt(SettingsService.MAX_CONTEXT_CHARS, 6000));
        String userPrompt = "【参考资料】\n" + context + "\n\n【问题】" + req.getQuestion().trim();
        ModelConfig chatModel = modelConfigService.resolveChat();

        if (chatModel == null || chatModel.getModelName() == null || chatModel.getModelName().isBlank()) {
            return Views.AskResult.builder()
                    .answer("当前未配置对话大模型，以下为检索到的原文片段（可在「模型配置」中新增 CHAT 模型后获得归纳式回答）：\n\n"
                            + previewOf(hits))
                    .backend(search.getBackend())
                    .llmUsed(false)
                    .tookMs(System.currentTimeMillis() - start)
                    .promptPreview(userPrompt.length() > 2000 ? userPrompt.substring(0, 2000) : userPrompt)
                    .citations(hits)
                    .build();
        }

        double temperature = settings.getDouble(SettingsService.LLM_TEMPERATURE, 0.2);
        String answer = embeddingService.resolveChat(chatModel.getProvider())
                .chat(chatModel, SYSTEM_PROMPT, userPrompt, temperature);

        return Views.AskResult.builder()
                .answer(answer)
                .model(chatModel.getModelName())
                .backend(search.getBackend())
                .llmUsed(true)
                .tookMs(System.currentTimeMillis() - start)
                .promptPreview(userPrompt.length() > 2000 ? userPrompt.substring(0, 2000) : userPrompt)
                .citations(hits)
                .build();
    }

    private String buildContext(List<Views.ChunkHit> hits, int maxChars) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (Views.ChunkHit hit : hits) {
            String block = "[" + index + "] 《" + (hit.getDocName() == null ? hit.getTitle() : hit.getDocName())
                    + "》 片段#" + hit.getSeq() + "\n" + hit.getContent() + "\n\n";
            if (sb.length() + block.length() > maxChars) {
                break;
            }
            sb.append(block);
            index++;
        }
        return sb.toString().trim();
    }

    private String previewOf(List<Views.ChunkHit> hits) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (Views.ChunkHit hit : hits) {
            String content = hit.getContent() == null ? "" : hit.getContent();
            if (content.length() > 300) {
                content = content.substring(0, 300) + "…";
            }
            sb.append(index++).append(". 《").append(hit.getDocName()).append("》 (相似度 ")
                    .append(String.format("%.3f", hit.getScore())).append(")\n").append(content).append("\n\n");
        }
        return sb.toString().trim();
    }
}
