import { fetchItem } from '../api/items';
import type { Item } from '../api/types';
import { useApiQuery } from './useApiQuery';
import type { QueryState } from './useApiQuery';

/** 문항 상세. id 가 null 이면 조회하지 않는다. */
export function useItem(id: number | null): QueryState<Item> {
  return useApiQuery(id === null ? null : `items/${id}`, (signal) => fetchItem(id ?? 0, signal));
}
