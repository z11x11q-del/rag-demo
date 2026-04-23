# Prompt 构建模块详细设计（Prompt Builder）

## 1. 模块职责

将系统指令（System Prompt）、检索上下文（Context）、用户查询（User Query）组装为发送给 LLM 的完整 Prompt，并根据用户意图选择合适的模板。位于上下文构造之后、LLM 调用之前。

---

## 2. 接口定义

### 2.1 PromptBuilder 接口

```java
package com.example.ragdemo.retrieval;

public interface PromptBuilder {

    /**
     * 构建最终发送给 LLM 的 Prompt
     *
     * @param context 检索上下文（ContextBuilder 输出）
     * @param query   用户查询（改写后）
     * @param intent  用户意图（qa / summary / comparison / how_to）
     * @return 完整 Prompt 文本
     */
    String build(String context, String query, String intent);
}
```

### 2.2 输入/输出说明

| 方向 | 类型 | 说明 |
|------|------|------|
| 输入 context | `String` | ContextBuilder 输出的格式化上下文（含编号和来源） |
| 输入 query | `String` | 用户查询（经过预处理改写后的版本） |
| 输入 intent | `String` | 查询意图标签，决定使用哪个 Prompt 模板 |
| 输出 | `String` | 完整 Prompt 文本，直接传给 `LlmClient.chat()` |

---

## 3. 详细设计

### 3.1 Prompt 结构

完整 Prompt 由三部分拼接而成：

```
┌─────────────────────────────────────────┐
│  System Prompt（系统指令）                │
│  定义模型角色、回答风格、约束条件          │
├─────────────────────────────────────────┤
│  Context（检索上下文）                    │
│  [1] 来源：... 内容：...                 │
│  [2] 来源：... 内容：...                 │
├─────────────────────────────────────────┤
│  User Query（用户问题）                   │
│  请基于以上参考文档回答：{query}           │
└─────────────────────────────────────────┘
```

### 3.2 System Prompt 模板

按 intent 维护不同的系统指令模板：

#### QA 模板（默认）

```
你是一个专业的知识库问答助手。请严格基于以下参考文档回答用户问题。

规则：
1. 只使用参考文档中的信息回答，不要编造内容
2. 如果参考文档中没有相关信息，请明确告知"根据已有资料，暂时无法回答该问题"
3. 在回答中引用来源，使用 [编号] 格式标注（如 [1]、[2]）
4. 回答应简洁、准确、有条理
```

#### Summary 模板

```
你是一个专业的文档总结助手。请基于以下参考文档，对相关内容进行归纳总结。

规则：
1. 只使用参考文档中的信息，不要编造内容
2. 按要点分条列出，突出关键信息
3. 在总结中标注信息来源 [编号]
4. 总结应全面但不冗长
```

#### Comparison 模板

```
你是一个专业的分析助手。请基于以下参考文档，对用户提问中的对象进行对比分析。

规则：
1. 只使用参考文档中的信息，不要编造内容
2. 使用表格或分项对比的形式呈现
3. 标注信息来源 [编号]
4. 如果文档中缺少某一方的信息，请明确指出
```

#### How-to 模板

```
你是一个专业的操作指南助手。请基于以下参考文档，为用户提供操作步骤。

规则：
1. 只使用参考文档中的信息，不要编造内容
2. 按步骤编号列出操作流程
3. 标注信息来源 [编号]
4. 如果步骤不完整，请明确告知
```

### 3.3 Prompt 组装逻辑

```java
public String build(String context, String query, String intent) {
    String systemPrompt = getSystemPrompt(intent);

    StringBuilder prompt = new StringBuilder();

    // 系统指令
    prompt.append(systemPrompt).append("\n\n");

    // 参考文档
    prompt.append("## 参考文档\n\n");
    prompt.append(context).append("\n\n");

    // 用户问题
    prompt.append("## 用户问题\n\n");
    prompt.append(query);

    return prompt.toString();
}
```

### 3.4 Token 预算分配

LLM 上下文窗口有限，需合理分配 Token 预算：

| 部分 | 预估 Token | 说明 |
|------|-----------|------|
| System Prompt | ~200 | 固定模板，长度可控 |
| Context | ~3000 | 由 ContextBuilder 控制截断 |
| User Query | ~100 | 用户查询一般较短 |
| **预留生成空间** | **~2000** | LLM 回答的 max_tokens |
| **总计** | **~5300** | 需小于模型上下文窗口 |

以通义千问 qwen-plus 为例，上下文窗口为 128K tokens，上述预算非常充裕。对于窗口较小的模型，需相应缩减 Context 预算。

约束公式：`SystemPrompt + Context + Query + MaxOutputTokens < ModelContextWindow`

---

## 4. MVP 实现方案

### 4.1 实现类：DefaultPromptBuilder

MVP 阶段使用硬编码模板，仅支持 QA 意图：

```java
@Slf4j
@RequiredArgsConstructor
@Component
public class DefaultPromptBuilder implements PromptBuilder {

    private static final String QA_SYSTEM_PROMPT = """
            你是一个专业的知识库问答助手。请严格基于以下参考文档回答用户问题。

            规则：
            1. 只使用参考文档中的信息回答，不要编造内容
            2. 如果参考文档中没有相关信息，请明确告知"根据已有资料，暂时无法回答该问题"
            3. 在回答中引用来源，使用 [编号] 格式标注（如 [1]、[2]）
            4. 回答应简洁、准确、有条理
            """;

    @Override
    public String build(String context, String query, String intent) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(QA_SYSTEM_PROMPT).append("\n\n");
        prompt.append("## 参考文档\n\n").append(context).append("\n\n");
        prompt.append("## 用户问题\n\n").append(query);

        log.debug("Prompt 构建完成: intent={}, promptLength={}",
                  intent, prompt.length());
        return prompt.toString();
    }
}
```

### 4.2 替换 Stub

实现类注册为 Spring Bean 后，`StubBeanConfig` 中的 stub（直接返回 query）将自动退让。

---

## 5. 配置项

```yaml
rag:
  prompt:
    default-intent: qa                    # 默认意图
    max-output-tokens: 2000              # LLM 生成的最大 token 数
    template-source: embedded            # 模板来源：embedded（代码内嵌）/ file（外部文件）
    template-dir: classpath:prompts/     # 外部模板文件目录（V2 启用）
    anti-hallucination: true             # 是否注入防幻觉指令
```

---

## 6. 异常处理

| 异常场景 | 处理策略 |
|---------|---------|
| context 为空字符串 | 正常构建 Prompt，System Prompt 中已有"无法回答"的兜底指令 |
| intent 无对应模板 | 降级使用 QA 模板 + warn 日志 |
| 组装后 Prompt 超长 | 记录 warn 日志（实际截断由 ContextBuilder 的 token-budget 控制） |

---

## 7. 演进规划

| 阶段 | 能力 | 说明 |
|------|------|------|
| MVP | 硬编码 QA 模板 | 单一模板，跑通链路 |
| V1 | 多意图模板 | 支持 qa / summary / comparison / how_to 四种模板 |
| V2 | 外部模板文件 | 模板从 classpath 文件加载，无需改代码即可调整 |
| V2+ | 模板变量 | 支持 `{context}`、`{query}`、`{date}` 等变量占位符替换 |
| V3 | Chat 模式 | 支持多轮对话历史拼接到 Prompt 中 |
| V3+ | 动态预算 | 根据模型上下文窗口动态计算 Context Token 预算 |
