/**
 * 前端全局类型定义模块
 * 包含用户、知识库、文档、分块、权限、同步等业务领域的 TypeScript 接口
 */

/**
 * 当前登录用户信息
 * 登录成功后由后端返回，存储在 authStore 中
 */
export interface CurrentUser {
  userId: string;
  username: string;
  email: string;
  displayName?: string | null;
  avatar?: string | null;
  /** 用户拥有的角色编码列表，如 ['admin'] */
  roles: string[];
  /** 用户拥有的权限编码列表 */
  permissions: string[];
}

/**
 * 用户管理列表项（管理员视角）
 * 继承 CurrentUser，额外包含状态、登录时间等管理字段
 */
export interface UserItem extends CurrentUser {
  id: string;
  /** 用户状态，如 'active'、'disabled' */
  status: string;
  lastLoginTime?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

/**
 * 通用分页结果包装
 * @template T - 列表项的类型
 */
export interface PageResult<T> {
  /** 数据记录列表 */
  records: T[];
  /** 总记录数 */
  total: number;
  /** 每页大小 */
  size: number;
  /** 当前页码 */
  current: number;
  /** 总页数 */
  pages: number;
}

/** 摄入 Pipeline 支持的节点类型 */
export type IngestionNodeType = 'fetcher' | 'parser' | 'enhancer' | 'chunker' | 'enricher' | 'indexer';

/** 摄入 Pipeline 节点配置 */
export interface IngestionPipelineNodeItem {
  /** 流水线内节点 ID */
  nodeId: string;
  /** 节点类型 */
  nodeType: IngestionNodeType | string;
  /** 节点私有配置 */
  settings: Record<string, unknown>;
  /** 条件配置，后端支持 true/false 或 JSON 字符串 */
  condition?: string | null;
  /** 默认下一个节点 ID */
  nextNodeId?: string | null;
  /** 排序号 */
  sortOrder?: number | null;
}

/** 摄入 Pipeline 定义 */
export interface IngestionPipelineItem {
  id: string;
  name: string;
  description?: string | null;
  nodeCount?: number;
  nodes?: IngestionPipelineNodeItem[] | null;
  createdBy?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

/** 创建或更新 Pipeline 的节点配置 */
export interface IngestionPipelineNodePayload {
  nodeId: string;
  nodeType: IngestionNodeType | string;
  settings?: Record<string, unknown>;
  condition?: string | null;
  nextNodeId?: string | null;
}

/** 创建或更新 Pipeline 请求 */
export interface IngestionPipelinePayload {
  name: string;
  description?: string;
  nodes: IngestionPipelineNodePayload[];
}

/** 摄入任务执行结果 */
export interface IngestionResultItem {
  taskId: string;
  pipelineId: string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | string;
  chunkCount: number;
  message?: string | null;
}

/** 摄入任务列表项 */
export interface IngestionTaskItem {
  id: string;
  pipelineId: string;
  sourceType: string;
  sourceLocation: string;
  status: string;
  chunkCount?: number | null;
  logs?: Array<Record<string, unknown>> | null;
  metadata?: Record<string, unknown> | null;
  createdBy?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

/** 摄入任务节点日志 */
export interface IngestionTaskNodeItem {
  id: string;
  taskId: string;
  pipelineId: string;
  nodeId: string;
  nodeType: string;
  nodeOrder?: number | null;
  status: string;
  durationMs?: number | null;
  output?: Record<string, unknown> | null;
  createTime?: string | null;
}

/** 执行摄入任务请求 */
export interface ExecuteIngestionTaskPayload {
  pipelineId: string;
  sourceType: 'FILE' | 'URL' | 'FEISHU' | 'S3' | string;
  sourceLocation: string;
  fileName?: string;
  metadata?: Record<string, unknown>;
}

/** 知识库状态类型 */
export type KnowledgeBaseStatus = 'enabled' | 'disabled';

/**
 * 知识库信息
 * 表示一个知识库的完整配置和统计信息
 */
export interface KnowledgeBaseItem {
  id: string;
  name: string;
  description?: string | null;
  /** 使用的 Embedding 模型标识 */
  embeddingModel: string;
  /** 向量数据库中的集合名称 */
  collectionName: string;
  status: KnowledgeBaseStatus | string;
  /** 知识库中的文档数量 */
  documentCount: number;
  /** 知识库中的分块总数 */
  chunkCount?: number | null;
  createdBy?: string | null;
  updatedBy?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

/**
 * 知识库分页查询参数
 */
export interface KnowledgeBasePageParams {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  status?: KnowledgeBaseStatus | '';
}

/**
 * 创建知识库请求参数
 */
export interface KnowledgeBaseCreatePayload {
  name: string;
  description?: string;
  /** 向量集合名称，需在向量数据库中唯一 */
  collectionName: string;
  embeddingModel: string;
  status?: KnowledgeBaseStatus;
}

/**
 * 更新知识库请求参数
 */
export interface KnowledgeBaseUpdatePayload {
  name: string;
  description?: string;
  embeddingModel: string;
  status: KnowledgeBaseStatus;
}

/**
 * 知识文档信息
 * 表示知识库中的一份文档及其处理状态
 */
export interface KnowledgeDocumentItem {
  id: string;
  /** 所属知识库 ID */
  kbId: string;
  docName: string;
  /** 是否启用（1=启用，0=禁用） */
  enabled: number;
  /** 文档分块数量 */
  chunkCount: number;
  fileUrl: string;
  fileType: string;
  fileSize: number;
  /** 处理模式 */
  processMode: string;
  /** 文档处理状态 */
  status: string;
  /** 来源类型：file、feishu、url */
  sourceType: string;
  sourceLocation?: string | null;
  /** 分块策略 */
  chunkStrategy?: string | null;
  /** 分块配置 JSON 字符串 */
  chunkConfig?: string | null;
  /** 处理流水线 ID */
  pipelineId?: string | null;
  /** 是否启用定时同步（1=启用，0=禁用） */
  scheduleEnabled?: number;
  /** 定时同步 Cron 表达式 */
  scheduleCron?: string | null;
  lastSyncTime?: string | null;
  /** 最后同步时的内容哈希，用于变更检测 */
  lastContentHash?: string | null;
  createTime: string;
  updateTime: string;
}

/**
 * 知识文档分页查询参数
 */
export interface KnowledgeDocumentPageParams {
  pageNo: number;
  pageSize: number;
  kbId?: string;
  keyword?: string;
  status?: string;
  /** 启用状态筛选，空字符串表示不筛选 */
  enabled?: number | '';
}

/**
 * 文档上传请求参数
 */
export interface DocumentUploadPayload {
  file: File;
  processMode?: string;
  chunkStrategy?: string;
  chunkConfig?: string;
  pipelineId?: string;
}

/**
 * 在线文档导入请求参数
 * 支持从飞书或 URL 导入文档
 */
export interface OnlineDocumentImportPayload {
  /** 来源类型：飞书文档或 URL */
  sourceType: 'feishu' | 'url';
  /** 来源地址（飞书文档链接或网页 URL） */
  sourceLocation: string;
  docName?: string;
  processMode?: string;
  chunkStrategy?: string;
  chunkConfig?: string;
  pipelineId?: string;
  /** 是否启用定时同步 */
  scheduleEnabled?: number;
  /** 定时同步 Cron 表达式 */
  scheduleCron?: string;
}

/**
 * 文档分块信息（通用接口返回）
 */
export interface DocumentChunkItem {
  chunkId: string;
  /** 分块在文档中的序号 */
  index: number;
  /** 分块文本内容 */
  content: string;
  /** 字符数 */
  charCount: number;
}

/**
 * 知识分块信息（知识库专用接口返回）
 * 包含更丰富的元数据，如哈希值、Token 数等
 */
export interface KnowledgeChunkItem {
  id: string;
  kbId: string;
  docId: string;
  chunkIndex: number;
  content: string;
  /** 内容哈希，用于去重和变更检测 */
  contentHash?: string | null;
  charCount?: number | null;
  /** Token 数量，用于 Embedding 模型输入限制 */
  tokenCount?: number | null;
  /** 是否启用（1=启用，0=禁用） */
  enabled?: number | null;
  createTime?: string | null;
  updateTime?: string | null;
}

/**
 * 角色信息
 * 用于 RBAC 权限模型中的角色定义
 */
export interface RoleItem {
  id: string;
  /** 角色编码，如 'admin'、'user' */
  roleCode: string;
  roleName: string;
  description?: string | null;
  /** 该角色拥有的权限编码列表 */
  permissionCodes: string[];
}

/**
 * 权限信息
 * 用于 RBAC 权限模型中的权限定义
 */
export interface PermissionItem {
  id: string;
  /** 权限编码，如 'kb:create'、'doc:delete' */
  permissionCode: string;
  permissionName: string;
  description?: string | null;
}

/**
 * API 资源信息
 * 定义 HTTP 接口与权限的映射关系
 */
export interface ResourceItem {
  id: string;
  resourceName: string;
  /** HTTP 方法：GET、POST、PUT、DELETE 等 */
  httpMethod: string;
  /** URL 路径模式，如 '/api/knowledge-base/**' */
  pathPattern: string;
  /** 关联的权限编码 */
  permissionCode?: string | null;
  /** 是否公开访问（1=公开，0=需认证） */
  publicAccess: number;
}

/**
 * 定时同步配置请求参数
 */
export interface ScheduleConfigPayload {
  sourceType: string;
  sourceLocation: string;
  /** 是否启用定时同步（1=启用，0=禁用） */
  scheduleEnabled: number;
  /** Cron 表达式，如 '0 0 2 * * ?' 表示每天凌晨 2 点 */
  scheduleCron?: string;
}

/**
 * 同步历史记录
 * 记录每次同步操作的执行结果
 */
export interface SyncHistoryItem {
  id: string;
  docId: string;
  /** 同步状态：success、failed 等 */
  syncStatus: string;
  contentHash?: string;
  /** 内容是否发生变化（1=变化，0=未变化） */
  contentChanged: number;
  /** 失败时的错误信息 */
  errorMessage?: string;
  /** 同步耗时（毫秒） */
  durationMs?: number;
  createTime: string;
}

/**
 * 同步任务概览
 * 展示文档的同步配置和最后同步状态
 */
export interface SyncTaskOverviewItem {
  docId: string;
  docName: string;
  kbId: string;
  sourceType: string;
  sourceLocation: string;
  scheduleEnabled: number;
  scheduleCron?: string;
  lastSyncTime?: string;
  lastContentHash?: string;
}

/** RAG 消息角色 */
export type RagMessageRole = 'user' | 'assistant' | 'system';

/** RAG 会话摘要，用于前台会话列表 */
export interface RagConversationSummary {
  conversationId: string;
  title?: string | null;
  lastQuestion?: string | null;
  lastAnswer?: string | null;
  lastTime?: string | null;
  messageCount?: number | null;
}

/** RAG 回答引用来源 */
export interface RagCitation {
  id?: string;
  kbId?: string | null;
  kbName?: string | null;
  docId?: string | null;
  docName?: string | null;
  chunkId?: string | null;
  chunkIndex?: number | null;
  score?: number | null;
  content?: string | null;
}

/** RAG 检索命中的 Chunk */
export interface RagRetrievedChunk extends RagCitation {
  collectionName?: string | null;
  metadata?: Record<string, unknown> | null;
}

/** Prompt 构建预览 */
export interface RagPromptPreview {
  scene?: string | null;
  baseTemplate?: string | null;
  kbContext?: string | null;
  mcpContext?: string | null;
  question?: string | null;
  finalPrompt?: string | null;
}

/** RAG 链路步骤状态 */
export interface RagTraceStep {
  name: 'retrieve' | 'prompt' | 'chat' | string;
  status: 'idle' | 'running' | 'success' | 'error' | string;
  durationMs?: number | null;
  message?: string | null;
}

/** RAG 会话消息 */
export interface RagMessage {
  id: string;
  conversationId?: string | null;
  role: RagMessageRole;
  content: string;
  thinkingContent?: string | null;
  thinkingDuration?: number | null;
  citations?: RagCitation[];
  retrievedChunks?: RagRetrievedChunk[];
  promptPreview?: RagPromptPreview | null;
  createTime?: string | null;
}

/** RAG 会话详情 */
export interface RagConversationDetail {
  conversationId: string;
  title?: string | null;
  messages: RagMessage[];
}

/** 前台 RAG 问答请求 */
export interface RagChatRequest {
  conversationId?: string | null;
  question: string;
  kbIds?: string[];
  topK?: number;
  returnDebug?: boolean;
}

/** 前台 RAG 问答响应 */
export interface RagChatResponse {
  conversationId: string;
  messageId?: string | null;
  answer: string;
  citations: RagCitation[];
  retrievedChunks?: RagRetrievedChunk[];
  promptPreview?: RagPromptPreview | null;
  traceSteps?: RagTraceStep[];
}

/** 后台 RAG 调试运行请求 */
export interface RagDebugRunRequest {
  question: string;
  kbIds?: string[];
  topK: number;
  returnPrompt: boolean;
}

/** 后台 RAG 调试运行结果 */
export interface RagDebugRunResult {
  runId?: string | null;
  answer?: string | null;
  citations: RagCitation[];
  retrievedChunks: RagRetrievedChunk[];
  promptPreview?: RagPromptPreview | null;
  traceSteps: RagTraceStep[];
  errorMessage?: string | null;
  createTime?: string | null;
}
