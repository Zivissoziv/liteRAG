package com.literag.parser;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * .xlsx / .xls 解析：按工作表 + 行的可读文本形式输出，便于语义检索。
 */
@Component
public class XlsxParser implements DocumentParser {

    @Override
    public boolean supports(String ext) {
        return "xlsx".equalsIgnoreCase(ext) || "xls".equalsIgnoreCase(ext) || "csv".equalsIgnoreCase(ext);
    }

    @Override
    public String parse(Path file) throws Exception {
        if ("csv".equalsIgnoreCase(extension(file))) {
            return java.nio.file.Files.readString(file);
        }
        StringBuilder sb = new StringBuilder();
        DataFormatter formatter = new DataFormatter();
        try (Workbook wb = WorkbookFactory.create(file.toFile())) {
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                sb.append("## 工作表：").append(sheet.getSheetName()).append("\n\n");
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        String value = formatter.formatCellValue(cell);
                        if (value == null) {
                            value = "";
                        }
                        value = value.replace("\n", " ").trim();
                        if (value.length() > 500) {
                            value = value.substring(0, 500);
                        }
                        cells.add(value);
                    }
                    while (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) {
                        cells.remove(cells.size() - 1);
                    }
                    if (cells.isEmpty()) {
                        continue;
                    }
                    sb.append(String.join(" | ", cells)).append('\n');
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String extension(Path file) {
        String name = file.getFileName().toString();
        int idx = name.lastIndexOf('.');
        return idx < 0 ? "" : name.substring(idx + 1);
    }
}
