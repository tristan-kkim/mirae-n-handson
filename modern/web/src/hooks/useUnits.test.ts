import { renderHook, waitFor } from '@testing-library/react';

import { units } from '../test/fixtures';
import { mockFetch } from '../test/mockFetch';
import { useUnits } from './useUnits';

describe('useUnits (fetch mock)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('loading → success 로 바뀌고 data 에 단원 배열이 들어온다', async () => {
    const fetchSpy = mockFetch({ '/api/units': { body: units } });

    const { result } = renderHook(() => useUnits());
    expect(result.current.status).toBe('loading');

    await waitFor(() => expect(result.current.status).toBe('success'));
    expect(result.current.data).toEqual(units);
    expect(result.current.error).toBeNull();

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(String(url)).toBe('http://localhost:8080/api/units');
    expect(init?.headers).toEqual({ Accept: 'application/json' });
  });

  it('5xx 응답이면 error 상태와 서버 오류 메시지를 돌려준다', async () => {
    mockFetch({ '/api/units': { status: 500, body: { message: 'boom' } } });

    const { result } = renderHook(() => useUnits());

    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.error).toBe('서버 오류 (500)');
    expect(result.current.data).toBeNull();
  });
});
