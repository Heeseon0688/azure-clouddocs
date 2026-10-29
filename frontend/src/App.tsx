import { useCallback, useEffect, useState } from 'react';
import { api } from './api';
import { Icon } from './components/Icon';
import { Sidebar, type View } from './components/Sidebar';
import { ToastProvider } from './components/Toasts';
import type { Health } from './types';
import { DashboardView } from './views/DashboardView';
import { FilesView } from './views/FilesView';

const HEALTH_POLL_MS = 30_000;

// 새로고침해도 현재 화면을 유지하도록 URL 해시(#/files)에 화면 상태를 둔다
const viewFromHash = (): View => (window.location.hash === '#/files' ? 'files' : 'dashboard');

export default function App() {
  const [view, setView] = useState<View>(viewFromHash);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [health, setHealth] = useState<Health | null>(null);
  // 업로드/삭제 후 목록·대시보드를 다시 불러오기 위한 트리거
  const [refreshKey, setRefreshKey] = useState(0);
  const refresh = useCallback(() => setRefreshKey((k) => k + 1), []);

  useEffect(() => {
    const load = () => api.health().then(setHealth).catch(() => setHealth(null));
    load();
    const timer = window.setInterval(load, HEALTH_POLL_MS);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    const onHashChange = () => setView(viewFromHash());
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  function navigate(next: View) {
    window.location.hash = next === 'files' ? '/files' : '/';
    setView(next);
    setSidebarOpen(false);
  }

  return (
    <ToastProvider>
      <div className="layout">
        <Sidebar view={view} open={sidebarOpen} health={health} onNavigate={navigate} onClose={() => setSidebarOpen(false)} />

        <div className="main">
          <div className="topbar">
            <button className="icon-btn menu-btn" onClick={() => setSidebarOpen(true)} aria-label="메뉴 열기">
              <Icon name="menu" />
            </button>
            <span className="topbar-title">CloudDocs</span>
            <button className="btn btn-ghost btn-sm" onClick={refresh}>
              <Icon name="refresh" size={14} /> 새로고침
            </button>
          </div>

          <main className="content">
            {view === 'dashboard' ? (
              <DashboardView health={health} refreshKey={refreshKey} onChanged={refresh} onViewAll={() => navigate('files')} />
            ) : (
              <FilesView refreshKey={refreshKey} onChanged={refresh} />
            )}
          </main>
        </div>
      </div>
    </ToastProvider>
  );
}
