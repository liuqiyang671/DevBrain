import type { GuideAgentTimelineItem } from '../../../types';
import { ToolCallInspector } from './ToolCallInspector';

interface AgentStepItemProps {
  step: GuideAgentTimelineItem;
  active: boolean;
  canInspect: boolean;
}

export function AgentStepItem({ step, active, canInspect }: AgentStepItemProps) {
  return (
    <article className={`guide-agent-step ${step.status}${active ? ' active' : ''}`}>
      <div className="guide-step-index">{step.stepNo}</div>
      <div className="guide-step-body">
        <div className="guide-step-heading">
          <strong>{step.action || step.toolName || 'Agent Step'}</strong>
          <span>{statusText(step.status)}</span>
        </div>
        <div className="guide-step-meta">
          {step.toolName && <span>{step.toolName}</span>}
          {step.durationMs != null && <span>{step.durationMs} ms</span>}
        </div>
        {step.observation && <p>{step.observation}</p>}
        {step.error && <p className="guide-step-error">{step.error}</p>}
        <ToolCallInspector step={step} canInspect={canInspect} />
      </div>
    </article>
  );
}

function statusText(status: string) {
  if (status === 'running') return '运行中';
  if (status === 'success') return '完成';
  if (status === 'failed') return '失败';
  if (status === 'cancelled') return '已停止';
  return status;
}
