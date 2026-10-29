export interface FileItem {
  id: number;
  originalName: string;
  contentType: string;
  extension: string;
  size: number;
  uploadedAt: string;
  downloadUrl: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface DashboardSummary {
  totalFiles: number;
  totalSize: number;
  recentFiles: FileItem[];
}

export interface Health {
  status: 'UP' | 'DOWN';
  database: 'UP' | 'DOWN';
  storage: 'UP' | 'DOWN';
  storageInfo: {
    provider: string;
    accountName: string;
    containerName: string;
    endpoint: string;
  };
  timestamp: string;
}
