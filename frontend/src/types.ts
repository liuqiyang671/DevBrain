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
