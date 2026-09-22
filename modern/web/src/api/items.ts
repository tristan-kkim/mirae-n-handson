import { getJson } from './client';
import type { Item, Unit } from './types';

/** GET /api/units — 단원 목록 */
export function fetchUnits(signal?: AbortSignal): Promise<Unit[]> {
  return getJson<Unit[]>('/api/units', signal);
}

/** GET /api/units/{code}/items — 단원별 문항(status='A' 만 서버가 돌려준다) */
export function fetchUnitItems(code: string, signal?: AbortSignal): Promise<Item[]> {
  return getJson<Item[]>(`/api/units/${encodeURIComponent(code)}/items`, signal);
}

/** GET /api/items/{id} — 문항 상세(없으면 404) */
export function fetchItem(id: number, signal?: AbortSignal): Promise<Item> {
  return getJson<Item>(`/api/items/${id}`, signal);
}
