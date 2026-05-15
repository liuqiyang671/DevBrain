-- 23: 评测与反馈治理增强
-- 增加评测进度、失败归因、Agent Run 关联和细粒度反馈目标。

ALTER TABLE t_eval_run
    ADD COLUMN IF NOT EXISTS progress_json JSONB,
    ADD COLUMN IF NOT EXISTS case_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS completed_case_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS failed_case_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE t_eval_result
    ADD COLUMN IF NOT EXISTS agent_run_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS failure_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS latency_ms BIGINT,
    ADD COLUMN IF NOT EXISTS expected_json JSONB,
    ADD COLUMN IF NOT EXISTS actual_json JSONB,
    ADD COLUMN IF NOT EXISTS debug_hints JSONB;

CREATE INDEX IF NOT EXISTS idx_eval_result_agent_run ON t_eval_result (agent_run_id);

ALTER TABLE t_guide_feedback
    ADD COLUMN IF NOT EXISTS target_type VARCHAR(32) NOT NULL DEFAULT 'answer',
    ADD COLUMN IF NOT EXISTS target_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS agent_run_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS step_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS evidence_id VARCHAR(96),
    ADD COLUMN IF NOT EXISTS reason_index INTEGER,
    ADD COLUMN IF NOT EXISTS improvement_suggestion TEXT;

ALTER TABLE t_guide_feedback
    DROP CONSTRAINT IF EXISTS ck_guide_feedback_type;

ALTER TABLE t_guide_feedback
    ADD CONSTRAINT ck_guide_feedback_type CHECK (
        feedback_type IN (
            'like', 'dislike', 'wrong', 'purchased', 'not_interested', 'helpful', 'not_helpful',
            'wrong_product', 'wrong_fact', 'missing_context', 'bad_citation', 'unsafe_or_inappropriate',
            'irrelevant_reason', 'weak_evidence', 'missing_product', 'bad_ranking', 'unhelpful_clarification'
        )
    );

ALTER TABLE t_guide_feedback
    DROP CONSTRAINT IF EXISTS ck_guide_feedback_target_type;

ALTER TABLE t_guide_feedback
    ADD CONSTRAINT ck_guide_feedback_target_type CHECK (
        target_type IN ('answer', 'product', 'reason', 'evidence', 'tool_step', 'session')
    );

INSERT INTO t_devbrain_schema_info (version, description)
VALUES ('23-evaluation-feedback-governance', 'Evaluation progress, failure attribution, agent-run linking, and feedback target governance')
ON CONFLICT (version) DO NOTHING;
