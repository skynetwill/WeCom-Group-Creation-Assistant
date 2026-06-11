package com.school.wechatgroup.service.parser;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件解析策略接口：支持多种文件格式解析为userid列表
 */
public interface FileParserStrategy {

    /**
     * 解析上传文件，提取userid列表
     */
    List<String> parse(MultipartFile file);

    /**
     * 返回支持的文件扩展名（不含点号），如 "csv", "xlsx"
     */
    String supportedExtension();
}
