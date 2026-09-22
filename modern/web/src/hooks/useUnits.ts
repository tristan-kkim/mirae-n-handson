import { fetchUnits } from '../api/items';
import type { Unit } from '../api/types';
import { useApiQuery } from './useApiQuery';
import type { QueryState } from './useApiQuery';

/** 단원 목록. 마운트 시 한 번 조회한다. */
export function useUnits(): QueryState<Unit[]> {
  return useApiQuery('units', fetchUnits);
}
