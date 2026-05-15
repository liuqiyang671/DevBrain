interface GuideFeedbackBarProps {
  disabled: boolean;
  targetLabel?: string;
  onSubmit: (feedbackType: string, comment?: string | null) => void;
}

export function GuideFeedbackBar({ disabled, targetLabel = '本次回答', onSubmit }: GuideFeedbackBarProps) {
  return (
    <div className="guide-feedback-bar">
      <span>{targetLabel}</span>
      <button type="button" disabled={disabled} onClick={() => onSubmit('helpful')}>有帮助</button>
      <button type="button" disabled={disabled} onClick={() => onSubmit('wrong_product', '推荐商品不匹配')}>商品不准</button>
      <button type="button" disabled={disabled} onClick={() => onSubmit('bad_citation', '证据不足或引用不对')}>证据问题</button>
    </div>
  );
}
