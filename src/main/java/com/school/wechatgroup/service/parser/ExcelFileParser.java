package com.school.wechatgroup.service.parser;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Component("excelParser")
public class ExcelFileParser implements FileParserStrategy {

    @Override
    public List<String> parse(MultipartFile file) {
        List<String> userList = new ArrayList<>();

        try (Workbook workbook = createWorkbook(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return userList;
            }

            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                Cell firstCell = row.getCell(0);
                if (firstCell == null) {
                    continue;
                }

                String value = getCellValueAsString(firstCell).trim();
                if (value.isEmpty()) {
                    continue;
                }

                // 跳过表头行
                if (value.equalsIgnoreCase("userid")) {
                    continue;
                }

                userList.add(value);
            }
        } catch (Exception e) {
            throw new RuntimeException("Excel文件解析失败: " + e.getMessage(), e);
        }

        return userList;
    }

    @Override
    public String supportedExtension() {
        return "xlsx";
    }

    private Workbook createWorkbook(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".xls")) {
            return new HSSFWorkbook(file.getInputStream());
        }
        return new XSSFWorkbook(file.getInputStream());
    }

    private String getCellValueAsString(Cell cell) {
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                double num = cell.getNumericCellValue();
                if (num == Math.floor(num) && !Double.isInfinite(num)) {
                    return String.valueOf((long) num);
                }
                return String.valueOf(num);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }
}
