package com.literag.parser;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * .pptx 解析：按幻灯片抽取文本框内容。
 */
@Component
public class PptxParser implements DocumentParser {

    @Override
    public boolean supports(String ext) {
        return "pptx".equalsIgnoreCase(ext);
    }

    @Override
    public String parse(Path file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = Files.newInputStream(file);
             XMLSlideShow ppt = new XMLSlideShow(in)) {
            int index = 1;
            for (XSLFSlide slide : ppt.getSlides()) {
                sb.append("## 幻灯片 ").append(index++).append("\n\n");
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            sb.append(text.trim()).append("\n\n");
                        }
                    }
                }
            }
        }
        return sb.toString();
    }
}
