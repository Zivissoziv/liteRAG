package com.literag.parser;

import com.literag.common.BizException;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ParserRegistry {

    private final List<DocumentParser> parsers;

    public ParserRegistry(List<DocumentParser> parsers) {
        this.parsers = parsers;
    }

    public Set<String> supportedExtensions() {
        Set<String> exts = new LinkedHashSet<>();
        exts.add("docx");
        exts.add("pdf");
        exts.add("xlsx");
        exts.add("xls");
        exts.add("pptx");
        exts.add("md");
        exts.add("markdown");
        exts.add("txt");
        exts.add("csv");
        exts.add("html");
        exts.add("htm");
        exts.add("json");
        return exts;
    }

    public boolean supported(String ext) {
        return supportedExtensions().contains(ext == null ? "" : ext.toLowerCase(Locale.ROOT));
    }

    public String parse(Path file, String ext) {
        String e = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        for (DocumentParser parser : parsers) {
            if (parser.supports(e)) {
                try {
                    String text = parser.parse(file);
                    if (text == null || text.isBlank()) {
                        throw new BizException("文件解析结果为空，可能是扫描版/图片型文件：" + file.getFileName());
                    }
                    return text;
                } catch (BizException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new BizException("解析 " + file.getFileName() + " 失败：" + ex.getMessage());
                }
            }
        }
        throw new BizException("暂不支持的文件类型：." + e + "，当前支持 " + String.join("/", supportedExtensions()));
    }
}
