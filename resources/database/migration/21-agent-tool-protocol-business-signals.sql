-- 21: Agent 工具协议与导购业务信号
-- 允许商品标签表保存文档抽取出的促销/优惠信息，供导购推荐解释使用。

ALTER TABLE t_product_tag
    DROP CONSTRAINT IF EXISTS ck_product_tag_type;

ALTER TABLE t_product_tag
    ADD CONSTRAINT ck_product_tag_type
        CHECK (tag_type IN ('selling_point', 'scenario', 'audience', 'risk', 'promotion'));

INSERT INTO t_devbrain_schema_info (version, description)
VALUES ('21-agent-tool-protocol-business-signals', 'Allow promotion tags for guide business signal recommendations')
ON CONFLICT (version) DO NOTHING;
