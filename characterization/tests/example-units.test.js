// 예시 동작 보존 테스트 — 문항 은행(item-bank)의 단원 목록 화면 units.php
//
// 이 파일은 "이렇게 쓴다"를 보여 주는 예시다. 실제 실습 대상(예: 문항 검색 search.php)은
// tests/<모듈명>.test.js (예: tests/item-bank.test.js) 로 따로 만든다.
//
// 규칙
// - 대상 주소는 lib/target.mjs 가 정한다(환경 변수 TARGET_BASE_URL, 없으면 모듈의 레거시 기본 주소).
//   테스트 코드에 주소를 직접 적지 않는다.
// - 응답은 fetchNormalized 로 {status, rows, count, message} 모양으로 바꾼 뒤 스냅샷과 비교한다.
// - 기대값을 손으로 적지 않는다. 지금 시스템의 실제 응답이 기대값이다(npm run baseline -- <모듈명>).
// - 이 예시는 레거시 화면(units.php) 전용이다. TARGET_BASE_URL 로 새 API를 대상으로 돌릴 때는
//   건너뛴다(새 API에는 이 화면에 대응하는 응답이 없다). 실습에서 만드는 tests/<모듈명>.test.js 는
//   레거시와 새 API 양쪽에서 같은 결과가 나와야 하므로 이런 건너뛰기를 두지 않는다.
import { describe, expect, it } from 'vitest';
import { fetchNormalized } from '../lib/target.mjs';

const MODULE = 'item-bank';
const legacyOnly = Boolean(process.env.TARGET_BASE_URL);

describe('item-bank · 단원 목록(units.php)', () => {
  it.skipIf(legacyOnly)('단원 목록 전체 — 표의 행 · 건수 · 안내 문구', async () => {
    const result = await fetchNormalized(MODULE, '/units.php');
    expect(result).toMatchSnapshot();
  });
});
