/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 비워두면 같은 origin의 /api 사용 (nginx 또는 Vite 프록시) */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
