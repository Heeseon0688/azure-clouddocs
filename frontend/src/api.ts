import type { DashboardSummary, FileItem, Health, Page } from './types';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

export class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

/** 백엔드는 RFC 9457 Problem Details 형식({ title, detail, status })으로 오류를 반환한다. */
function messageFrom(body: unknown, status: number): string {
  if (body && typeof body === 'object' && 'detail' in body && typeof body.detail === 'string') {
    return body.detail;
  }
  return `요청 처리에 실패했습니다. (HTTP ${status})`;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response;
  try {
    res = await fetch(BASE_URL + path, init);
  } catch {
    throw new ApiError('서버에 연결할 수 없습니다.', 0);
  }
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new ApiError(messageFrom(body, res.status), res.status);
  }
  return (res.status === 204 ? undefined : await res.json()) as T;
}

export const api = {
  async health(): Promise<Health> {
    // DOWN이면 503과 함께 상세 본문이 오므로 상태 코드와 무관하게 본문을 읽는다
    const res = await fetch(`${BASE_URL}/api/health`);
    return res.json();
  },

  listFiles(keyword: string, page: number, size = 10): Promise<Page<FileItem>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (keyword.trim()) params.set('keyword', keyword.trim());
    return request(`/api/files?${params}`);
  },

  dashboard(): Promise<DashboardSummary> {
    return request('/api/dashboard/summary');
  },

  deleteFile(id: number): Promise<void> {
    return request(`/api/files/${id}`, { method: 'DELETE' });
  },

  downloadUrl(id: number): string {
    return `${BASE_URL}/api/files/${id}/download`;
  },

  /** 업로드 진행률 표시를 위해 fetch 대신 XMLHttpRequest를 사용한다. */
  uploadFile(file: File, onProgress: (percent: number) => void): Promise<FileItem> {
    return new Promise((resolve, reject) => {
      const form = new FormData();
      form.append('file', file);

      const xhr = new XMLHttpRequest();
      xhr.open('POST', `${BASE_URL}/api/files`);
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) onProgress(Math.round((e.loaded / e.total) * 100));
      };
      xhr.onload = () => {
        let body: unknown = null;
        try {
          body = JSON.parse(xhr.responseText);
        } catch {
          /* 비 JSON 응답 */
        }
        if (xhr.status === 201) resolve(body as FileItem);
        else reject(new ApiError(messageFrom(body, xhr.status), xhr.status));
      };
      xhr.onerror = () => reject(new ApiError('서버에 연결할 수 없습니다.', 0));
      xhr.send(form);
    });
  },
};
