package com.agentx.tools.files.render;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * 结构化行列 → xlsx（POI XSSF）。表头黑底白字加粗 + 冻结首行 + 列宽自适应；
 * 单元格能解析为数字的写数值类型（便于用户直接做公式/透视）。
 */
public final class XlsxRenderer {

    public record Sheet(String name, List<String> headers, List<List<String>> rows) {}

    public byte[] render(List<Sheet> sheets) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = headerStyle(wb);
            int index = 1;
            for (Sheet spec : sheets) {
                String name = spec.name() == null || spec.name().isBlank() ? "Sheet" + index : spec.name();
                XSSFSheet sheet = wb.createSheet(org.apache.poi.ss.util.WorkbookUtil.createSafeSheetName(name));
                int rowIdx = 0;
                List<String> headers = spec.headers() == null ? List.of() : spec.headers();
                if (!headers.isEmpty()) {
                    Row header = sheet.createRow(rowIdx++);
                    for (int c = 0; c < headers.size(); c++) {
                        Cell cell = header.createCell(c);
                        cell.setCellValue(headers.get(c));
                        cell.setCellStyle(headerStyle);
                    }
                    sheet.createFreezePane(0, 1);
                }
                for (List<String> rowData : spec.rows() == null ? List.<List<String>>of() : spec.rows()) {
                    Row row = sheet.createRow(rowIdx++);
                    for (int c = 0; c < rowData.size(); c++) {
                        writeCell(row.createCell(c), rowData.get(c));
                    }
                }
                int cols = Math.max(headers.size(),
                        spec.rows() == null || spec.rows().isEmpty() ? 0 : spec.rows().getFirst().size());
                for (int c = 0; c < cols; c++) {
                    sheet.autoSizeColumn(c);
                    sheet.setColumnWidth(c, Math.min(sheet.getColumnWidth(c) + 512, 60 * 256));
                }
                index++;
            }
            if (wb.getNumberOfSheets() == 0) {
                wb.createSheet("Sheet1");
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeCell(Cell cell, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.strip();
        if (!trimmed.isEmpty() && trimmed.matches("-?\\d+(\\.\\d+)?")) {
            try {
                cell.setCellValue(Double.parseDouble(trimmed));
                return;
            } catch (NumberFormatException ignored) {
                // 超长数字等极端情况回退为文本
            }
        }
        cell.setCellValue(value);
    }

    private CellStyle headerStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.BLACK.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setBold(true);
        style.setFont(font);
        return style;
    }
}
