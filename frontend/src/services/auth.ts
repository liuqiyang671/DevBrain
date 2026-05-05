import { api } from './api';
import type { CurrentUser, PageResult, PermissionItem, ResourceItem, RoleItem, UserItem } from '../types';

/**
 * 认证与用户管理 API 模块
 * 提供登录、注册、登出、密码重置、用户/角色/权限/资源的 CRUD 操作
 */

/**
 * 用户注册
 * @param payload - 注册信息（用户名、邮箱、密码、可选显示名称）
 * @returns 注册成功后的当前用户信息
 */
export async function register(payload: { username: string; email: string; password: string; displayName?: string }) {
  return api.post<CurrentUser, CurrentUser>('/auth/register', payload);
}

/**
 * 用户登录
 * @param payload - 登录凭据（用户名、密码）
 * @returns 包含用户信息的对象
 */
export async function login(payload: { username: string; password: string }) {
  return api.post<{ user: CurrentUser }, { user: CurrentUser }>('/auth/login', payload);
}

/**
 * 用户登出
 * 清除服务端会话，前端由 authStore 处理状态重置
 */
export async function logout() {
  return api.post<void, void>('/auth/logout');
}

/**
 * 忘记密码 - 发送重置链接到指定邮箱
 * @param email - 用户邮箱地址
 */
export async function forgotPassword(email: string) {
  return api.post<void, void>('/auth/password/forgot', { email });
}

/**
 * 重置密码
 * @param token - 邮件中的重置令牌
 * @param newPassword - 新密码
 */
export async function resetPassword(token: string, newPassword: string) {
  return api.post<void, void>('/auth/password/reset', { token, newPassword });
}

/**
 * 获取当前登录用户信息
 * @returns 当前用户的详细信息
 */
export async function getCurrentUser() {
  return api.get<CurrentUser, CurrentUser>('/user/me');
}

/**
 * 更新当前用户个人资料
 * @param payload - 可选的邮箱、显示名称、头像
 * @returns 更新后的用户信息
 */
export async function updateProfile(payload: { email?: string; displayName?: string; avatar?: string }) {
  return api.put<CurrentUser, CurrentUser>('/user/me', payload);
}

/**
 * 修改当前用户密码
 * @param payload - 当前密码和新密码
 */
export async function changePassword(payload: { currentPassword: string; newPassword: string }) {
  return api.put<void, void>('/user/password', payload);
}

// ==================== 用户管理（管理员） ====================

/**
 * 获取用户列表
 * @param keyword - 搜索关键词，可选
 * @returns 分页用户列表
 */
export async function getUsers(keyword = '') {
  return api.get<PageResult<UserItem>, PageResult<UserItem>>('/users', { params: { current: 1, size: 50, keyword: keyword || undefined } });
}

/**
 * 保存用户（新建或更新）
 * - 有 id 时执行更新（PUT），无 id 时执行新建（POST）
 * @param payload - 用户信息，包含可选的密码和角色编码列表
 */
export async function saveUser(payload: Partial<UserItem> & { password?: string; roleCodes?: string[] }) {
  if (payload.id) {
    return api.put<UserItem, UserItem>(`/users/${payload.id}`, payload);
  }
  return api.post<UserItem, UserItem>('/users', payload);
}

/**
 * 删除用户
 * @param id - 用户 ID
 */
export async function deleteUser(id: string) {
  return api.delete<void, void>(`/users/${id}`);
}

// ==================== 角色管理 ====================

/**
 * 获取所有角色列表
 * @returns 角色数组
 */
export async function getRoles() {
  return api.get<RoleItem[], RoleItem[]>('/roles');
}

/**
 * 保存角色（新建或更新）
 * @param payload - 角色信息
 */
export async function saveRole(payload: Partial<RoleItem>) {
  if (payload.id) {
    return api.put<RoleItem, RoleItem>(`/roles/${payload.id}`, payload);
  }
  return api.post<RoleItem, RoleItem>('/roles', payload);
}

/**
 * 删除角色
 * @param id - 角色 ID
 */
export async function deleteRole(id: string) {
  return api.delete<void, void>(`/roles/${id}`);
}

/**
 * 为角色分配权限
 * @param id - 角色 ID
 * @param permissionCodes - 权限编码数组
 */
export async function assignRolePermissions(id: string, permissionCodes: string[]) {
  return api.put<void, void>(`/roles/${id}/permissions`, { permissionCodes });
}

// ==================== 权限管理 ====================

/**
 * 获取所有权限列表
 * @returns 权限数组
 */
export async function getPermissions() {
  return api.get<PermissionItem[], PermissionItem[]>('/permissions');
}

/**
 * 保存权限（新建或更新）
 * @param payload - 权限信息
 */
export async function savePermission(payload: Partial<PermissionItem>) {
  if (payload.id) {
    return api.put<PermissionItem, PermissionItem>(`/permissions/${payload.id}`, payload);
  }
  return api.post<PermissionItem, PermissionItem>('/permissions', payload);
}

/**
 * 删除权限
 * @param id - 权限 ID
 */
export async function deletePermission(id: string) {
  return api.delete<void, void>(`/permissions/${id}`);
}

// ==================== 资源管理 ====================

/**
 * 获取所有资源列表
 * @returns 资源数组
 */
export async function getResources() {
  return api.get<ResourceItem[], ResourceItem[]>('/resources');
}

/**
 * 保存资源（新建或更新）
 * @param payload - 资源信息（HTTP 方法、路径模式、关联权限等）
 */
export async function saveResource(payload: Partial<ResourceItem>) {
  if (payload.id) {
    return api.put<ResourceItem, ResourceItem>(`/resources/${payload.id}`, payload);
  }
  return api.post<ResourceItem, ResourceItem>('/resources', payload);
}

/**
 * 删除资源
 * @param id - 资源 ID
 */
export async function deleteResource(id: string) {
  return api.delete<void, void>(`/resources/${id}`);
}
