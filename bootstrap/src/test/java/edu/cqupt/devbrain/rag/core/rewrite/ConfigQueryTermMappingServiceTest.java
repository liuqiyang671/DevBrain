package edu.cqupt.devbrain.rag.core.rewrite;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigQueryTermMappingServiceTest {

    @Test
    void normalizeShouldReplaceAliasesWithStandardTerms() {
        QueryRewriteProperties properties = new QueryRewriteProperties();
        properties.setTermMappings(Map.of(
                "虚拟专用网络", List.of("VPN", "vpn"),
                "Redis 缓存", List.of("redis")
        ));
        ConfigQueryTermMappingService service = new ConfigQueryTermMappingService(properties);

        String result = service.normalize("VPN 连不上，redis 怎么清理？");

        assertEquals("虚拟专用网络 连不上，Redis 缓存 怎么清理？", result);
    }
}
