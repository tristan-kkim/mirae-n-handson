/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** API 기본 URL. 없으면 http://localhost:8080 */
  readonly VITE_API_BASE?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
