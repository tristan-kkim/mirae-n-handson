export const API_BASE: string = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080';

/** HTTP 상태가 2xx 가 아닐 때 던진다. */
export class ApiError extends Error {
  readonly status: number;
  readonly path: string;

  constructor(status: number, path: string) {
    super(`API 요청 실패 (${status}) ${path}`);
    this.name = 'ApiError';
    this.status = status;
    this.path = path;
  }
}

/**
 * JSON GET 공통 함수. 모든 API 호출은 이 함수를 거친다.
 * - 경로는 `/api/...` 형태로 넘긴다.
 * - 취소는 AbortSignal 로 한다(훅의 cleanup 에서 사용).
 */
export async function getJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { Accept: 'application/json' },
    signal,
  });
  if (!response.ok) {
    throw new ApiError(response.status, path);
  }
  return (await response.json()) as T;
}
