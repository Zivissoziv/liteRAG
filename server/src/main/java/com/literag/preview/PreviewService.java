package com.literag.preview;

import com.literag.common.BizException;
import com.literag.entity.Document;
import com.literag.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PreviewService {

    private static final Set<String> SLIDE_EXT = Set.of("pptx", "pptm", "ppsx");
    private static final Set<String> SHEET_EXT = Set.of("xlsx", "xls", "csv");
    private static final Set<String> WORD_EXT = Set.of("docx", "docm", "dotx");
    private static final Set<String> IMAGE_EXT = Set.of("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg");

    private final StorageService storage;
    private final PptxHtmlRenderer pptxRenderer;

    public Path requireFile(Document doc) {
        if (doc.getFilePath() == null || doc.getFilePath().isBlank()) {
            throw new BizException(404, "该文档没有原始文件，只能查看解析文本");
        }
        Path path = storage.resolve(doc.getFilePath());
        if (!Files.isRegularFile(path)) {
            throw new BizException(404, "原始文件已丢失");
        }
        return path;
    }

    public MediaType mediaType(String ext) {
        String e = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        return switch (e) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "docx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "doc" -> MediaType.parseMediaType("application/msword");
            case "xlsx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "xls" -> MediaType.parseMediaType("application/vnd.ms-excel");
            case "pptx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation");
            case "txt", "md", "markdown", "csv", "log", "json", "xml", "html" ->
                    new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8);
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "bmp" -> MediaType.parseMediaType("image/bmp");
            case "svg" -> MediaType.parseMediaType("image/svg+xml");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    public boolean isSlide(String ext) {
        return ext != null && SLIDE_EXT.contains(ext.toLowerCase(Locale.ROOT));
    }

    public boolean isSheet(String ext) {
        return ext != null && SHEET_EXT.contains(ext.toLowerCase(Locale.ROOT));
    }

    public boolean isWord(String ext) {
        return ext != null && WORD_EXT.contains(ext.toLowerCase(Locale.ROOT));
    }

    public boolean isImage(String ext) {
        return ext != null && IMAGE_EXT.contains(ext.toLowerCase(Locale.ROOT));
    }

    public String slidesHtml(Document doc) {
        if (!isSlide(doc.getFileExt())) {
            throw new BizException(400, "仅支持 pptx 版式预览");
        }
        String html = pptxRenderer.render(requireFile(doc), doc.getName());
        if (html == null) {
            throw new BizException(500, "无法渲染该演示文稿");
        }
        return html;
    }
}
