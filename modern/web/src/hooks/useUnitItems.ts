import { fetchUnitItems } from '../api/items';
import type { Item } from '../api/types';
import { useApiQuery } from './useApiQuery';
import type { QueryState } from './useApiQuery';

/** 선택한 단원의 문항 목록. code 가 null 이면 조회하지 않는다. */
export function useUnitItems(code: string | null): QueryState<Item[]> {
  return useApiQuery(code === null ? null : `units/${code}/items`, (signal) =>
    fetchUnitItems(code ?? '', signal),
  );
}
