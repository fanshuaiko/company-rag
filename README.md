# Company RAG

基于 Java 25、Spring Boot 4.1.1、Spring AI 2.0.0、DeepSeek 和 PostgreSQL/pgvector 的企业知识库 RAG 项目。

## 项目链路

```text
PDF / Word / Markdown / TXT / HTML
              ↓
       Apache Tika 文档解析
              ↓
         TokenTextSplitter
              ↓
   OpenAI 兼容 Embedding API
              ↓
        PostgreSQL + pgvector
              ↓
       Top-K 向量相似度检索
              ↓
      DeepSeek + 检索上下文
              ↓
         答案 + 引用片段
```

## 配置与启动

需要 Java 25、Docker 和 Maven Wrapper。当前使用 DeepSeek 生成答案，并使用 OpenAI 兼容的 Embedding API 生成向量；Embedding 服务与聊天模型分开配置。

```bash
export DEEPSEEK_API_KEY='你的 DeepSeek API Key'
export EMBEDDING_API_KEY='你的 Embedding API Key'
# 如使用 OpenAI 兼容服务，可配置它的 API base URL 和模型：
# export EMBEDDING_BASE_URL='https://api.openai.com'
# export EMBEDDING_MODEL='text-embedding-3-small'
# export EMBEDDING_DIMENSIONS='1536'

docker compose up -d
./mvnw spring-boot:run
```

首次启动会按配置初始化 pgvector 表。向量模型、维度和已有向量表必须保持一致；更换 Embedding 模型或维度时，应先规划并重建向量数据。

## API

### 上传文档

```bash
curl -X POST http://localhost:8080/api/documents \
  -F 'file=@./handbook.pdf'
```

支持 PDF、DOC、DOCX、Markdown、TXT、HTML；单文件最大 20 MB。上传成功后返回 `documentId`、文件名和分块数量。导入处理为解析 → 分块 → 向量化 → 写入向量库。

### 检索问答

```bash
curl -X POST http://localhost:8080/api/rag/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"公司的差旅报销规则是什么？"}'
```

响应包含 `answer` 与 `citations`。没有超过相似度阈值的检索结果时，系统会直接说明当前知识库缺少依据，不调用模型编造答案。

## 主要模块

- `document/DocumentController`：文档上传 API。
- `document/DocumentIngestionService`：格式和大小校验、解析、元数据标注、切分和向量入库。
- `rag/RagController`：RAG 问答 API。
- `rag/RagService`：Top-K 检索、上下文构造、DeepSeek 生成和来源引用。
- `application.yaml`：模型、pgvector、分块、检索和上传限制配置。

## 当前范围

这是 Project 02 的首个可运行骨架：完成基础文档导入与问答闭环。当前每次上传都创建独立文档 ID；尚未实现文档列表/删除、重复文件幂等更新、重排（Reranking）、混合检索和评测集。这些能力可在基础 RAG 跑通后逐步扩展。
