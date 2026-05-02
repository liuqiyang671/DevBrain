package edu.cqupt.devbrain.user.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * 资源访问规则实体 —— 对应数据库表 t_resource，定义 API 接口的访问控制策略。
 * <p>
 * 每条规则绑定一个 HTTP 方法 + 路径模式（支持 Ant 风格通配符），
 * 并关联一个权限码，用于 {@link edu.cqupt.devbrain.user.service.AccessControlService} 进行接口级权限校验。
 * <p>
 * 字段说明：
 * <ul>
 *   <li>httpMethod — HTTP 方法（GET/POST/PUT/PATCH/DELETE）</li>
 *   <li>pathPattern — 路径模式，支持 Ant 风格通配符，如 /api/users/**</li>
 *   <li>permissionCode — 要求的权限码，为空表示仅需登录即可访问</li>
 *   <li>publicAccess — 是否公开访问，1 表示无需登录即可访问</li>
 * </ul>
 */
@TableName("t_resource")
public class ResourceDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String resourceName;
    /** HTTP 方法：GET、POST、PUT、PATCH、DELETE */
    private String httpMethod;
    /** 路径模式，支持 Ant 风格通配符 */
    private String pathPattern;
    /** 为空表示只要求登录；非空时要求当前用户具备对应权限码。 */
    private String permissionCode;
    /** 是否公开访问：0 表示需认证，1 表示无需登录 */
    private Integer publicAccess;
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }
    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public String getPathPattern() { return pathPattern; }
    public void setPathPattern(String pathPattern) { this.pathPattern = pathPattern; }
    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }
    public Integer getPublicAccess() { return publicAccess; }
    public void setPublicAccess(Integer publicAccess) { this.publicAccess = publicAccess; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
