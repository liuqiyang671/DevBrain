import { mkdirSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { build } from 'esbuild';

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const outDir = join(root, '.tmp-tests');
const tests = [
  'src/pages/shopping-guide/hooks/guideWorkbenchModel.test.ts',
];

rmSync(outDir, { recursive: true, force: true });
mkdirSync(outDir, { recursive: true });

for (const test of tests) {
  const outFile = join(outDir, test.replace(/[\\/]/g, '__').replace(/\.ts$/, '.mjs'));
  await build({
    entryPoints: [join(root, test)],
    outfile: outFile,
    bundle: true,
    platform: 'node',
    target: 'node20',
    format: 'esm',
    external: ['node:test', 'node:assert/strict'],
    logLevel: 'silent',
  });
  const result = spawnSync(process.execPath, ['--test', outFile], {
    cwd: root,
    stdio: 'inherit',
  });
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}
