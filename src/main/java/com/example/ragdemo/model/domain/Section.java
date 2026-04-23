package com.example.ragdemo.model.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档中的一个章节 — 结构化处理层输出的组成部分
 */
public class Section {

    /** 章节标题 */
    private String title;
    /** 标题层级（1=一级标题，2=二级标题…） */
    private int level;
    /** 章节内容 */
    private String content;
    /** 子章节 */
    private List<Section> children = new ArrayList<>();

    public Section() {}

    public Section(String title, int level, String content) {
        this.title = title;
        this.level = level;
        this.content = content;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<Section> getChildren() { return children; }
    public void setChildren(List<Section> children) { this.children = children; }
}
