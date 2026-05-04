export interface CurrentUser {
  userId: string;
  username: string;
  email: string;
  displayName?: string | null;
  avatar?: string | null;
  roles: string[];
  permissions: string[];
}

export interface UserItem extends CurrentUser {
  id: string;
  status: string;
  lastLoginTime?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export type KnowledgeBaseStatus = 'enabled' | 'disabled';

export interface KnowledgeBaseItem {
  id: string;
  name: string;
  description?: string | null;
  embeddingModel: string;
  collectionName: string;
  status: KnowledgeBaseStatus | string;
  documentCount: number;
  chunkCount?: number | null;
  createdBy?: string | null;
  updatedBy?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface KnowledgeBasePageParams {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  status?: KnowledgeBaseStatus | '';
}

export interface KnowledgeBaseCreatePayload {
  name: string;
  description?: string;
  collectionName: string;
  embeddingModel: string;
  status?: KnowledgeBaseStatus;
}

export interface KnowledgeBaseUpdatePayload {
  name: string;
  description?: string;
  embeddingModel: string;
  status: KnowledgeBaseStatus;
}

export interface KnowledgeDocumentItem {
  id: string;
  kbId: string;
  docName: string;
  enabled: number;
  chunkCount: number;
  fileUrl: string;
  fileType: string;
  fileSize: number;
  processMode: string;
  status: string;
  sourceType: string;
  sourceLocation?: string | null;
  chunkStrategy?: string | null;
  chunkConfig?: string | null;
  pipelineId?: string | null;
  scheduleEnabled?: number;
  scheduleCron?: string | null;
  lastSyncTime?: string | null;
  lastContentHash?: string | null;
  createTime: string;
  updateTime: string;
}

export interface KnowledgeDocumentPageParams {
  pageNo: number;
  pageSize: number;
  kbId?: string;
  keyword?: string;
  status?: string;
  enabled?: number | '';
}

export interface DocumentUploadPayload {
  file: File;
  processMode?: string;
  chunkStrategy?: string;
  chunkConfig?: string;
  pipelineId?: string;
}

export interface OnlineDocumentImportPayload {
  sourceType: 'feishu' | 'url';
  sourceLocation: string;
  docName?: string;
  processMode?: string;
  chunkStrategy?: string;
  chunkConfig?: string;
  pipelineId?: string;
  scheduleEnabled?: number;
  scheduleCron?: string;
}

export interface DocumentChunkItem {
  chunkId: string;
  index: number;
  content: string;
  charCount: number;
}

export interface KnowledgeChunkItem {
  id: string;
  kbId: string;
  docId: string;
  chunkIndex: number;
  content: string;
  contentHash?: string | null;
  charCount?: number | null;
  tokenCount?: number | null;
  enabled?: number | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface RoleItem {
  id: string;
  roleCode: string;
  roleName: string;
  description?: string | null;
  permissionCodes: string[];
}

export interface PermissionItem {
  id: string;
  permissionCode: string;
  permissionName: string;
  description?: string | null;
}

export interface ResourceItem {
  id: string;
  resourceName: string;
  httpMethod: string;
  pathPattern: string;
  permissionCode?: string | null;
  publicAccess: number;
}

export interface ScheduleConfigPayload {
  sourceType: string;
  sourceLocation: string;
  scheduleEnabled: number;
  scheduleCron?: string;
}

export interface SyncHistoryItem {
  id: string;
  docId: string;
  syncStatus: string;
  contentHash?: string;
  contentChanged: number;
  errorMessage?: string;
  durationMs?: number;
  createTime: string;
}

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
