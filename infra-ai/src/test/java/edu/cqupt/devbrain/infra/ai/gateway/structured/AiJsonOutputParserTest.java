package edu.cqupt.devbrain.infra.ai.gateway.structured;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiJsonOutputParserTest {

    private final AiJsonOutputParser parser = new AiJsonOutputParser(new ObjectMapper());

    @Test
    void parsesPlainJsonObject() {
        PurchaseIntent value = parser.parse("""
                {"intentType":"find_product","category":"audio","requirements":["降噪","通勤"]}
                """, PurchaseIntent.class);

        assertThat(value.intentType()).isEqualTo("find_product");
        assertThat(value.category()).isEqualTo("audio");
        assertThat(value.requirements()).containsExactly("降噪", "通勤");
    }

    @Test
    void parsesJsonObjectWrappedByMarkdownAndText() {
        PurchaseIntent value = parser.parse("""
                我理解为：
                ```json
                {"intentType":"find_product","category":"laptop","requirements":["写代码"]}
                ```
                后续建议先查商品库。
                """, PurchaseIntent.class);

        assertThat(value.category()).isEqualTo("laptop");
    }

    @Test
    void reportsMissingRequiredFieldsAsWarnings() {
        AiJsonOutputParser.ParseResult<PurchaseIntent> result = parser.parseWithWarnings("""
                {"intentType":"find_product"}
                """, PurchaseIntent.class);

        assertThat(result.value().intentType()).isEqualTo("find_product");
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("category"));
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> parser.parse("模型没有返回 JSON", PurchaseIntent.class))
                .isInstanceOf(AiJsonOutputParser.AiJsonOutputParseException.class)
                .hasMessageContaining("不包含 JSON 对象");
    }

    private record PurchaseIntent(
            String intentType,
            @AiJsonOutputParser.RequiredField String category,
            List<String> requirements
    ) {
        @JsonCreator
        private PurchaseIntent(@JsonProperty("intentType") String intentType,
                               @JsonProperty("category") String category,
                               @JsonProperty("requirements") List<String> requirements) {
            this.intentType = intentType;
            this.category = category;
            this.requirements = requirements;
        }
    }
}
