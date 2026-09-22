#!/usr/bin/env node
// 베이스라인 찍기: npm run baseline -- <모듈명>
//   = vitest run -u tests/<모듈명>.test.js  (스냅샷을 새로 쓴다)
// 모듈명은 item-bank | assignment | grade.
import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const MODULES = ['item-bank', 'assignment', 'grade'];

const [module, ...extra] = process.argv.slice(2);

if (!module) {
  console.error('사용법: npm run baseline -- <모듈명>');
  console.error(`모듈명: ${MODULES.join(' | ')}`);
  process.exit(2);
}

if (!MODULES.includes(module)) {
  console.error(`알 수 없는 모듈명입니다: "${module}"`);
  console.error(`모듈명은 폴더 이름이 아니라 ${MODULES.join(' | ')} 중 하나입니다.`);
  process.exit(2);
}

const testFile = join(root, 'tests', `${module}.test.js`);
if (!existsSync(testFile)) {
  console.error(`테스트 파일이 없습니다: ${relative(process.cwd(), testFile) || testFile}`);
  console.error(`먼저 tests/${module}.test.js 를 만들어 주세요. (예시: tests/example-units.test.js, 안내: README.md)`);
  process.exit(1);
}

const vitestBin = join(root, 'node_modules', 'vitest', 'vitest.mjs');
if (!existsSync(vitestBin)) {
  console.error('vitest 가 설치돼 있지 않습니다. characterization/ 폴더에서 npm install 을 먼저 실행하세요.');
  process.exit(1);
}

console.log(`베이스라인 갱신: vitest run -u tests/${module}.test.js`);
console.log(`스냅샷 파일: __snapshots__/${module}.test.js.snap`);
const result = spawnSync(process.execPath, [vitestBin, 'run', '-u', `tests/${module}.test.js`, ...extra], {
  cwd: root,
  stdio: 'inherit',
  env: process.env,
});

if (result.error) {
  console.error(`vitest 실행 실패: ${result.error.message}`);
  process.exit(1);
}
process.exit(result.status ?? 1);
