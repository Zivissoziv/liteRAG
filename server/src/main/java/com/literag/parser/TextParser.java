package com.literag.parser;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 纯文本类：md / markdown / txt / log / json / xml / html 等。
 */
@Component
public class TextParser implements DocumentParser {

    @Override
    public boolean supports(String ext) {
        String e = ext == null ? "" : ext.toLowerCase();
        return switch (e) {
            case "txt", "md", "markdown", "log", "json", "xml", "yaml", "yml", "html", "htm", "csv" -> true;
            default -> false;
        };
    }

    @Override
    public String parse(Path file) throws Exception {
        String ext = extension(file);
        String text = Files.readString(file, StandardCharsets.UTF_8);
        // 去掉 UTF-8 BOM
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        if ("html".equals(ext) || "htm".equals(ext)) {
            text = stripHtml(text);
        }
        return text;
    }

    private String stripHtml(String html) {
        String s = html.replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?is)<!--.*?-->", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|li|h[1-6]|tr)>", "\n")
                .replaceAll("<[^>]+>", " ");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'");
        return s.replaceAll("[ \\t]{2,}", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private String extension(Path file) {
        String name = file.getFileName().toString();
        int idx = name.lastIndexOf('.');
        return idx < 0 ? "" : name.substring(idx + 1).toLowerCase();
    }
}
