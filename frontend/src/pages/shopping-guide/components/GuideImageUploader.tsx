/**
 * 导购图片上传组件。
 * 支持点击选择和拖拽上传，最多4张，限制JPG/PNG/WebP格式，单张最大10MB。
 */
import { ChangeEvent, DragEvent, useRef, useState } from 'react';
import type { GuideImageRef } from '../../../types';
import { uploadGuideImage } from '../../../services/guide';

const MAX_IMAGES = 4;
const MAX_SIZE = 10 * 1024 * 1024;
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

interface GuideImageUploaderProps {
  disabled?: boolean;
  sessionId?: string | null;
  images: GuideImageRef[];
  onChange: (images: GuideImageRef[]) => void;
}

export function GuideImageUploader({ disabled, sessionId, images, onChange }: GuideImageUploaderProps) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const imagesRef = useRef(images);
  const [dragging, setDragging] = useState(false);
  imagesRef.current = images;

  async function handleFiles(fileList: FileList | File[]) {
    if (disabled) return;
    const files = Array.from(fileList).slice(0, Math.max(0, MAX_IMAGES - imagesRef.current.length));
    if (files.length === 0) return;
    const pending = files.map(toPendingImage);
    onChange([...imagesRef.current, ...pending]);
    for (let index = 0; index < files.length; index += 1) {
      const file = files[index];
      const pendingItem = pending[index];
      const validation = validateFile(file);
      if (validation) {
        replaceImage(pendingItem.imageId, { ...pendingItem, uploadStatus: 'failed', errorMessage: validation });
        continue;
      }
      try {
        const uploaded = await uploadGuideImage(file, sessionId);
        replaceImage(pendingItem.imageId, { ...uploaded, uploadStatus: 'uploaded' });
      } catch (error) {
        replaceImage(pendingItem.imageId, {
          ...pendingItem,
          uploadStatus: 'failed',
          errorMessage: error instanceof Error ? error.message : '图片上传失败',
        });
      }
    }
  }

  function replaceImage(tempId: string, next: GuideImageRef) {
    const updated = imagesRef.current.map((item) => (item.imageId === tempId ? next : item));
    imagesRef.current = updated;
    onChange(updated);
  }

  function removeImage(imageId: string) {
    const updated = imagesRef.current.filter((item) => item.imageId !== imageId);
    imagesRef.current = updated;
    onChange(updated);
  }

  function onInputChange(event: ChangeEvent<HTMLInputElement>) {
    if (event.target.files) {
      void handleFiles(event.target.files);
    }
    event.target.value = '';
  }

  function onDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setDragging(false);
    void handleFiles(event.dataTransfer.files);
  }

  return (
    <div
      className={`guide-image-uploader ${dragging ? 'dragging' : ''}`}
      onDragOver={(event) => {
        event.preventDefault();
        if (!disabled) setDragging(true);
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={onDrop}
    >
      <input
        ref={inputRef}
        className="guide-image-input"
        type="file"
        accept="image/jpeg,image/png,image/webp"
        multiple
        onChange={onInputChange}
        disabled={disabled}
      />
      <button
        className="guide-icon-button"
        type="button"
        aria-label="上传图片"
        title="上传图片"
        onClick={() => inputRef.current?.click()}
        disabled={disabled || images.length >= MAX_IMAGES}
      >
        ◱
      </button>
      {images.length > 0 && (
        <div className="guide-image-preview-row">
          {images.map((image) => (
            <figure className={`guide-image-preview ${image.uploadStatus || 'uploaded'}`} key={image.imageId}>
              {image.previewUrl ? <img src={image.previewUrl} alt={image.fileName} /> : <span>{image.fileName.slice(0, 2)}</span>}
              <button type="button" aria-label="移除图片" title="移除图片" onClick={() => removeImage(image.imageId)}>
                x
              </button>
              <figcaption>{image.uploadStatus === 'uploading' ? '上传中' : image.errorMessage || image.fileName}</figcaption>
            </figure>
          ))}
        </div>
      )}
    </div>
  );
}

function toPendingImage(file: File): GuideImageRef {
  return {
    imageId: `pending-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    fileName: file.name,
    contentType: file.type,
    size: file.size,
    previewUrl: URL.createObjectURL(file),
    uploadStatus: 'uploading',
  };
}

function validateFile(file: File) {
  if (!ALLOWED_TYPES.includes(file.type)) {
    return '仅支持 JPG、PNG、WebP';
  }
  if (file.size > MAX_SIZE) {
    return '图片不能超过 10MB';
  }
  return null;
}
