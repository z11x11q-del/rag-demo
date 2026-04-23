---
name: git-review-commit-push
description: 审查未提交的代码变更，生成 Conventional Commits 格式的提交信息，并执行 git commit 和 push。当用户要求提交代码、审查变更或推送到远程仓库时使用。
---

# Git 审查、提交与推送

## 工作流程

### 第一步：检查未提交的变更

获取当前变更的全貌：

```bash
git status
git diff --cached          # 已暂存的变更
git diff                   # 未暂存的变更
```

### 第二步：代码审查

使用以下清单审查所有未提交的变更：

- [ ] **正确性**：无语法错误、逻辑缺陷或明显 bug
- [ ] **风格**：符合项目编码规范（命名、格式）
- [ ] **死代码**：无未使用的变量、import 或调试语句
- [ ] **调试输出**：无残留的 `console.log`、`print` 或调试输出
- [ ] **完整性**：变更聚焦且完整，无意外修改

如果发现问题，报告问题并停止。询问用户是修复还是继续。

### 第三步：生成 Commit Message

分析 diff 并生成 **Conventional Commits** 格式的提交信息：

```
<type>(<scope>): <简要描述>

<正文>
```

| 类型 | 使用场景 |
|------|----------|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `docs` | 仅文档变更 |
| `style` | 格式化、分号、空格等（无逻辑变更） |
| `refactor` | 既非修复 bug 也非新增功能的代码变更 |
| `perf` | 性能优化 |
| `test` | 添加或修正测试 |
| `chore` | 构建流程、依赖、工具链 |

规则：
- 描述：祈使语气，冒号后小写，无句号，最多 50 字符
- 正文：说明做了什么以及为什么，每行不超过 72 字符，描述与正文之间空一行
- 如果包含多个逻辑变更，建议拆分为多次提交

### 第四步：暂存、提交并推送

如果第二步未发现问题，**无需向用户确认**，直接执行：

```bash
git add <files>   # 暂存指定文件，如果所有变更都是预期的则用 -A
git commit -m "<type>(<scope>): <描述>" -m "<正文>"
git push origin <当前分支>
```

如果远程尚不存在该分支：

```bash
git push -u origin <当前分支>
```

**不要在 commit 或 push 前询问用户确认。** 审查通过后直接执行。

## 示例

**示例 1 — 简单修复：**
```
fix(auth): handle null token in middleware

Add early return when authorization header is missing
instead of throwing an unhandled exception.
```

**示例 2 — 新功能：**
```
feat(api): add pagination to user list endpoint

Implement cursor-based pagination to improve performance
on large user datasets. Default page size is 20.
```

**示例 3 — 仅格式调整：**
```
style(components): reformat button variants with prettier
```
