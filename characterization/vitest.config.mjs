// 동작 보존 테스트 설정.
// - 테스트는 한 파일씩, 한 케이스씩 순서대로 돈다(대상 서비스에 동시에 요청을 쏟지 않는다).
// - 스냅샷은 characterization/__snapshots__/<테스트 파일명>.snap 에 모은다.
// - 스냅샷 직렬화 형식을 고정해 vitest 버전이 바뀌어도 파일 내용이 흔들리지 않게 한다.
import { basename, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

const root = fileURLToPath(new URL('.', import.meta.url));

export default defineConfig({
  test: {
    include: ['tests/**/*.test.js'],
    fileParallelism: false,
    sequence: { concurrent: false, shuffle: false },
    testTimeout: 15_000,
    hookTimeout: 15_000,
    resolveSnapshotPath: (testPath, snapExtension) =>
      join(root, '__snapshots__', basename(testPath) + snapExtension),
    snapshotFormat: {
      printBasicPrototype: false,
      escapeString: false,
    },
  },
});
