import { useEffect, useRef, useState } from 'react';

import { ApiError } from '../api/client';

/** 모든 조회 훅이 돌려주는 상태. status 로 분기하면 data/error 타입이 좁혀진다. */
export type QueryState<T> =
  | { status: 'idle'; data: null; error: null }
  | { status: 'loading'; data: null; error: null }
  | { status: 'success'; data: T; error: null }
  | { status: 'error'; data: null; error: string };

const IDLE: QueryState<never> = { status: 'idle', data: null, error: null };
const LOADING: QueryState<never> = { status: 'loading', data: null, error: null };

export function toErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.status === 404 ? '요청한 데이터가 없습니다' : `서버 오류 (${error.status})`;
  }
  if (error instanceof TypeError) {
    return 'API 서버에 연결할 수 없습니다';
  }
  return '알 수 없는 오류가 발생했습니다';
}

/**
 * key 가 바뀔 때마다 load 를 다시 실행하는 공통 조회 훅.
 * - key 가 null 이면 조회하지 않고 idle 로 둔다(선택 전 상태).
 * - 언마운트 · key 변경 시 이전 요청은 AbortSignal 로 취소한다.
 */
export function useApiQuery<T>(
  key: string | null,
  load: (signal: AbortSignal) => Promise<T>,
): QueryState<T> {
  const [state, setState] = useState<QueryState<T>>(IDLE);
  const loadRef = useRef(load);
  loadRef.current = load;

  useEffect(() => {
    if (key === null) {
      setState(IDLE);
      return;
    }

    const controller = new AbortController();
    setState(LOADING);

    loadRef
      .current(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) {
          setState({ status: 'success', data, error: null });
        }
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setState({ status: 'error', data: null, error: toErrorMessage(error) });
        }
      });

    return () => controller.abort();
  }, [key]);

  return state;
}
