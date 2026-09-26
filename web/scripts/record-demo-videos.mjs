import { chromium } from 'playwright';
import { mkdir } from 'node:fs/promises';

const baseURL = process.env.HUB_VIDEO_BASE_URL ?? 'http://127.0.0.1:5173';
const outDir = process.env.HUB_VIDEO_OUT_DIR ?? 'demo-videos';
await mkdir(outDir, { recursive: true });
await mkdir(`${outDir}/raw`, { recursive: true });

const ADMIN_ROUTES = [
  '/',
  '/search',
  '/ask',
  '/context',
  '/todos',
  '/review',
  '/documents',
  '/meetings',
  '/sheets',
  '/connectors',
  '/account',
  '/admin/members',
  '/admin/reassign',
  '/admin/users',
  '/admin/organization',
  '/admin/search',
  '/admin/security',
  '/admin/history',
];

const MEMBER_ROUTES = [
  '/',
  '/search',
  '/ask',
  '/context',
  '/todos',
  '/documents',
  '/meetings',
  '/sheets',
  '/connectors',
  '/account',
];

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function showWholePage(page, path) {
  await page.goto(baseURL + path, { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('networkidle').catch(() => {});
  await sleep(900);

  const height = await page.evaluate(() => document.documentElement.scrollHeight);
  const viewport = page.viewportSize()?.height ?? 900;
  const maxY = Math.max(0, height - viewport);
  const step = Math.max(500, Math.floor(viewport * 0.72));

  for (let y = 0; y <= maxY; y += step) {
    await page.evaluate((top) => window.scrollTo({ top, behavior: 'smooth' }), y);
    await sleep(450);
  }
  if (maxY > 0) {
    await page.evaluate((top) => window.scrollTo({ top, behavior: 'smooth' }), maxY);
    await sleep(650);
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
    await sleep(450);
  }
}

async function exerciseKeyFlow(page, path) {
  if (path === '/search') {
    const input = page.locator('input:visible').first();
    if (await input.count()) {
      await input.fill('베타 일정');
      const button = page.getByRole('button', { name: /찾기|검색/ }).first();
      if (await button.count()) {
        await button.click();
        await sleep(1300);
      }
    }
  }

  if (path === '/ask') {
    const input = page.locator('input:visible').first();
    if (await input.count()) {
      await input.fill('베타 일정이 왜 바뀌었어?');
      const button = page.getByRole('button', { name: /답변|질문|묻기/ }).first();
      if (await button.count()) {
        await button.click();
        await sleep(1600);
      }
    }
  }

  if (path === '/context') {
    const input = page.locator('input:visible').first();
    if (await input.count()) {
      await input.fill('로그인 방식 개선');
      const button = page.getByRole('button', { name: /찾기|모아보기|검색/ }).first();
      if (await button.count()) {
        await button.click();
        await sleep(1400);
      }
    }
  }
}

async function record(role, routes, filename) {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 1,
    recordVideo: {
      dir: `${outDir}/raw`,
      size: { width: 1440, height: 900 },
    },
  });

  await context.addInitScript(({ role }) => {
    // Seed the initial login state only once for this browser session. Re-applying
    // "logged-out" on every navigation would send the recording back to /login.
    if (sessionStorage.getItem('hub.video.seeded') === 'true') return;
    localStorage.setItem('hub.mock.logged-out', 'true');
    localStorage.setItem('hub.mock.role', role);
    localStorage.removeItem('hub.currentProjectId');
    sessionStorage.setItem('hub.video.seeded', 'true');
  }, { role });

  const page = await context.newPage();
  await page.goto(baseURL + '/login', { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('networkidle').catch(() => {});
  await sleep(900);

  await page.getByLabel('아이디 또는 이메일').fill(role === 'ADMIN' ? 'demo-admin' : 'demo-member');
  await page.getByLabel('비밀번호').fill('video-demo-password');
  await page.getByRole('button', { name: '로그인' }).click();
  await page.waitForURL((url) => url.pathname === '/', { timeout: 10_000 });
  await sleep(1000);

  for (const path of routes) {
    await showWholePage(page, path);
    await exerciseKeyFlow(page, path);
    await sleep(650);
  }

  const video = page.video();
  await context.close();
  if (!video) throw new Error('Playwright video was not created.');
  await video.saveAs(`${outDir}/${filename}.webm`);
  await browser.close();
}

await record('ADMIN', ADMIN_ROUTES, 'hub-admin-full-demo');
await record('MEMBER', MEMBER_ROUTES, 'hub-member-full-demo');
