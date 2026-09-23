import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// Dev proxy makes the browser see http://localhost:5173/api/... as same-origin, so cookies work
// with the default SameSite=Lax and no CORS headers are needed during local development at all.
// Production builds call the real backend origin directly via VITE_API_BASE_URL (see src/api/client.ts).
export default defineConfig(({ mode }) => {
  // VITE_API_BASE_URL deliberately does NOT come from Vite's normal env resolution
  // (loadEnv merges in process.env, and an already-set shell var always wins over any .env
  // file - see Vite's own loadEnv behavior). That bit a real build here: an unrelated older
  // project had `export VITE_API_BASE_URL=http://<some other host>:8080` sitting in this
  // machine's ~/.bashrc, which silently pointed every API call at a dead host in any build run
  // from that shell, `web/.env`/`.env.local` included or not - same-origin (the empty default,
  // required by the current same-Spring-Boot-origin deployment) never had a chance to apply.
  // Read this one var directly from this project's own env files only, so a global shell
  // variable belonging to a different project can never silently override it again; a real
  // override still works the sanctioned way, by setting it in web/.env.local.
  const fileEnv = ['.env', '.env.local', `.env.${mode}`, `.env.${mode}.local`].reduce((acc, name) => {
    const path = fileURLToPath(new URL(name, import.meta.url));
    if (!existsSync(path)) return acc;
    for (const line of readFileSync(path, 'utf-8').split('\n')) {
      const match = line.match(/^\s*VITE_API_BASE_URL\s*=\s*(.*)\s*$/);
      if (match) acc = match[1].replace(/^["']|["']$/g, '');
    }
    return acc;
  }, '');

  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    define: {
      'import.meta.env.VITE_API_BASE_URL': JSON.stringify(fileEnv),
    },
    server: {
      port: 5173,
      proxy: {
        '/api': { target: 'http://localhost:8080', changeOrigin: true },
      },
    },
  };
});
