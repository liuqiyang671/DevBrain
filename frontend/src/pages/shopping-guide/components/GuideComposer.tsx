/**
 * 导购对话输入组件。
 * 支持文本输入和图片上传，Enter发送、Shift+Enter换行。
 */
import { FormEvent, KeyboardEvent, useState } from 'react';
import type { GuideImageRef } from '../../../types';
import { GuideImageUploader } from './GuideImageUploader';

interface GuideComposerProps {
  streaming: boolean;
  sessionId?: string | null;
  onSend: (message: string, images: GuideImageRef[]) => void;
  onStop: () => void;
}

export function GuideComposer({ streaming, sessionId, onSend, onStop }: GuideComposerProps) {
  const [draft, setDraft] = useState('');
  const [images, setImages] = useState<GuideImageRef[]>([]);

  function submit(event?: FormEvent) {
    event?.preventDefault();
    const message = draft.trim();
    if ((!message && images.length === 0) || streaming || images.some((image) => image.uploadStatus === 'uploading')) return;
    const uploadedImages = images.filter((image) => image.uploadStatus !== 'failed');
    setDraft('');
    setImages([]);
    onSend(message || '请结合我上传的图片帮我判断', uploadedImages);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      submit();
    }
  }

  return (
    <form className="guide-composer" onSubmit={submit}>
      <GuideImageUploader
        disabled={streaming}
        sessionId={sessionId}
        images={images}
        onChange={setImages}
      />
      <textarea
        value={draft}
        rows={2}
        placeholder="描述预算、品类、使用场景或要比较的商品"
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={handleKeyDown}
        disabled={streaming}
      />
      <div className="guide-composer-actions">
        {streaming ? (
          <button className="btn btn-danger" type="button" onClick={onStop}>停止</button>
        ) : (
          <button
            className="btn btn-primary"
            type="submit"
            disabled={(!draft.trim() && images.length === 0) || images.some((image) => image.uploadStatus === 'uploading')}
          >
            发送
          </button>
        )}
      </div>
    </form>
  );
}
