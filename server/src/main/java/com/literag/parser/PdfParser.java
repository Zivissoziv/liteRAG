package com.literag.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * .pdf 解析：逐页抽取文本，页间以空行分隔。
 */
@Component
public class PdfParser implements DocumentParser {

    @Override
    public boolean supports(String ext) {
        return "pdf".equalsIgnoreCase(ext);
    }

    @Override
    public String parse(Path file) throws Exception {
        try (PDDocument doc = PDDocument.load(file.toFile())) {
            if (doc.isEncrypted()) {
                throw new IllegalStateException("PDF 已加密，无法解析");
            }
            int pages = doc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            StringBuilder sb = new StringBuilder();
            // 单页抽取，避免超长文档一次性占用过多内存
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(doc);
                if (text != null && !text.isBlank()) {
                    sb.append(text.trim()).append("\n\n");
                }
            }
            return sb.toString();
        }
    }
}
