package edu.cqupt.devbrain.sync.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("t_document_sync_history")
public class DocumentSyncHistoryDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String docId;

    private String syncStatus;

    private String contentHash;

    private Integer contentChanged;

    private String errorMessage;

    private Long durationMs;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
