import type { Health } from '../types';
import { Icon, type IconName } from './Icon';

export type View = 'dashboard' | 'files';

const NAV: { view: View; label: string; icon: IconName }[] = [
  { view: 'dashboard', label: '대시보드', icon: 'dashboard' },
  { view: 'files', label: '문서 관리', icon: 'files' },
];

interface Props {
  view: View;
  open: boolean;
  health: Health | null;
  onNavigate: (view: View) => void;
  onClose: () => void;
}

export function Sidebar({ view, open, health, onNavigate, onClose }: Props) {
  const status = health?.status ?? 'UNKNOWN';

  return (
    <>
      <aside className={`sidebar ${open ? 'is-open' : ''}`}>
        <div className="brand">
          <div className="brand-logo">
            <Icon name="cloud" size={18} />
          </div>
          <div>
            <div className="brand-name">CloudDocs</div>
            <div className="brand-sub">Azure Document Hub</div>
          </div>
        </div>

        <nav className="nav">
          <div className="nav-section">워크스페이스</div>
          {NAV.map((item) => (
            <button
              key={item.view}
              className={`nav-item ${view === item.view ? 'is-active' : ''}`}
              onClick={() => onNavigate(item.view)}
            >
              <Icon name={item.icon} />
              {item.label}
            </button>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="nav-section">연결 상태</div>
          <div className="conn">
            <span className={`dot dot-${status === 'UP' ? 'up' : status === 'DOWN' ? 'down' : 'unknown'}`} />
            <div className="conn-text">
              <div className="conn-title">{health?.storageInfo.provider ?? '확인 중…'}</div>
              <div className="conn-sub">
                {health ? `container: ${health.storageInfo.containerName}` : 'Blob Storage'}
              </div>
            </div>
          </div>
          {health && (
            <div className="conn-grid">
              <span>Blob</span>
              <span className={health.storage === 'UP' ? 'ok' : 'bad'}>{health.storage}</span>
              <span>MySQL</span>
              <span className={health.database === 'UP' ? 'ok' : 'bad'}>{health.database}</span>
            </div>
          )}
        </div>
      </aside>
      {open && <div className="sidebar-backdrop" onClick={onClose} />}
    </>
  );
}
