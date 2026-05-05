# 09 - 语义分块功能扩展

## 1. 本步骤要完成什么

基于已有的 Embedding 能力，实现语义感知的文本分块策略。语义分块通过计算文本段落之间的语义相似度，在语义变化明显的地方进行分割，从而保持每个 chunk 内部的语义一致性。

本步骤完成后，系统具备：
- 语义感知的文本分块能力（SEMANTIC_CHUNKING 模式）
- 基于 Embedding 相似度的分段点检测
- 与现有分块架构无缝集成
- 支持配置相似度阈值和块大小约束

## 2. 架构概览

```
┌─────────────────────────────────────────────────────────────────┐
│  ChunkingStrategyFactory                                        │
│    ├─ FixedSizeTextChunker                                      │
│    ├─ StructureAwareTextChunker                                 │
│    ├─ RecursiveCharacterTextChunker                             │
│    ├─ QaPairTextChunker                                         │
│    ├─ TableAwareTextChunker                                     │
│    └─ SemanticTextChunker (新增)                                 │
├─────────────────────────────────────────────────────────────────┤
│  SemanticTextChunker                                            │
│    ├─ 输入: 文本 + SemanticOptions                              │
│    ├─ 步骤 1: 按句子/段落初步分割                                │
│    ├─ 步骤 2: 生成每个段落的 Embedding                          │
│    ├─ 步骤 3: 计算相邻段落的余弦相似度                           │
│    ├─ 步骤 4: 在相似度低于阈值处分割                            │
│    └─ 输出: List<VectorChunk>                                  │
├─────────────────────────────────────────────────────────────────┤
│  EmbeddingService (已有)                                        │
│    └─ RoutingEmbeddingService.embed()                          │
└─────────────────────────────────────────────────────────────────┘
```

语义分块算法流程：

```
原始文本
    ↓
初步分割（按句子/段落）
    ↓
生成每个段落的 Embedding
    ↓
计算相邻段落余弦相似度
    ↓
识别相似度低谷点（语义边界）
    ↓
在语义边界处分割，合并小块
    ↓
输出语义一致的 Chunk 列表
```

## 3. 分步实现提示词

> **使用方式**：按顺序将每一步的提示词发给 AI，每步验证通过后再进入下一步。每步提示词都自包含上下文，不依赖 ragent 源码。

---

### 第 1 步：语义分块配置类

#### 目标

定义语义分块的配置结构，扩展 ChunkingOptions 体系。

#### 提示词

```text
请为 DevBrain-CQUPT 项目添加语义分块的配置类。

项目背景：DevBrain 是一个研发知识库系统，已有分块策略包括固定大小、结构感知、递归字符、问答对、表格感知。现在需要扩展语义分块能力，利用 Embedding 计算文本段落之间的语义相似度，在语义变化处进行分块。

已有类型（在 core/chunk 包下）：
- ChunkingOptions 接口：sealed interface，已有实现 FixedSizeOptions、TextBoundaryOptions、RecursiveOptions、QaPairOptions
- ChunkingMode 枚举：定义分块模式，需要新增 SEMANTIC_CHUNKING

请创建以下类：

1. SemanticOptions 配置类（core/chunk 包下）：
   - 实现 ChunkingOptions 接口
   - int chunkSize -- 目标块大小（字符数），默认 512
   - int overlapSize -- 重叠大小（字符数），默认 50
   - double similarityThreshold -- 相似度阈值，低于此值认为是语义边界，默认 0.5
   - int minChunkSize -- 最小块大小（字符数），默认 100
   - int maxChunkSize -- 最大块大小（字符数），默认 1024
   - int batchSize -- Embedding 批处理大小，默认 10
   - 实现 toConfigMap() 方法

2. 修改 ChunkingMode 枚举：
   - 新增 SEMANTIC_CHUNKING("semantic_chunking", "语义分块") 枚举值
   - 在 createOptions() 方法中添加对应的 case 分支
   - 在 createDefaultOptions() 方法中添加对应的 case 分支

3. 在 application.yaml 中添加语义分块配置示例：
   chunking:
     semantic:
       chunk-size: 512
       overlap-size: 50
       similarity-threshold: 0.5
       min-chunk-size: 100
       max-chunk-size: 1024
       batch-size: 10

请生成完整的 Java 代码。注意保持与现有配置类一致的风格。
```

#### 验证方式

编译通过，SemanticOptions 可以正常创建和序列化。

---

### 第 2 步：语义分块策略实现

#### 目标

实现基于 Embedding 相似度的语义分块策略。

#### 提示词

```text
请为 DevBrain-CQUPT 项目实现语义分块策略。

项目背景：DevBrain 需要基于语义相似度进行文本分块。核心思想是：将文本按句子初步分割，计算相邻句子的 Embedding 相似度，在相似度低的地方（语义边界）进行分割。

已有类型（在 core/chunk 包下）：
- ChunkingStrategy 接口：定义了 getType() 和 chunk(text, config) 方法
- ChunkingMode 枚举：包含 SEMANTIC_CHUNKING 枚举值
- SemanticOptions 配置类：包含 chunkSize、overlapSize、similarityThreshold、minChunkSize、maxChunkSize、batchSize
- VectorChunk 类：包含 chunkId、index、content、metadata 字段

已有服务（在 infra/embedding 包下）：
- EmbeddingService 接口：embed(String text) 返回 List<Float>，embedBatch(List<String> texts) 返回 List<List<Float>>

请创建以下类：

1. SemanticTextChunker 实现类（core/chunk/strategy 包下）：
   - @Component 注解
   - 实现 ChunkingStrategy 接口
   - 注入 EmbeddingService

   核心算法 chunk(text, config)：
   a. 解析配置：将 ChunkingOptions 转为 SemanticOptions
   b. 初步分割：将文本按句子分割（按句号、问号、感叹号、换行符等）
   c. 生成 Embedding：批量调用 embeddingService.embedBatch(sentences)
   d. 计算相似度：计算相邻句子的余弦相似度
   e. 识别分割点：相似度低于阈值的位置为分割点
   f. 合并小块：将连续的句子按分割点分组，同时满足 minChunkSize 和 maxChunkSize 约束
   g. 添加重叠：在相邻 chunk 之间添加 overlapSize 个字符的重叠
   h. 物化为 VectorChunk 列表

2. 辅助方法：
   - splitIntoSentences(String text): 按句子分割文本
   - cosineSimilarity(List<Float> a, List<Float> b): 计算两个向量的余弦相似度
   - mergeChunks(List<String> sentences, List<Integer> splitPoints, SemanticOptions options): 按分割点合并句子

3. 句子分割规则：
   - 按中文句号（。）、问号（？）、感叹号（！）分割
   - 按英文句号（.）、问号（?）、感叹号（!）分割
   - 按换行符（\n）分割
   - 保持分割符在句子末尾
   - 过滤空白句子

请生成完整的 Java 代码。关键点：
- 句子分割要准确，不要误分割
- 相似度计算要高效，避免重复计算
- 合并逻辑要满足大小约束，避免过小或过大的块
```

#### 验证方式

编写单元测试：
- 测试句子分割的准确性
- 测试相似度计算的正确性
- 测试分块结果满足大小约束
- 测试语义相关的文本被分到同一个 chunk

---

### 第 3 步：语义分块单元测试

#### 目标

编写语义分块策略的单元测试，验证算法正确性。

#### 提示词

```text
请为 DevBrain-CQUPT 项目编写语义分块策略的单元测试。

项目背景：DevBrain 的 SemanticTextChunker 实现了基于 Embedding 相似度的语义分块。需要编写测试验证算法的正确性。

已有类型（在 core/chunk 包下）：
- SemanticTextChunker：实现 ChunkingStrategy 接口
- SemanticOptions：配置类，包含 chunkSize、overlapSize、similarityThreshold、minChunkSize、maxChunkSize、batchSize
- VectorChunk：包含 chunkId、index、content 字段

已有服务（在 infra/embedding 包下）：
- EmbeddingService 接口：embed(String text) 返回 List<Float>，embedBatch(List<String> texts) 返回 List<List<Float>>

请在 test 目录下创建以下测试类：

1. SemanticTextChunkerTest 测试类：
   - 使用 @SpringBootTest 或 @ExtendWith(MockitoExtension.class)
   - Mock EmbeddingService

   测试用例：
   a. shouldSplitBySemanticBoundary：
      - 输入文本包含两个语义不同的段落（如前半段讲技术，后半段讲业务）
      - Mock EmbeddingService 返回不同的向量
      - 验证分块结果在语义边界处分割

   b. shouldMergeSmallChunks：
      - 输入文本包含多个短句子
      - Mock EmbeddingService 返回相似的向量
      - 验证短句子被合并到一个 chunk

   c. shouldRespectMaxChunkSize：
      - 输入一个超长文本
      - 验证每个 chunk 的长度不超过 maxChunkSize

   d. shouldAddOverlap：
      - 配置 overlapSize > 0
      - 验证相邻 chunk 之间有重叠内容

   e. shouldHandleEmptyText：
      - 输入空文本
      - 验证返回空列表

   f. shouldHandleSingleSentence：
      - 输入单个句子
      - 验证返回一个 chunk

2. 测试数据准备：
   - 准备中文技术文档文本
   - 准备不同语义段落的文本
   - 准备 Mock 向量数据

请生成完整的 Java 测试代码。使用 JUnit 5 + Mockito。
```

#### 验证方式

运行测试，所有测试用例通过。

---

### 第 4 步：集成到前端配置

#### 目标

将语义分块选项集成到前端界面，让用户可以选择使用。

#### 提示词

```text
请为 DevBrain-CQUPT 项目将语义分块选项集成到前端界面。

项目背景：DevBrain 的前端已经支持选择分块模式（固定大小、结构感知等），现在需要添加语义分块选项。

已有前端代码：
- 分块模式选择组件
- 分块配置表单组件

请修改以下文件：

1. 分块模式枚举/常量：
   - 添加 SEMANTIC_CHUNKING 选项
   - 中文标签：语义分块
   - 描述：基于文本语义相似度进行智能分块，保持语义完整性

2. 分块配置表单：
   - 当选择语义分块时，显示以下配置项：
     * 目标块大小（chunkSize）：滑块或输入框，默认 512
     * 重叠大小（overlapSize）：滑块或输入框，默认 50
     * 相似度阈值（similarityThreshold）：滑块，范围 0.3-0.8，默认 0.5
     * 最小块大小（minChunkSize）：输入框，默认 100
     * 最大块大小（maxChunkSize）：输入框，默认 1024

3. 配置说明：
   - 添加工具提示，解释每个参数的含义
   - 相似度阈值说明：值越低，分割越细；值越高，块越大

请生成前端代码修改建议。如果前端是 React/Vue，请提供相应的组件代码。
```

#### 验证方式

前端界面可以正常显示语义分块选项，配置项可以正常修改和保存。

---

### 第 5 步：端到端测试

#### 目标

验证语义分块与现有系统的完整集成。

#### 提示词

```text
请为 DevBrain-CQUPT 项目编写语义分块的端到端集成测试。

项目背景：DevBrain 的语义分块功能需要与现有系统完整集成，包括 Embedding 服务、向量存储、检索等环节。

已有类型：
- SemanticTextChunker：语义分块策略
- EmbeddingService：Embedding 服务
- VectorStoreService：向量存储服务
- RetrieverService：向量检索服务
- VectorChunk：向量块
- RetrievedChunk：检索结果

请在 test 目录下创建以下测试类：

1. SemanticChunkingIntegrationTest 集成测试：
   - @SpringBootTest 注解
   - 注入 SemanticTextChunker、EmbeddingService、VectorStoreService、RetrieverService

   测试用例：
   a. shouldChunkAndIndexSemanticDocuments：
      - 准备一篇中文技术文档（包含多个语义段落）
      - 使用 SemanticTextChunker 进行分块
      - 将分块结果写入向量库
      - 验证写入成功

   b. shouldRetrieveSemanticSimilarChunks：
      - 使用语义分块处理文档
      - 写入向量库
      - 使用语义相关的问题进行检索
      - 验证返回的 chunk 语义相关

   c. shouldCompareWithOtherChunkingMethods：
      - 分别使用语义分块和固定大小分块处理同一文档
      - 检索相同问题
      - 比较两种方法的检索效果

2. 测试数据：
   - 准备一篇包含以下内容的文档：
     * 技术架构介绍段落
     * 数据库设计段落
     * API 接口说明段落
     * 部署流程段落
   - 准备语义相关的问题：
     * "系统使用了什么技术栈？"
     * "数据库表结构是怎样的？"
     * "如何部署系统？"

请生成完整的 Java 测试代码。测试需要真实的 Embedding 服务和数据库连接。
```

#### 验证方式

运行集成测试，验证语义分块功能与现有系统正常集成。

---

## 4. 实现步骤总览

| 步骤 | 内容 | 前置依赖 | 验证方式 |
|------|------|----------|----------|
| 1 | 语义分块配置类 | 无 | 编译通过 |
| 2 | 语义分块策略实现 | 步骤 1 | 单元测试通过 |
| 3 | 语义分块单元测试 | 步骤 2 | 测试通过 |
| 4 | 前端配置集成 | 步骤 1 | 界面正常显示 |
| 5 | 端到端测试 | 步骤 2, 3 | 集成测试通过 |

## 5. 技术选型说明

### 语义分块算法

| 维度 | 语义分块 | 固定大小分块 | 结构感知分块 |
|------|----------|--------------|--------------|
| 原理 | 基于 Embedding 相似度 | 固定字符数 | Markdown 结构边界 |
| 优点 | 保持语义完整性 | 实现简单，速度快 | 适合结构化文档 |
| 缺点 | 需要调用 Embedding 服务 | 可能切断语义 | 依赖文档结构 |
| 适用场景 | 通用文档，特别是非结构化文本 | 快速处理，对质量要求不高 | Markdown、技术文档 |

### 相似度阈值选择

| 阈值范围 | 效果 | 适用场景 |
|----------|------|----------|
| 0.3-0.4 | 分割较细，块较小 | 需要精确检索的场景 |
| 0.4-0.6 | 平衡分割 | 通用场景（推荐） |
| 0.6-0.8 | 分割较粗，块较大 | 需要完整上下文的场景 |

## 6. 关键配置项

```yaml
# application.yaml 关键配置

chunking:
  semantic:
    chunk-size: 512           # 目标块大小（字符数）
    overlap-size: 50          # 重叠大小（字符数）
    similarity-threshold: 0.5 # 相似度阈值，低于此值认为是语义边界
    min-chunk-size: 100       # 最小块大小（字符数）
    max-chunk-size: 1024      # 最大块大小（字符数）
    batch-size: 10            # Embedding 批处理大小
```

## 7. 验收标准

- [ ] 语义分块策略实现完成
- [ ] 支持配置相似度阈值、块大小等参数
- [ ] 与现有分块架构无缝集成
- [ ] 前端界面可以正常选择语义分块模式
- [ ] 前端界面可以配置语义分块参数
- [ ] 单元测试通过
- [ ] 集成测试通过
- [ ] 语义相关的文本被分到同一个 chunk
- [ ] 不同语义的文本在边界处分割
- [ ] 分块结果满足大小约束
- [ ] 相邻 chunk 之间有适当的重叠
- [ ] 性能满足要求（Embedding 调用次数合理）

## 8. 注意事项

### 性能优化

1. **批量 Embedding**：使用 embedBatch 一次处理多个句子，减少 HTTP 调用次数
2. **缓存 Embedding**：对相同文本的 Embedding 结果进行缓存
3. **异步处理**：对于大文档，可以考虑异步分块

### 边界情况处理

1. **短文本**：当文本长度小于 minChunkSize 时，直接返回单个 chunk
2. **长句子**：当单个句子超过 maxChunkSize 时，按标点符号进一步分割
3. **无标点文本**：当文本没有明显的句子分隔符时，按固定长度分割

### 错误处理

1. **Embedding 服务不可用**：降级到固定大小分块
2. **向量维度不匹配**：检查配置的维度与模型输出维度是否一致
3. **相似度计算异常**：记录日志，使用默认分割点

## 9. 后续扩展

### 多层级语义分块

可以实现多层级的语义分块：
1. 第一层：按大主题分割（相似度阈值较低）
2. 第二层：在每个主题内按细节分割（相似度阈值较高）

### 自适应阈值

根据文本特征自动调整相似度阈值：
- 文本越长，阈值越低
- 文本越复杂，阈值越低

### 混合分块策略

结合多种分块策略的优点：
1. 先用结构感知分块处理 Markdown 文档
2. 再用语义分块处理每个结构块内部的文本
