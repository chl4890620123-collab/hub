// Copies the compiled runtime/login bundles next to the Java resources that serve them, so
// `npm run build` alone is enough - no separate PowerShell step required to see the change.
import { copyFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const frontendDir = dirname(dirname(fileURLToPath(import.meta.url)));
const distDir = join(frontendDir, 'dist');
const staticJsDir = join(frontendDir, '..', 'backend', 'src', 'main', 'resources', 'static', 'js');

mkdirSync(staticJsDir, { recursive: true });
for (const file of ['hub-runtime.js', 'login.js']) {
  copyFileSync(join(distDir, file), join(staticJsDir, file));
  console.log(`copied ${file} -> backend/src/main/resources/static/js/${file}`);
}
