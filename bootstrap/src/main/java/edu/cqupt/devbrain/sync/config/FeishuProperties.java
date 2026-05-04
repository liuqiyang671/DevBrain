package edu.cqupt.devbrain.sync.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 飞书开放平台配置属性，前缀为 {@code devbrain.sync.feishu}。
 */
@Data
@ConfigurationProperties(prefix = "devbrain.sync.feishu")
public class FeishuProperties {

    private String appId = "";
    private String appSecret = "";
    private String tokenUrl = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
    private String docxContentUrl = "https://open.feishu.cn/open-apis/docx/v1/documents";
    private String wikiNodeUrl = "https://open.feishu.cn/open-apis/wiki/v2/spaces/get_node";
    private String sheetValuesUrl = "https://open.feishu.cn/open-apis/sheets/v2/spreadsheets";
    private Duration tokenCacheTtl = Duration.ofMinutes(90);
}
