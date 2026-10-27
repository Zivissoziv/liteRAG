package com.literag.parser;

import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * .docx 解析：按正文顺序保留段落与表格，标题层级用 # 标注。
 */
@Component
public class DocxParser implements DocumentParser {

    @Override
    public boolean supports(String ext) {
        return "docx".equalsIgnoreCase(ext);
    }

    @Override
    public String parse(Path file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = Files.newInputStream(file);
             XWPFDocument doc = new XWPFDocument(in)) {

            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph p) {
                    String text = p.getText() == null ? "" : p.getText().trim();
                    if (text.isEmpty()) {
                        continue;
                    }
                    int level = headingLevel(p.getStyle());
                    if (level > 0) {
                        sb.append("#".repeat(Math.min(level, 6))).append(' ').append(text).append("\n\n");
                    } else {
                        sb.append(text).append('\n');
                    }
                } else if (element instanceof XWPFTable table) {
                    sb.append('\n');
                    for (XWPFTableRow row : table.getRows()) {
                        List<String> cells = new ArrayList<>();
                        row.getTableCells().forEach(c -> cells.add(c.getText() == null ? "" : c.getText().replace("\n", " ").trim()));
                        if (cells.stream().anyMatch(s -> !s.isEmpty())) {
                            sb.append(String.join(" | ", cells)).append('\n');
                        }
                    }
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    private int headingLevel(String style) {
        if (style == null) {
            return 0;
        }
        String s = style.toLowerCase();
        if (s.startsWith("heading")) {
            String num = s.replaceAll("[^0-9]", "");
            try {
                return Integer.parseInt(num);
            } catch (Exception e) {
                return 1;
            }
        }
        if (s.contains("标题")) {
            String num = s.replaceAll("[^0-9]", "");
            try {
                return Integer.parseInt(num);
            } catch (Exception e) {
                return 1;
            }
        }
        return 0;
    }
}
