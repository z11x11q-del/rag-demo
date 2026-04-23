package com.example.ragdemo.datasource;

import com.example.ragdemo.model.enums.SourceType;

import java.io.InputStream;

/**
 * 数据源抽象接口 — 支持插件化扩展
 */
public interface DataSource {

    /**
     * 该数据源支持的类型
     */
    SourceType sourceType();

    /**
     * 读取原始数据流
     *
     * @param sourcePath 源路径（文件路径 / URL / 表名等）
     * @return 原始数据流
     */
    InputStream fetch(String sourcePath);

    /**
     * 计算源数据的内容哈希（SHA256），用于去重和变更检测
     *
     * @param sourcePath 源路径
     * @return SHA256 哈希值
     */
    String computeContentHash(String sourcePath);
}
