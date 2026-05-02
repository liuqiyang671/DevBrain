package edu.cqupt.devbrain.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.user.controller.request.ChangePasswordRequest;
import edu.cqupt.devbrain.user.controller.request.ProfileUpdateRequest;
import edu.cqupt.devbrain.user.controller.request.UserCreateRequest;
import edu.cqupt.devbrain.user.controller.request.UserUpdateRequest;
import edu.cqupt.devbrain.user.controller.vo.CurrentUserVO;
import edu.cqupt.devbrain.user.controller.vo.UserVO;

/**
 * 用户服务接口 —— 定义用户信息查询、个人资料管理、密码修改及用户 CRUD 操作。
 */
public interface UserService {

    /**
     * 获取当前登录用户信息。
     *
     * @return 当前用户信息
     */
    CurrentUserVO current();

    /**
     * 更新当前用户个人资料。
     *
     * @param request 个人资料更新请求
     * @return 更新后的用户信息
     */
    CurrentUserVO updateProfile(ProfileUpdateRequest request);

    /**
     * 修改当前用户密码。
     *
     * @param request 密码修改请求
     */
    void changePassword(ChangePasswordRequest request);

    /**
     * 分页查询用户列表。
     *
     * @param current 当前页码
     * @param size    每页大小
     * @param keyword 搜索关键词（可选）
     * @return 分页用户数据
     */
    IPage<UserVO> page(long current, long size, String keyword);

    /**
     * 创建新用户。
     *
     * @param request 用户创建请求
     * @return 创建成功的用户信息
     */
    UserVO create(UserCreateRequest request);

    /**
     * 更新用户信息。
     *
     * @param id      用户ID
     * @param request 用户更新请求
     * @return 更新后的用户信息
     */
    UserVO update(String id, UserUpdateRequest request);

    /**
     * 删除用户（逻辑删除）。
     *
     * @param id 用户ID
     */
    void delete(String id);
}
