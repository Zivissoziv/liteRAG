package com.literag.preview;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.sl.usermodel.ColorStyle;
import org.apache.poi.sl.usermodel.PaintStyle;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFBackground;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSimpleShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * 把 pptx 按幻灯片版面渲染成 HTML，供前端 iframe 预览。
 * 只处理定位、填充、文字、表格与图片，不做图形特效。
 */
@Slf4j
@Component
public class PptxHtmlRenderer {

    public String render(Path file, String title) {
        try (InputStream in = Files.newInputStream(file); XMLSlideShow ppt = new XMLSlideShow(in)) {
            Dimension page = ppt.getPageSize();
            double width = page.getWidth();
            double height = page.getHeight();

            StringBuilder body = new StringBuilder();
            int index = 0;
            double widthPx = width * 4.0 / 3.0;
            double heightPx = height * 4.0 / 3.0;
            for (XSLFSlide slide : ppt.getSlides()) {
                index++;
                body.append("<div class=\"frame\"><section class=\"slide\" data-w=\"").append(round(widthPx))
                        .append("\" data-h=\"").append(round(heightPx))
                        .append("\" style=\"width:").append(round(widthPx))
                        .append("px;height:").append(round(heightPx)).append("px\">");
                background(slide, body);
                for (XSLFShape shape : slide.getShapes()) {
                    try {
                        shape(shape, body);
                    } catch (Exception e) {
                        log.debug("跳过无法渲染的元素 {}: {}", shape.getShapeName(), e.getMessage());
                    }
                }
                body.append("<span class=\"no\">").append(index).append("</span>");
                body.append("</section></div>");
            }
            return document(title, body.toString());
        } catch (Exception e) {
            log.warn("PPTX 预览渲染失败 {}: {}", file, e.getMessage());
            return null;
        }
    }

    private void background(XSLFSlide slide, StringBuilder sb) {
        try {
            XSLFBackground bg = slide.getBackground();
            Color color = bg == null ? null : bg.getFillColor();
            if (color != null) {
                sb.append("<div class=\"bg\" style=\"background:").append(css(color)).append("\"></div>");
            }
        } catch (Exception ignored) {
            // 背景取不到就保持白色
        }
    }

    private void shape(XSLFShape shape, StringBuilder sb) {
        Rectangle2D anchor = shape.getAnchor();
        if (anchor == null || anchor.getWidth() <= 0 || anchor.getHeight() <= 0) {
            return;
        }
        String box = String.format("left:%s;top:%s;width:%s;height:%s;",
                pt(anchor.getX()), pt(anchor.getY()), pt(anchor.getWidth()), pt(anchor.getHeight()));

        if (shape instanceof XSLFTable table) {
            table(table, box, sb);
            return;
        }
        if (shape instanceof XSLFPictureShape picture) {
            picture(picture, box, sb);
            return;
        }
        if (shape instanceof XSLFTextShape text) {
            String fill = css(fillColor(shape));
            sb.append("<div class=\"shape tx\" style=\"").append(box)
                    .append(fill == null ? "" : "background:" + fill + ";");
            VerticalAlignment va = text.getVerticalAlignment();
            String justify = va == VerticalAlignment.MIDDLE ? "center"
                    : va == VerticalAlignment.BOTTOM ? "flex-end" : "flex-start";
            sb.append("justify-content:").append(justify).append("\">");
            paragraphs(text, sb);
            sb.append("</div>");
            return;
        }
        String fill = css(fillColor(shape));
        if (fill != null) {
            sb.append("<div class=\"shape\" style=\"").append(box)
                    .append("background:").append(fill).append("\"></div>");
        }
    }

    private void paragraphs(XSLFTextShape text, StringBuilder sb) {
        for (XSLFTextParagraph p : text.getTextParagraphs()) {
            TextParagraph.TextAlign align = p.getTextAlign() == null ? TextParagraph.TextAlign.LEFT : p.getTextAlign();
            String alignCss = switch (align) {
                case CENTER -> "center";
                case RIGHT -> "right";
                case JUSTIFY, JUSTIFY_LOW -> "justify";
                case DIST -> "justify";
                default -> "left";
            };
            int level = Math.max(0, p.getIndentLevel());
            sb.append("<p style=\"text-align:").append(alignCss)
                    .append(";padding-left:").append(level * 14).append("pt;\">");
            if (p.isBullet()) {
                sb.append("<span class=\"bu\">•</span>");
            }
            for (XSLFTextRun run : p.getTextRuns()) {
                String raw = run.getRawText();
                if (raw == null || raw.isEmpty()) {
                    continue;
                }
                StringBuilder st = new StringBuilder();
                Double size = run.getFontSize();
                st.append("font-size:").append(pt(size == null || size <= 0 ? 18 : size)).append(";");
                if (run.isBold()) st.append("font-weight:600;");
                if (run.isItalic()) st.append("font-style:italic;");
                if (run.isUnderlined()) st.append("text-decoration:underline;");
                String color = cssPaint(run.getFontColor());
                if (color != null) st.append("color:").append(color).append(";");
                sb.append("<span style=\"").append(st).append("\">")
                        .append(escape(raw).replace("\n", "<br/>")).append("</span>");
            }
            sb.append("</p>");
        }
    }

    private void table(XSLFTable table, String box, StringBuilder sb) {
        sb.append("<div class=\"shape\" style=\"").append(box).append("\">");
        sb.append("<table class=\"tb\"><tbody>");
        for (XSLFTableRow row : table.getRows()) {
            sb.append("<tr>");
            for (XSLFTableCell cell : row.getCells()) {
                String bg = css(cell.getFillColor());
                sb.append("<td");
                if (cell.getGridSpan() > 1) {
                    sb.append(" colspan=\"").append(cell.getGridSpan()).append("\"");
                }
                if (bg != null) {
                    sb.append(" style=\"background:").append(bg).append("\"");
                }
                sb.append(">").append(escape(cell.getText() == null ? "" : cell.getText())).append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</tbody></table></div>");
    }

    private void picture(XSLFPictureShape picture, String box, StringBuilder sb) {
        XSLFPictureData data = picture.getPictureData();
        if (data == null) {
            return;
        }
        byte[] bytes = data.getData();
        String mime = sniff(bytes);
        if (mime == null) {
            return;
        }
        sb.append("<div class=\"shape\" style=\"").append(box).append("\">")
                .append("<img src=\"data:").append(mime).append(";base64,")
                .append(Base64.getEncoder().encodeToString(bytes)).append("\" alt=\"\"/></div>");
    }

    private Color fillColor(XSLFShape shape) {
        try {
            return shape instanceof XSLFSimpleShape simple ? simple.getFillColor() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String cssPaint(PaintStyle paint) {
        try {
            if (!(paint instanceof PaintStyle.SolidPaint solid)) {
                return null;
            }
            ColorStyle style = solid.getSolidColor();
            if (style == null || style.getColor() == null) {
                return null;
            }
            Color c = style.getColor();
            int alpha = style.getAlpha();
            double a = alpha <= 0 ? 1 : Math.min(1, alpha / 100000.0);
            return String.format(java.util.Locale.ROOT, "rgba(%d,%d,%d,%.2f)",
                    c.getRed(), c.getGreen(), c.getBlue(), a);
        } catch (Exception e) {
            return null;
        }
    }

    private String css(Color color) {
        if (color == null) {
            return null;
        }
        if (color.getAlpha() == 0) {
            return null;
        }
        return String.format("rgba(%d,%d,%d,%.2f)", color.getRed(), color.getGreen(), color.getBlue(),
                color.getAlpha() / 255.0);
    }

    private String sniff(byte[] b) {
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if (b.length > 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F') {
            return "image/gif";
        }
        if (b.length > 12 && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private String escape(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private String pt(double value) {
        return String.format(java.util.Locale.ROOT, "%.1fpt", value);
    }

    private String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.0f", value);
    }

    private String document(String title, String body) {
        return """
                <!doctype html>
                <html lang="zh-CN"><head><meta charset="utf-8"><title>%s</title>
                <style>
                :root { color-scheme: light; }
                body { margin: 0; background: #f9f9f9; color: #202020;
                       font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC",
                                    "Microsoft YaHei", sans-serif; }
                #deck { padding: 16px; display: flex; flex-direction: column; align-items: center; gap: 14px; }
                .frame { position: relative; flex: none; }
                .slide { position: relative; background: #ffffff; overflow: hidden; transform-origin: top left;
                         border: 0.5px solid #e0e0e0; box-sizing: border-box; }
                .bg { position: absolute; inset: 0; }
                .shape { position: absolute; box-sizing: border-box; }
                .tx { display: flex; flex-direction: column; padding: 3pt 7pt; overflow: hidden; }
                p { margin: 0; line-height: 1.32; word-break: break-word; }
                .bu { margin-right: 5pt; }
                .tb { width: 100%%; height: 100%%; border-collapse: collapse; table-layout: fixed; }
                .tb td { border: 0.5px solid #d9d9d9; padding: 3pt 5pt; vertical-align: top;
                         font-size: 11pt; overflow: hidden; word-break: break-word; }
                img { width: 100%%; height: 100%%; object-fit: contain; display: block; }
                .no { position: absolute; right: 6pt; bottom: 4pt; font-size: 9pt; color: #bbbbbb; }
                </style></head>
                <body><main id="deck">%s</main>
                <script>
                (function () {
                  var deck = document.getElementById('deck');
                  function fit() {
                    var avail = deck.clientWidth - 32;
                    var frames = document.querySelectorAll('.frame');
                    for (var i = 0; i < frames.length; i++) {
                      var frame = frames[i], slide = frame.firstElementChild;
                      var w = parseFloat(slide.getAttribute('data-w'));
                      var h = parseFloat(slide.getAttribute('data-h'));
                      if (!w || !h) continue;
                      var k = Math.min(1, avail / w);
                      slide.style.transform = 'scale(' + k + ')';
                      frame.style.width = Math.round(w * k) + 'px';
                      frame.style.height = Math.round(h * k) + 'px';
                    }
                  }
                  window.addEventListener('resize', fit);
                  if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', fit);
                  } else { fit(); }
                })();
                </script>
                </body></html>
                """.formatted(escape(title == null ? "幻灯片预览" : title), body);
    }
}
