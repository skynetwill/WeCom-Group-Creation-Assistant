package com.school.wechatgroup.service.parser;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExcelFileParserTest {

    private final ExcelFileParser parser = new ExcelFileParser();

    @Test
    void shouldParseValidXlsx() throws Exception {
        byte[] excelBytes = createXlsxBytes(new String[]{"userid", "zhangsan", "lisi", "wangwu"});
        MultipartFile file = new MockMultipartFile("test.xlsx", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        List<String> result = parser.parse(file);

        assertEquals(3, result.size());
        assertEquals("zhangsan", result.get(0));
        assertEquals("lisi", result.get(1));
        assertEquals("wangwu", result.get(2));
    }

    @Test
    void shouldParseValidXls() throws Exception {
        byte[] excelBytes = createXlsBytes(new String[]{"userid", "zhangsan", "lisi"});
        MultipartFile file = new MockMultipartFile("test.xls", "test.xls",
                "application/vnd.ms-excel", excelBytes);

        List<String> result = parser.parse(file);

        assertEquals(2, result.size());
        assertEquals("zhangsan", result.get(0));
        assertEquals("lisi", result.get(1));
    }

    @Test
    void shouldHandleEmptySheet() throws Exception {
        byte[] excelBytes = createXlsxBytes(new String[]{});
        MultipartFile file = new MockMultipartFile("empty.xlsx", "empty.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        List<String> result = parser.parse(file);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldHandleHeaderOnly() throws Exception {
        byte[] excelBytes = createXlsxBytes(new String[]{"userid"});
        MultipartFile file = new MockMultipartFile("header.xlsx", "header.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        List<String> result = parser.parse(file);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldHandleNumericCells() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("userid");
            Row dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue(1001);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);

            MultipartFile file = new MockMultipartFile("num.xlsx", "num.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    bos.toByteArray());

            List<String> result = parser.parse(file);

            assertEquals(1, result.size());
            assertEquals("1001", result.get(0));
        }
    }

    @Test
    void shouldReturnSupportedExtension() {
        assertEquals("xlsx", parser.supportedExtension());
    }

    private byte[] createXlsxBytes(String[] values) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            for (int i = 0; i < values.length; i++) {
                Row row = sheet.createRow(i);
                row.createCell(0).setCellValue(values[i]);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    private byte[] createXlsBytes(String[] values) throws Exception {
        try (Workbook workbook = new HSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            for (int i = 0; i < values.length; i++) {
                Row row = sheet.createRow(i);
                row.createCell(0).setCellValue(values[i]);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        }
    }
}
