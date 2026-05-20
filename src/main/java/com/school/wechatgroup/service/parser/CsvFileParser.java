package com.school.wechatgroup.service.parser;

import com.opencsv.CSVReader;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component("csvParser")
public class CsvFileParser implements FileParserStrategy {

    @Override
    public List<String> parse(MultipartFile file) {
        List<String> userList = new ArrayList<>();

        try (CSVReader csvReader = new CSVReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String[] values;
            while ((values = csvReader.readNext()) != null) {
                if (values.length > 0 && !values[0].trim().isEmpty()) {
                    if (!values[0].equalsIgnoreCase("userid")) {
                        userList.add(values[0].trim());
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("CSV文件解析失败: " + e.getMessage(), e);
        }

        return userList;
    }

    @Override
    public String supportedExtension() {
        return "csv";
    }
}
