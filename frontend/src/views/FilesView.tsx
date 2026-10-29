import { useEffect, useState } from 'react';
import { api } from '../api';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { FileTypeBadge } from '../components/FileTypeBadge';
import { Icon } from '../components/Icon';
import { useToast } from '../components/Toasts';
import { UploadZone } from '../components/UploadZone';
import { formatBytes, formatDate } from '../format';
import type { FileItem, Page } from '../types';

const PAGE_SIZE = 10;

export function FilesView({ refreshKey, onChanged }: { refreshKey: number; onChanged: () => void }) {
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<FileItem> | null>(null);
  const [loading, setLoading] = useState(true);
  const [target, setTarget] = useState<FileItem | null>(null);
  const [deleting, setDeleting] = useState(false);
  const notify = useToast();

  // 입력 중 매 키 입력마다 요청하지 않도록 300ms 디바운스
  useEffect(() => {
    const t = window.setTimeout(() => {
      setQuery(keyword);
      setPage(0);
    }, 300);
    return () => window.clearTimeout(t);
  }, [keyword]);

  useEffect(() => {
    setLoading(true);
    api
      .listFiles(query, page, PAGE_SIZE)
      .then(setData)
      .catch((e: Error) => notify('error', `목록 조회 실패: ${e.message}`))
      .finally(() => setLoading(false));
  }, [query, page, refreshKey, notify]);

  async function confirmDelete() {
    if (!target) return;
    setDeleting(true);
    try {
      await api.deleteFile(target.id);
      notify('success', `삭제 완료: ${target.originalName}`);
      if (data && data.content.length === 1 && page > 0) setPage(page - 1);
      onChanged();
    } catch (e) {
      notify('error', `삭제 실패: ${(e as Error).message}`);
    } finally {
      setDeleting(false);
      setTarget(null);
    }
  }

  const files = data?.content ?? [];

  return (
    <>
      <header className="page-header">
        <div>
          <h1>문서 관리</h1>
          <p className="muted">업로드한 문서를 검색하고 다운로드하거나 삭제합니다</p>
        </div>
      </header>

      <UploadZone onUploaded={onChanged} compact />

      <div className="card table-card">
        <div className="table-toolbar">
          <div className="search">
            <Icon name="search" size={16} />
            <input
              type="search"
              placeholder="파일명으로 검색"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              aria-label="파일명 검색"
            />
          </div>
          <span className="muted">
            {data ? `총 ${data.totalElements.toLocaleString()}개` : ''} · 최신 업로드 순
          </span>
        </div>

        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>파일명</th>
                <th className="col-size">크기</th>
                <th className="col-date">업로드 날짜</th>
                <th className="col-actions" aria-label="작업" />
              </tr>
            </thead>
            <tbody>
              {files.map((f) => (
                <tr key={f.id}>
                  <td>
                    <div className="file-cell">
                      <FileTypeBadge extension={f.extension} />
                      <div className="file-cell-text">
                        <span className="truncate" title={f.originalName}>
                          {f.originalName}
                        </span>
                        <span className="muted mobile-only">
                          {formatBytes(f.size)} · {formatDate(f.uploadedAt)}
                        </span>
                      </div>
                    </div>
                  </td>
                  <td className="col-size">{formatBytes(f.size)}</td>
                  <td className="col-date">{formatDate(f.uploadedAt)}</td>
                  <td className="col-actions">
                    <a className="icon-btn" href={api.downloadUrl(f.id)} title="다운로드" aria-label={`${f.originalName} 다운로드`}>
                      <Icon name="download" size={16} />
                    </a>
                    <button className="icon-btn icon-btn-danger" onClick={() => setTarget(f)} title="삭제" aria-label={`${f.originalName} 삭제`}>
                      <Icon name="trash" size={16} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {!loading && files.length === 0 && (
            <div className="empty">
              {query ? `"${query}"에 해당하는 파일이 없습니다.` : '아직 업로드된 파일이 없습니다. 위 영역에 파일을 끌어다 놓으세요.'}
            </div>
          )}
        </div>

        {data && data.totalPages > 1 && (
          <div className="pagination">
            <button className="icon-btn" disabled={page === 0} onClick={() => setPage(page - 1)} aria-label="이전 페이지">
              <Icon name="chevronLeft" size={16} />
            </button>
            <span>
              {page + 1} / {data.totalPages}
            </span>
            <button className="icon-btn" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)} aria-label="다음 페이지">
              <Icon name="chevronRight" size={16} />
            </button>
          </div>
        )}
      </div>

      {target && (
        <ConfirmDialog
          title="파일을 삭제할까요?"
          message={`"${target.originalName}" 파일이 Azure Blob Storage와 데이터베이스에서 영구 삭제됩니다.`}
          confirmLabel="삭제"
          busy={deleting}
          onConfirm={confirmDelete}
          onCancel={() => !deleting && setTarget(null)}
        />
      )}
    </>
  );
}
