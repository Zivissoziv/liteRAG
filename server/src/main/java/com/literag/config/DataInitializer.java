package com.literag.config;

import com.literag.dto.Requests;
import com.literag.entity.KnowledgeBase;
import com.literag.entity.ModelConfig;
import com.literag.entity.SysUser;
import com.literag.repository.KnowledgeBaseRepository;
import com.literag.repository.ModelConfigRepository;
import com.literag.repository.SysUserRepository;
import com.literag.security.PasswordHasher;
import com.literag.service.DocumentService;
import com.literag.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 首次启动时初始化默认 embedding 模型与示例知识库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final ModelConfigRepository modelRepository;
    private final KnowledgeBaseRepository kbRepository;
    private final KnowledgeBaseService kbService;
    private final DocumentService documentService;
    private final SysUserRepository userRepository;
    private final PasswordHasher passwordHasher;

    @Override
    public void run(ApplicationArguments args) {
        initUsers();
        initDefaultModel();
        initSampleWiki();
    }

    /**
     * 首次启动创建三个演示账号，分别对应管理员 / 编辑者 / 只读。
     * 已存在任何用户时不再干预，避免覆盖线上改过的密码。
     */
    private void initUsers() {
        if (userRepository.count() > 0) {
            return;
        }
        createUser("admin", "admin123", "管理员", SysUser.ROLE_ADMIN);
        createUser("editor", "editor123", "编辑者", SysUser.ROLE_EDITOR);
        createUser("viewer", "viewer123", "只读用户", SysUser.ROLE_VIEWER);
        log.info("已创建默认账号：admin / editor / viewer（密码分别为同名加 123）");
    }

    private void createUser(String username, String password, String displayName, String role) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordHasher.hash(password));
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setEnabled(true);
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    private void initDefaultModel() {
        if (modelRepository.countByType(ModelConfig.TYPE_EMBEDDING) > 0) {
            return;
        }
        ModelConfig cfg = new ModelConfig();
        cfg.setName("本地哈希向量（离线演示）");
        cfg.setType(ModelConfig.TYPE_EMBEDDING);
        cfg.setProvider(ModelConfig.PROVIDER_LOCAL);
        cfg.setBaseUrl("");
        cfg.setModelName("local-hash-256");
        cfg.setDimension(256);
        cfg.setBatchSize(32);
        cfg.setTimeout(30);
        cfg.setEnabled(true);
        cfg.setIsDefault(true);
        cfg.setRemark("零依赖的离线向量化模型，适合快速验证流程；正式使用请切换为语义向量模型");
        modelRepository.save(cfg);
        log.info("已创建默认本地 Embedding 模型");
    }

    private void initSampleWiki() {
        if (kbRepository.count() > 0) {
            return;
        }
        Requests.KbReq req = new Requests.KbReq();
        req.setName("LiteRAG 使用指南");
        req.setDescription("系统自带的示例知识库，说明 LiteRAG 的能力、接口与使用方式。");
        req.setChunkSize(500);
        req.setChunkOverlap(80);
        req.setMcpExposed(true);
        long kbId = kbService.create(req).getId();

        String content = """
                # LiteRAG 是什么

                LiteRAG 是一套基于检索增强生成（RAG）的 Wiki 文档管理系统。它由 Vue 3 前端、Spring Boot 后端与 ChromaDB 向量数据库组成，
                支持把 docx、pdf、xlsx、pptx、markdown 等文档转换成可检索的知识片段。

                ## 核心能力

                1. 文档知识管理：上传 docx / pdf / xlsx / xls / pptx / md / txt 等格式，系统自动解析、段落感知分块并写入向量库。
                2. 自定义 Embedding 模型：支持 OpenAI 兼容接口（OpenAI、硅基流动、通义 compatible-mode、智谱、vLLM、one-api）、本地 Ollama，以及离线哈希模型。
                3. 向量检索：支持跨知识库语义检索、相似度阈值过滤、按文档过滤，并返回带相似度得分的原文片段。
                4. MCP 知识服务：以内置 MCP Server 的形式向 Claude Desktop、Cursor 等支持 MCP 的客户端提供 search_knowledge、ask_knowledge 等工具。
                5. RAG 问答：检索到的片段作为上下文交给大模型，回答中标注来源编号，便于溯源。

                ## 典型使用流程

                第一步，在「模型配置」中新增一个 Embedding 模型并测试连接，把它设为默认模型。
                第二步，新建知识库，选择该 Embedding 模型与合适的分块长度。
                第三步，在知识库中上传文档或创建 Wiki 页面，等待状态变为「已就绪」。
                第四步，在「检索测试」中验证召回效果，必要时调整分块长度与相似度阈值。
                第五步，在「MCP 接入」中复制配置到 MCP 客户端，让外部 AI 工具直接使用你的知识库。

                ## MCP 接入说明

                LiteRAG 的 MCP 服务地址为 http://localhost:8080/mcp，使用 Streamable HTTP 传输；
                同时兼容旧版 HTTP+SSE 传输（/mcp/sse 与 /mcp/message）。可用工具包括：
                search_knowledge 语义检索、ask_knowledge 检索增强问答、list_knowledge_bases 列出知识库、
                list_documents 列出文档、get_document 获取文档全文。

                ## 分块与召回的关系

                分块长度过大会稀释语义，过小会割裂上下文。中文技术文档一般建议 400 至 800 字，重叠 80 至 150 字。
                如果检索结果经常缺少关键上下文，可以适当增大重叠长度后重新索引。
                """;

        Requests.WikiReq wiki = new Requests.WikiReq();
        wiki.setTitle("LiteRAG 快速上手");
        wiki.setContent(content);
        wiki.setCategory("入门");
        wiki.setTags("指南,快速开始");
        documentService.createWiki(kbId, wiki);
        log.info("已创建示例知识库与示例 Wiki 页面");
    }
}
