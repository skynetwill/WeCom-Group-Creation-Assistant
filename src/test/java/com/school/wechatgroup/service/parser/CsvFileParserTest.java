package com.school.wechatgroup.service.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvFileParserTest {

    private final CsvFileParser parser = new CsvFileParser();

    @Test
    void shouldParseValidCsv() {
        String content = "userid\nzhangsan\nlisi\nwangwu\n";
        MultipartFile file = new MockMultipartFile("test.csv", "test.csv",
                "text/csv", content.getBytes());

        List<String> result = parser.parse(file);

        assertEquals(3, result.size());
        assertEquals("zhangsan", result.get(0));
        assertEquals("lisi", result.get(1));
        assertEquals("wangwu", result.get(2));
    }

    @Test
    void shouldHandleEmptyFile() {
        MultipartFile file = new MockMultipartFile("empty.csv", "empty.csv",
                "text/csv", "".getBytes());

        List<String> result = parser.parse(file);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldHandleHeaderOnly() {
        String content = "userid\n";
        MultipartFile file = new MockMultipartFile("header.csv", "header.csv",
                "text/csv", content.getBytes());

        List<String> result = parser.parse(file);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldHandleBlankLines() {
        String content = "userid\n\nzhangsan\n\nlisi\n\n";
        MultipartFile file = new MockMultipartFile("blanks.csv", "blanks.csv",
                "text/csv", content.getBytes());

        List<String> result = parser.parse(file);

        assertEquals(2, result.size());
        assertEquals("zhangsan", result.get(0));
        assertEquals("lisi", result.get(1));
    }

    @Test
    void shouldReturnSupportedExtension() {
        assertEquals("csv", parser.supportedExtension());
    }
}
