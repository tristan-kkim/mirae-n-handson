import { vi } from 'vitest';

type Route = { status?: number; body: unknown };

/**
 * 경로(`/api/...`)별 응답을 등록해 globalThis.fetch 를 가짜로 바꾼다.
 * 등록되지 않은 경로는 404 로 응답한다. 테스트가 끝나면 vi.restoreAllMocks() 로 되돌린다.
 */
export function mockFetch(routes: Record<string, Route>) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const path = new URL(url).pathname;
    const route = routes[path];
    if (!route) {
      return Promise.resolve(new Response('{"message":"not found"}', { status: 404 }));
    }
    return Promise.resolve(
      new Response(JSON.stringify(route.body), {
        status: route.status ?? 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
  });
}

/** 네트워크 자체가 실패하는 상황(서버 꺼짐)을 흉내낸다. */
export function mockFetchNetworkError() {
  return vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));
}
