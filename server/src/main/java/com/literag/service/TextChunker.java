package com.literag.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 段落感知的中文友好分块器：优先按空行/段落切分，超长段落按句末标点滑窗切分，
 * 相邻分块保留 overlap 个字符的上下文重叠。
 */
@Component
public class TextChunker {

    public List<String> split(String text, int maxChars, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        int size = Math.max(200, maxChars);
        int ov = Math.max(0, Math.min(overlap, size / 2));

        String normalized = text.replace("\r\n", "\n").replace("\r", "\n")
                .replace("\u0000", "")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        if (normalized.isEmpty()) {
            return chunks;
        }

        List<String> units = splitUnits(normalized, size);
        StringBuilder cur = new StringBuilder();
        for (String unit : units) {
            if (cur.length() > 0 && cur.length() + unit.length() + 1 > size) {
                String chunk = cur.toString().trim();
                if (!chunk.isEmpty()) {
                    chunks.add(chunk);
                }
                String tail = tail(chunk, ov);
                cur.setLength(0);
                if (!tail.isEmpty()) {
                    cur.append(tail).append('\n');
                }
            }
            if (cur.length() > 0) {
                cur.append('\n');
            }
            cur.append(unit);
        }
        String last = cur.toString().trim();
        if (!last.isEmpty()) {
            chunks.add(last);
        }
        return chunks;
    }

    private List<String> splitUnits(String text, int maxChars) {
        List<String> units = new ArrayList<>();
        for (String block : text.split("\\n{2,}")) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() <= maxChars) {
                units.add(trimmed);
                continue;
            }
            // 超长段落：先按句子聚合
            StringBuilder buf = new StringBuilder();
            for (String sentence : splitSentences(trimmed)) {
                if (buf.length() > 0 && buf.length() + sentence.length() > maxChars) {
                    units.add(buf.toString().trim());
                    buf.setLength(0);
                }
                if (sentence.length() > maxChars) {
                    if (buf.length() > 0) {
                        units.add(buf.toString().trim());
                        buf.setLength(0);
                    }
                    units.addAll(hardSlice(sentence, maxChars));
                } else {
                    buf.append(sentence);
                }
            }
            if (buf.length() > 0) {
                units.add(buf.toString().trim());
            }
        }
        return units;
    }

    private List<String> splitSentences(String block) {
        List<String> sentences = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < block.length(); i++) {
            char c = block.charAt(i);
            buf.append(c);
            if ("。！？!?；;\n".indexOf(c) >= 0) {
                sentences.add(buf.toString());
                buf.setLength(0);
            }
        }
        if (buf.length() > 0) {
            sentences.add(buf.toString());
        }
        return sentences;
    }

    private List<String> hardSlice(String text, int maxChars) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < text.length(); i += maxChars) {
            parts.add(text.substring(i, Math.min(text.length(), i + maxChars)));
        }
        return parts;
    }

    private String tail(String chunk, int overlap) {
        if (overlap <= 0 || chunk.isEmpty()) {
            return "";
        }
        int start = Math.max(0, chunk.length() - overlap);
        // 尽量从一个句子边界开始，避免截断词语
        for (int i = start; i < Math.min(chunk.length(), start + 40); i++) {
            char c = chunk.charAt(i);
            if ("。！？!?\n".indexOf(c) >= 0) {
                return chunk.substring(i + 1).trim();
            }
        }
        return chunk.substring(start).trim();
    }

    // ------------------------------------------------------- 标题结构分块

    /**
     * 按 Markdown 标题（# ~ ######）切分，每个分块前缀带上完整章节路径，
     * 如「部署指南 > Docker 方式」，让检索命中的片段自带出处上下文。
     * 解析器输出的「## 工作表：X」「## 幻灯片 N」同样被识别。
     */
    public List<String> splitByHeading(String text, int maxChars, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        int size = Math.max(200, maxChars);
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n").trim();

        // path 栈：level -> 标题；cur 为当前小节的正文
        List<String> path = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        String lastPathKey = null;
        List<String[]> sections = new ArrayList<>(); // [pathKey, body]

        for (String line : normalized.split("\n", -1)) {
            var m = HEADING.matcher(line);
            if (m.find()) {
                if (!cur.isEmpty()) {
                    appendSection(sections, path, cur, lastPathKey);
                    cur.setLength(0);
                }
                int level = m.group(1).length();
                String title = m.group(2).trim();
                while (path.size() >= level) {
                    path.remove(path.size() - 1);
                }
                path.add(title);
                lastPathKey = null; // 新标题开始新的小节
                continue;
            }
            cur.append(line).append('\n');
        }
        if (!cur.isEmpty()) {
            appendSection(sections, path, cur, lastPathKey);
        }

        StringBuilder out = new StringBuilder();
        for (String[] sec : sections) {
            String prefix = sec[0].isEmpty() ? "" : sec[0] + "\n";
            String body = sec[1].trim();
            if (body.isEmpty()) {
                continue;
            }
            // 超长小节正文退回通用切分，每片保留章节前缀
            int budget = Math.max(120, size - prefix.length());
            List<String> pieces = body.length() <= budget
                    ? List.of(body)
                    : split(body, budget, overlap);
            for (String piece : pieces) {
                String full = (prefix + piece).trim();
                if (out.length() > 0 && out.length() + full.length() + 2 > size) {
                    chunks.add(out.toString().trim());
                    out.setLength(0);
                }
                if (out.length() > 0) {
                    out.append("\n\n");
                }
                out.append(full);
            }
        }
        if (!out.isEmpty()) {
            chunks.add(out.toString().trim());
        }
        return chunks;
    }

    private static final java.util.regex.Pattern HEADING =
            java.util.regex.Pattern.compile("^(#{1,6})\\s+(\\S.*)$");

    /** 合并连续相同路径的小节，避免每个短段都单独成块 */
    private void appendSection(List<String[]> sections, List<String> path,
                               StringBuilder body, String lastPathKey) {
        String pathKey = String.join(" > ", path);
        String trimmed = body.toString().trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (!sections.isEmpty() && pathKey.equals(lastPathKey) && pathKey.isEmpty()) {
            // 无标题的散文合并到前一节
            String[] prev = sections.get(sections.size() - 1);
            prev[1] = prev[1] + "\n\n" + trimmed;
            return;
        }
        if (!sections.isEmpty() && pathKey.equals(lastPathKey)) {
            String[] prev = sections.get(sections.size() - 1);
            prev[1] = prev[1] + "\n\n" + trimmed;
            return;
        }
        sections.add(new String[]{pathKey, trimmed});
    }

    // ------------------------------------------------------- 表格感知分块

    /**
     * 表格感知分块：识别解析器输出的「## 工作表：X」小节，竖线行按表头分组打包。
     * 每个分块都带工作表名和表头，保证单独一块也可读。
     * 非表格文本（没有工作表标记）返回 null，由调用方回退到通用切分。
     */
    public List<String> splitByTable(String text, int maxChars) {
        if (text == null || !text.contains("## 工作表：")) {
            return null;
        }
        int size = Math.max(200, maxChars);
        List<String> chunks = new ArrayList<>();
        String[] lines = text.replace("\r\n", "\n").split("\n");
        String sheet = null;
        String header = null;
        StringBuilder cur = new StringBuilder();

        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("## 工作表：")) {
                flushTableChunk(chunks, sheet, header, cur);
                sheet = t.substring("##".length()).replace("工作表：", "").trim();
                header = null;
                cur.setLength(0);
                continue;
            }
            if (t.isEmpty() || sheet == null) {
                continue;
            }
            if (header == null) {
                header = t;
                continue;
            }
            if (cur.length() > 0 && cur.length() + t.length() + 1 > size - 40) {
                flushTableChunk(chunks, sheet, header, cur);
            }
            cur.append(t).append('\n');
        }
        flushTableChunk(chunks, sheet, header, cur);
        return chunks;
    }

    private void flushTableChunk(List<String> chunks, String sheet, String header, StringBuilder rows) {
        String body = rows.toString().trim();
        if (sheet == null || (body.isEmpty() && (header == null || header.isEmpty()))) {
            return;
        }
        String prefix = "【工作表：" + sheet + "】";
        if (header != null && !header.isEmpty()) {
            prefix += "\n表头：" + header;
        }
        if (!body.isEmpty()) {
            chunks.add(prefix + "\n" + body);
        } else {
            chunks.add(prefix);
        }
        rows.setLength(0);
    }
}
