import { useEffect, useState } from 'react';
import { api } from '../api';
import { FileTypeBadge } from '../components/FileTypeBadge';
import { Icon, type IconName } from '../components/Icon';
import { useToast } from '../components/Toasts';
import { UploadZone } from '../components/UploadZone';
import { formatBytes, formatDate, relativeTime } from '../format';
import type { DashboardSummary, Health } from '../types';

interface Props {
  health: Health | null;
  refreshKey: number;
  onChanged: () => void;
  onViewAll: () => void;
}

export function DashboardView({ health, refreshKey, onChanged, onViewAll }: Props) {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const notify = useToast();

  useEffect(() => {
    api
      .dashboard()
      .then(setSummary)
      .catch((e: Error) => notify('error', `대시보드 조회 실패: ${e.message}`));
  }, [refreshKey, notify]);

  const latest = summary?.recentFiles[0];

  return (
    <>
      <header className="page-header">
        <div>
          <h1>대시보드</h1>
          <p className="muted">Azure Blob Storage에 저장된 문서 현황</p>
        </div>
      </header>

      <section className="stats">
        <StatCard icon="files" label="전체 파일" value={summary ? summary.totalFiles.toLocaleString() : '—'} hint="MySQL 메타데이터 기준" />
        <StatCard icon="database" label="전체 저장 용량" value={summary ? formatBytes(summary.totalSize) : '—'} hint="Blob 원본 크기 합계" />
        <StatCard icon="clock" label="최근 업로드" value={latest ? relativeTime(latest.uploadedAt) : '—'} hint={latest ? latest.originalName : '업로드 내역 없음'} />
        <StatCard
          icon="cloud"
          label="스토리지"
          value={health ? (health.storage === 'UP' ? '정상' : '연결 끊김') : '—'}
          hint={health ? `${health.storageInfo.accountName} / ${health.storageInfo.containerName}` : ''}
          tone={health?.storage === 'DOWN' ? 'danger' : 'default'}
        />
      </section>

      <section className="grid-2">
        <div>
          <h2 className="section-title">빠른 업로드</h2>
          <UploadZone onUploaded={onChanged} />
        </div>

        <div className="card">
          <div className="card-header">
            <h2 className="section-title">최근 업로드</h2>
            <button className="btn btn-ghost btn-sm" onClick={onViewAll}>
              전체 보기
            </button>
          </div>
          {summary && summary.recentFiles.length === 0 && <p className="empty">아직 업로드된 파일이 없습니다.</p>}
          <ul className="recent-list">
            {summary?.recentFiles.map((f) => (
              <li key={f.id}>
                <FileTypeBadge extension={f.extension} />
                <div className="recent-meta">
                  <a className="recent-name truncate" href={api.downloadUrl(f.id)} title={`${f.originalName} 다운로드`}>
                    {f.originalName}
                  </a>
                  <span className="muted">
                    {formatBytes(f.size)} · {formatDate(f.uploadedAt)}
                  </span>
                </div>
              </li>
            ))}
          </ul>
        </div>
      </section>
    </>
  );
}

function StatCard({
  icon,
  label,
  value,
  hint,
  tone = 'default',
}: {
  icon: IconName;
  label: string;
  value: string;
  hint: string;
  tone?: 'default' | 'danger';
}) {
  return (
    <div className={`card stat stat-${tone}`}>
      <div className="stat-icon">
        <Icon name={icon} size={18} />
      </div>
      <div className="stat-body">
        <span className="stat-label">{label}</span>
        <span className="stat-value">{value}</span>
        <span className="stat-hint truncate" title={hint}>
          {hint}
        </span>
      </div>
    </div>
  );
}
