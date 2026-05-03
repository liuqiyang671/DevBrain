package edu.cqupt.devbrain.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import edu.cqupt.devbrain.framework.convention.Result;
import edu.cqupt.devbrain.framework.web.Results;
import edu.cqupt.devbrain.user.controller.request.ChangePasswordRequest;
import edu.cqupt.devbrain.user.controller.request.ProfileUpdateRequest;
import edu.cqupt.devbrain.user.controller.request.UserCreateRequest;
import edu.cqupt.devbrain.user.controller.request.UserUpdateRequest;
import edu.cqupt.devbrain.user.controller.vo.CurrentUserVO;
import edu.cqupt.devbrain.user.controller.vo.UserVO;
import edu.cqupt.devbrain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理控制器 —— 提供当前用户信息获取、个人资料更新、密码修改以及用户 CRUD 管理接口。
 * <p>
 * 接口分为两类：
 * <ul>
 *   <li><b>个人操作</b>（/user/me、/user/password）：操作当前登录用户自身的数据</li>
 *   <li><b>管理操作</b>（/users）：管理员对用户进行增删改查，需要相应权限</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 获取当前登录用户信息。
     * <p>
     * 从用户上下文中读取当前登录用户数据，包含角色和权限信息。
     *
     * @return 当前用户信息
     */
    @GetMapping("/user/me")
    public Result<CurrentUserVO> currentUser() {
        return Results.success(userService.current());
    }

    /**
     * 更新当前用户个人资料。
     * <p>
     * 支持更新邮箱、显示名称和头像地址。邮箱更新时会校验唯一性。
     *
     * @param request 个人资料更新请求
     * @return 更新后的用户信息
     */
    @PutMapping("/user/me")
    public Result<CurrentUserVO> updateProfile(@RequestBody @Valid ProfileUpdateRequest request) {
        return Results.success(userService.updateProfile(request));
    }

    /**
     * 修改当前用户密码。
     * <p>
     * 需要验证当前密码正确后才能设置新密码。
     *
     * @param request 密码修改请求，包含当前密码和新密码
     * @return 空结果
     */
    @PutMapping("/user/password")
    public Result<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        userService.changePassword(request);
        return Results.success();
    }

    /**
     * 分页查询用户列表。
     * <p>
     * 支持按用户名、邮箱或显示名称进行模糊搜索，按更新时间倒序排列。
     * 每页最大 100 条记录，防止大量数据查询影响性能。
     *
     * @param current 当前页码，默认 1
     * @param size    每页大小，默认 10
     * @param keyword 搜索关键词（可选）
     * @return 分页用户列表
     */
    @GetMapping("/users")
    public Result<IPage<UserVO>> users(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return Results.success(userService.page(current, size, keyword));
    }

    /**
     * 创建新用户。
     * <p>
     * 管理员创建用户，需指定用户名、邮箱、密码，可选指定角色。
     *
     * @param request 用户创建请求
     * @return 创建成功的用户信息
     */
    @PostMapping("/users")
    public Result<UserVO> create(@RequestBody @Valid UserCreateRequest request) {
        return Results.success(userService.create(request));
    }

    /**
     * 更新用户信息。
     * <p>
     * 管理员更新用户资料，支持更新邮箱、显示名称、头像、状态、密码和角色。
     *
     * @param id      用户ID
     * @param request 用户更新请求
     * @return 更新后的用户信息
     */
    @PutMapping("/users/{id}")
    public Result<UserVO> update(@PathVariable String id, @RequestBody @Valid UserUpdateRequest request) {
        return Results.success(userService.update(id, request));
    }

    /**
     * 删除用户。
     * <p>
     * 逻辑删除，不允许删除当前登录用户自身。
     *
     * @param id 用户ID
     * @return 空结果
     */
    @DeleteMapping("/users/{id}")
    public Result<Void> delete(@PathVariable String id) {
        userService.delete(id);
        return Results.success();
    }
}
