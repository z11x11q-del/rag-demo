# 项目编码规范

## 1. 依赖注入

- **必须**使用 Lombok `@RequiredArgsConstructor` + `private final` 字段实现构造器注入
- **禁止**使用 `@Autowired`、`@Resource` 字段注入
- **禁止**手写构造函数进行依赖注入（由 Lombok 自动生成）

```java
// ✅ 正确写法
@Service
@RequiredArgsConstructor
public class XxxService {
    private final FooRepository fooRepository;
    private final BarClient barClient;
}

// ❌ 错误写法
@Service
public class XxxService {
    @Autowired
    private FooRepository fooRepository;
}
```

## 2. 日志

- **必须**使用 Lombok `@Slf4j` 注解自动生成 `log` 字段
- **禁止**手写 `private static final Logger log = LoggerFactory.getLogger(...)` 

```java
// ✅ 正确写法
@Slf4j
@Service
public class XxxService {
    // 直接使用 log.info(...) / log.error(...)
}

// ❌ 错误写法
@Service
public class XxxService {
    private static final Logger log = LoggerFactory.getLogger(XxxService.class);
}
```

## 3. 方法体量控制

### 3.1 行数上限

- 单个方法（不含空行和注释）**不得超过 20 行有效代码**；超过时必须拆分
- 含空行和注释在内，单方法总行数**建议不超过 30 行**
- 若方法体超过上限，应立即进行**方法抽取（Extract Method）**

### 3.2 拆分原则

- **单一职责**：一个方法只做一件事。如果需要用注释分隔"阶段"或"步骤"，说明该方法应该被拆分
- **同一抽象层级**：方法体内的语句应处于同一抽象层级；高层编排方法只调用子步骤方法，不混入底层细节
- **命名即文档**：抽取出的方法用动词短语命名（如 `doParse`、`writeDenseIndex`、`resolveChunkStatus`），使调用处无需注释即可自解释

### 3.3 常见拆分手法

| 场景 | 手法 | 示例 |
|---|---|---|
| 方法中有多个顺序步骤 | 编排方法 + 各步骤子方法 | `run()` 只列步骤调用，每步一个 `doXxx()` |
| try-catch 块体量大 | 将 try 体抽取为独立方法，返回成功/失败 | `boolean writeDenseIndex(...)` |
| 条件分支逻辑复杂 | 将各分支抽取为方法，或使用策略模式 | `resolveChunkStatus(chunks)` |
| 循环体内逻辑超过 5 行 | 将循环体抽取为方法或用 Stream + map/forEach | `chunks.stream().map(this::toChunkEntity)` |
| 对象构建字段多（>5 个 setter） | 抽取为工厂方法或 Builder | `buildChunkEntity(documentId, textChunk)` |

### 3.4 编排方法模式

对于流水线、工作流等多步骤场景，**必须**采用"编排方法"模式：

```java
// ✅ 正确写法：编排方法精简，一目了然
public void run(Task task, InputStream input, String fileName) {
    try {
        markRunning(task);
        ParsedDocument parsed = doParse(task, input, fileName);
        StructuredDocument structured = doStructure(task, parsed);
        List<Chunk> chunks = doChunk(task, structured);
        List<float[]> vectors = doEmbed(task, chunks);
        boolean indexOk = doIndex(task, chunks, vectors);
        finishPipeline(task, chunks, indexOk);
    } catch (Exception e) {
        handleFailure(task, e);
    }
}

// ❌ 错误写法：所有逻辑平铺在一个 200 行的大方法中
```

### 3.5 检查清单

编写或审查方法时，逐项检查：

1. 方法有效代码是否超过 20 行？→ 拆分
2. 方法内是否存在用注释分隔的"区块"？→ 每个区块抽取为独立方法
3. 嵌套层级是否超过 2 层（方法体 > if/for > 内层）？→ 提前 return 或抽取内层
4. 方法参数是否超过 4 个？→ 考虑封装为参数对象

## 4. 注解顺序

类上的注解按以下顺序排列：
1. Lombok 注解（`@Slf4j`、`@RequiredArgsConstructor`、`@Data` 等）
2. Spring 组件注解（`@Service`、`@Component`、`@RestController` 等）
3. Spring 配置注解（`@RequestMapping` 等）

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class XxxService { ... }

@RestController
@RequestMapping("/api/xxx")
@RequiredArgsConstructor
public class XxxController { ... }
```
