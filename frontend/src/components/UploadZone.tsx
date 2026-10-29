import { useRef, useState, type DragEvent } from 'react';
import { api } from '../api';
import { formatBytes, validateBeforeUpload } from '../format';
import { Icon } from './Icon';
import { useToast } from './Toasts';

interface UploadTask {
  key: string;
  name: string;
  size: number;
  progress: number;
}

export function UploadZone({ onUploaded, compact = false }: { onUploaded: () => void; compact?: boolean }) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [tasks, setTasks] = useState<UploadTask[]>([]);
  const notify = useToast();

  const updateProgress = (key: string, progress: number) =>
    setTasks((list) => list.map((t) => (t.key === key ? { ...t, progress } : t)));

  async function handleFiles(fileList: FileList | null) {
    if (!fileList?.length) return;

    for (const file of Array.from(fileList)) {
      const error = validateBeforeUpload(file);
      if (error) {
        notify('error', error);
        continue;
      }
      const key = `${file.name}-${Date.now()}-${Math.random()}`;
      setTasks((list) => [...list, { key, name: file.name, size: file.size, progress: 0 }]);
      try {
        await api.uploadFile(file, (p) => updateProgress(key, p));
        notify('success', `업로드 완료: ${file.name}`);
        onUploaded();
      } catch (e) {
        notify('error', `업로드 실패: ${file.name} — ${(e as Error).message}`);
      } finally {
        setTasks((list) => list.filter((t) => t.key !== key));
      }
    }
    if (inputRef.current) inputRef.current.value = '';
  }

  function onDrop(e: DragEvent) {
    e.preventDefault();
    setDragging(false);
    handleFiles(e.dataTransfer.files);
  }

  return (
    <div className="card upload-card">
      <div
        className={`dropzone ${dragging ? 'is-dragging' : ''} ${compact ? 'is-compact' : ''}`}
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        onClick={() => inputRef.current?.click()}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && inputRef.current?.click()}
      >
        <div className="dropzone-icon">
          <Icon name="upload" size={22} />
        </div>
        <div>
          <p className="dropzone-title">
            파일을 끌어다 놓거나 <span className="link">클릭하여 선택</span>
          </p>
          <p className="dropzone-hint">PDF, PNG, JPG, TXT · 파일당 최대 10MB · Azure Blob Storage에 저장됩니다</p>
        </div>
        <input
          ref={inputRef}
          type="file"
          multiple
          accept=".pdf,.png,.jpg,.jpeg,.txt"
          hidden
          onChange={(e) => handleFiles(e.target.files)}
        />
      </div>

      {tasks.length > 0 && (
        <ul className="upload-list">
          {tasks.map((t) => (
            <li key={t.key}>
              <div className="upload-row">
                <span className="truncate">{t.name}</span>
                <span className="muted">
                  {formatBytes(t.size)} · {t.progress}%
                </span>
              </div>
              <div className="progress">
                <div className="progress-bar" style={{ width: `${t.progress}%` }} />
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
