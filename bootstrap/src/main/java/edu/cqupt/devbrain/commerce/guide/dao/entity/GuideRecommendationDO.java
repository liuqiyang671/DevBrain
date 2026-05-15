package edu.cqupt.devbrain.commerce.guide.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import edu.cqupt.devbrain.knowledge.dao.typehandler.JsonbStringTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 导购推荐记录实体。
 * <p>
 * 对应 t_guide_recommendation 表，持久化每轮对话的推荐结果：
 * <ul>
 *   <li><b>关联信息</b> — conversationId（对话 ID）、turnId（轮次 ID）</li>
 *   <li><b>商品信息</b> — productId（商品 ID）、skuId（SKU ID）</li>
 *   <li><b>推荐排名</b> — rankNo（排名序号，从 1 开始）、score（推荐评分）</li>
 *   <li><b>推荐理由</b> — reasonJson（JSONB 格式的推荐理由）</li>
 *   <li><b>推荐证据</b> — evidenceJson（JSONB 格式的推荐证据）</li>
 * </ul>
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see edu.cqupt.devbrain.commerce.guide.domain.GuideRecommendation 推荐领域对象
 */
@Data
@TableName(value = "t_guide_recommendation", autoResultMap = true)
public class GuideRecommendationDO {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 对话ID */
    private String conversationId;

    /** 对话轮次ID */
    private String turnId;

    /** 推荐商品ID */
    private String productId;

    /** 推荐SKU ID */
    private String skuId;

    /** 排名序号（从1开始） */
    private Integer rankNo;

    /** 推荐评分 */
    private BigDecimal score;

    /** 推荐理由JSON */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String reasonJson;

    /** 推荐证据JSON */
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String evidenceJson;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
