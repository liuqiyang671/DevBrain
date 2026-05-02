import { api } from './api';
import type { CurrentUser, PageResult, PermissionItem, ResourceItem, RoleItem, UserItem } from '../types';

export async function register(payload: { username: string; email: string; password: string; displayName?: string }) {
  return api.post<CurrentUser, CurrentUser>('/auth/register', payload);
}

export async function login(payload: { username: string; password: string }) {
  return api.post<{ user: CurrentUser }, { user: CurrentUser }>('/auth/login', payload);
}

export async function logout() {
  return api.post<void, void>('/auth/logout');
}

export async function forgotPassword(email: string) {
  return api.post<void, void>('/auth/password/forgot', { email });
}

export async function resetPassword(token: string, newPassword: string) {
  return api.post<void, void>('/auth/password/reset', { token, newPassword });
}

export async function getCurrentUser() {
  return api.get<CurrentUser, CurrentUser>('/user/me');
}

export async function updateProfile(payload: { email?: string; displayName?: string; avatar?: string }) {
  return api.put<CurrentUser, CurrentUser>('/user/me', payload);
}

export async function changePassword(payload: { currentPassword: string; newPassword: string }) {
  return api.put<void, void>('/user/password', payload);
}

export async function getUsers(keyword = '') {
  return api.get<PageResult<UserItem>, PageResult<UserItem>>('/users', { params: { current: 1, size: 50, keyword: keyword || undefined } });
}

export async function saveUser(payload: Partial<UserItem> & { password?: string; roleCodes?: string[] }) {
  if (payload.id) {
    return api.put<UserItem, UserItem>(`/users/${payload.id}`, payload);
  }
  return api.post<UserItem, UserItem>('/users', payload);
}

export async function deleteUser(id: string) {
  return api.delete<void, void>(`/users/${id}`);
}

export async function getRoles() {
  return api.get<RoleItem[], RoleItem[]>('/roles');
}

export async function saveRole(payload: Partial<RoleItem>) {
  if (payload.id) {
    return api.put<RoleItem, RoleItem>(`/roles/${payload.id}`, payload);
  }
  return api.post<RoleItem, RoleItem>('/roles', payload);
}

export async function deleteRole(id: string) {
  return api.delete<void, void>(`/roles/${id}`);
}

export async function assignRolePermissions(id: string, permissionCodes: string[]) {
  return api.put<void, void>(`/roles/${id}/permissions`, { permissionCodes });
}

export async function getPermissions() {
  return api.get<PermissionItem[], PermissionItem[]>('/permissions');
}

export async function savePermission(payload: Partial<PermissionItem>) {
  if (payload.id) {
    return api.put<PermissionItem, PermissionItem>(`/permissions/${payload.id}`, payload);
  }
  return api.post<PermissionItem, PermissionItem>('/permissions', payload);
}

export async function deletePermission(id: string) {
  return api.delete<void, void>(`/permissions/${id}`);
}

export async function getResources() {
  return api.get<ResourceItem[], ResourceItem[]>('/resources');
}

export async function saveResource(payload: Partial<ResourceItem>) {
  if (payload.id) {
    return api.put<ResourceItem, ResourceItem>(`/resources/${payload.id}`, payload);
  }
  return api.post<ResourceItem, ResourceItem>('/resources', payload);
}

export async function deleteResource(id: string) {
  return api.delete<void, void>(`/resources/${id}`);
}
