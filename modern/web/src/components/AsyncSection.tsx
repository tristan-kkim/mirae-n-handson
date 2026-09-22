import type { ReactNode } from 'react';

import type { QueryState } from '../hooks/useApiQuery';

interface AsyncSectionProps<T> {
  state: QueryState<T>;
  /** idle(아직 선택 전)일 때 보여줄 안내 문구 */
  idleMessage?: string;
  children: (data: T) => ReactNode;
}

/**
 * 조회 상태(QueryState)에 따라 로딩 · 오류 · 결과를 한 곳에서 분기한다.
 * 데이터를 어떻게 그릴지는 children 함수가 결정한다.
 */
export function AsyncSection<T>({ state, idleMessage, children }: AsyncSectionProps<T>) {
  switch (state.status) {
    case 'idle':
      return idleMessage ? <p className="hint">{idleMessage}</p> : null;
    case 'loading':
      return (
        <p className="hint" role="status">
          불러오는 중…
        </p>
      );
    case 'error':
      return (
        <p className="error" role="alert">
          {state.error}
        </p>
      );
    case 'success':
      return <>{children(state.data)}</>;
  }
}
