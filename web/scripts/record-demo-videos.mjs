import { chromium } from 'playwright';
import { mkdir } from 'node:fs/promises';

const baseURL = process.env.HUB_VIDEO_BASE_URL ?? 'http://127.0.0.1:5173';
const outDir = process.env.HUB_VIDEO_OUT_DIR ?? 'demo-videos';
await mkdir(outDir, { recursive: true });
await mkdir(`${outDir}/raw`, { recursive: true });

const ADMIN_ROUTES = [
  '/',
  '/admin/members',
  '/review',
  '/admin/reassign',
  '/todos',
  '/search',
  '/ask',
  '/context',
  '/documents',
  '/meetings',
  '/sheets',
  '/connectors',
  '/account',
  '/admin/users',
  '/admin/organization',
  '/admin/search',
  '/admin/security',
  '/admin/history',
];

const MEMBER_ROUTES = [
  '/',
  '/todos',
  '/search',
  '/ask',
  '/context',
  '/documents',
  '/meetings',
  '/sheets',
  '/connectors',
  '/account',
];

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function waitAndShow(page, ms = 2800) {
  // Intentionally keep the waiting/loading state in the recording instead of cutting past it.
  await sleep(550);
  await sleep(ms);
}

async function navigateLikeUser(page, path) {
  const current = new URL(page.url()).pathname;
  if (current === path) return;

  const link = page.locator(`a[href="${path}"]:visible`).first();
  if (await link.count()) {
    await link.scrollIntoViewIfNeeded();
    await sleep(500);
    await link.click();
    await page.waitForURL((url) => url.pathname === path, { timeout: 10_000 });
  } else {
    await page.goto(baseURL + path, { waitUntil: 'domcontentloaded' });
  }
  await waitAndShow(page);
}

async function scrollWholePage(page) {
  const height = await page.evaluate(() => document.documentElement.scrollHeight);
  const viewport = page.viewportSize()?.height ?? 900;
  const maxY = Math.max(0, height - viewport);
  const step = Math.max(430, Math.floor(viewport * 0.62));

  for (let y = 0; y <= maxY; y += step) {
    await page.evaluate((top) => window.scrollTo({ top, behavior: 'smooth' }), y);
    await sleep(900);
  }
  if (maxY > 0) {
    await page.evaluate((top) => window.scrollTo({ top, behavior: 'smooth' }), maxY);
    await sleep(1200);
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
    await sleep(900);
  }
}

async function clickVisible(page, locator, after = 2600) {
  if (!(await locator.count())) return false;
  await locator.scrollIntoViewIfNeeded();
  await sleep(650);
  await locator.click();
  await waitAndShow(page, after);
  return true;
}

async function showReviewTabs(page) {
  for (const label of ['할 일', '결정', '변경 이력']) {
    const tab = page.getByRole('tab', { name: label, exact: true });
    if (await tab.count()) {
      await clickVisible(page, tab, 1800);
      await scrollWholePage(page);
    }
  }
}

async function showTodoViews(page) {
  const trash = page.getByRole('button', { name: /휴지통$/, exact: false }).first();
  if (await trash.count()) {
    await clickVisible(page, trash, 1800);
    await scrollWholePage(page);
    const back = page.getByRole('button', { name: '할 일로 돌아가기', exact: true });
    if (await back.count()) await clickVisible(page, back, 1800);
  }
}

async function showDocumentViews(page) {
  for (const filter of ['보관함', '전체', '사용 중']) {
    const button = page.getByRole('button', { name: filter, exact: true });
    if (await button.count()) await clickVisible(page, button, 1400);
  }
  for (const view of ['카드', '달력', '목록']) {
    const button = page.getByRole('button', { name: view, exact: true });
    if (await button.count()) {
      await clickVisible(page, button, 1400);
      await scrollWholePage(page);
    }
  }
}

async function adminCollaboration(page, path) {
  if (path === '/admin/members') {
    const applicant = page.getByText('최민지', { exact: false }).first();
    if (await applicant.count()) {
      const item = applicant.locator('xpath=ancestor::li[1]');
      const approve = item.getByRole('button', { name: '승인', exact: true });
      await clickVisible(page, approve, 2600);
    }
  }

  if (path === '/review') {
    const candidate = page.getByText('회의록에서 추출된 예산 검토', { exact: false }).first();
    if (await candidate.count()) {
      const card = candidate.locator('xpath=ancestor::div[contains(@class,"rounded-md")][1]');
      const combo = card.getByRole('combobox').first();
      if (await combo.count()) {
        await combo.click();
        await sleep(700);
        const option = page.getByRole('option', { name: /박준호/ }).first();
        if (await option.count()) await option.click();
        await sleep(700);
      }
      const confirm = card.getByRole('button', { name: '확정', exact: true });
      await clickVisible(page, confirm, 2800);
    }
    await showReviewTabs(page);
  }

  if (path === '/admin/reassign') {
    const rowText = page.getByText('온보딩 인터뷰 3건 예약', { exact: false }).first();
    if (await rowText.count()) {
      const row = rowText.locator('xpath=ancestor::li[1]');
      const combo = row.getByRole('combobox').first();
      if (await combo.count()) {
        await combo.click();
        await sleep(700);
        const option = page.getByRole('option', { name: /박준호/ }).first();
        if (await option.count()) await option.click();
        await sleep(700);
      }
      const reassign = row.getByRole('button', { name: '재배정', exact: true });
      await clickVisible(page, reassign, 2800);
    }
  }

  if (path === '/todos') {
    const task = page.getByText('검색 결과 출처 표시 최종 점검', { exact: false }).first();
    if (await task.count()) {
      const card = task.locator('xpath=ancestor::div[contains(@class,"rounded-md")][1]');
      const approve = card.getByRole('button', { name: '승인', exact: true });
      await clickVisible(page, approve, 2800);
    }
    await showTodoViews(page);
  }
}

async function memberCollaboration(page, path) {
  if (path !== '/todos') return;

  const task = page.getByText('검색 결과 카드에 출처 배지 추가', { exact: false }).first();
  if (await task.count()) {
    const card = task.locator('xpath=ancestor::div[contains(@class,"rounded-md")][1]');
    const resume = card.getByRole('button', { name: /도움 받음/ }).first();
    if (await resume.count()) {
      await clickVisible(page, resume, 2800);
      const refreshedTask = page.getByText('검색 결과 카드에 출처 배지 추가', { exact: false }).first();
      const refreshedCard = refreshedTask.locator('xpath=ancestor::div[contains(@class,"rounded-md")][1]');
      const completion = refreshedCard.getByRole('button', { name: '완료 요청', exact: true });
      await clickVisible(page, completion, 3000);
    }
  }

  await showTodoViews(page);
}

async function exerciseSearchAndAi(page, path) {
  if (path === '/search') {
    const input = page.locator('input:visible').first();
    if (await input.count()) {
      await input.fill('베타 일정');
      await sleep(900);
      const button = page.getByRole('button', { name: /찾기|검색/ }).first();
      await clickVisible(page, button, 3500);
      await scrollWholePage(page);
    }
  }

  if (path === '/ask') {
    const input = page.locator('input:visible').first();
    if (await input.count()) {
      await input.fill('베타 일정이 왜 바뀌었어?');
      await sleep(900);
      const button = page.getByRole('button', { name: /답변|질문|묻기/ }).first();
      await clickVisible(page, button, 4200);
      await scrollWholePage(page);
    }
  }

  if (path === '/context') {
    const input = page.locator('input:visible').first();
    if (await input.count()) {
      await input.fill('로그인 방식 개선');
      await sleep(900);
      const button = page.getByRole('button', { name: /찾기|모아보기|검색/ }).first();
      await clickVisible(page, button, 3900);
      await scrollWholePage(page);
    }
  }
}

async function exercisePage(page, role, path) {
  if (role === 'ADMIN') await adminCollaboration(page, path);
  else await memberCollaboration(page, path);

  await exerciseSearchAndAi(page, path);

  if (path === '/documents') await showDocumentViews(page);
  if (path === '/review' && role !== 'ADMIN') await showReviewTabs(page);

  await scrollWholePage(page);
  await sleep(1300);
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
    if (sessionStorage.getItem('hub.video.seeded') === 'true') return;
    localStorage.setItem('hub.mock.logged-out', 'true');
    localStorage.setItem('hub.mock.role', role);
    localStorage.removeItem('hub.currentProjectId');
    sessionStorage.setItem('hub.video.seeded', 'true');
  }, { role });

  const page = await context.newPage();
  let activePath = '/login';
  const pageErrors = [];
  page.on('pageerror', (error) => {
    const message = `[${role}] ${activePath}: ${error.stack ?? error.message}`;
    pageErrors.push(message);
    console.error(message);
  });

  await page.goto(baseURL + '/login', { waitUntil: 'domcontentloaded' });
  await waitAndShow(page, 1300);

  // Type credentials visibly instead of filling them instantaneously.
  await page.getByLabel('아이디 또는 이메일').pressSequentially(role === 'ADMIN' ? 'demo-admin' : 'demo-member', { delay: 90 });
  await page.getByLabel('비밀번호').pressSequentially('video-demo-password', { delay: 65 });
  await sleep(900);
  await page.getByRole('button', { name: '로그인' }).click();
  await page.waitForURL((url) => url.pathname === '/', { timeout: 10_000 });
  await waitAndShow(page, 2600);

  for (const path of routes) {
    activePath = path;
    console.log(`[${role}] recording ${path}`);
    await navigateLikeUser(page, path);
    await exercisePage(page, role, path);
    if (pageErrors.length > 0) throw new Error(pageErrors.join('\n\n'));
  }

  const video = page.video();
  await context.close();
  if (!video) throw new Error('Playwright video was not created.');
  await video.saveAs(`${outDir}/${filename}.webm`);
  await browser.close();
}

await record('ADMIN', ADMIN_ROUTES, 'hub-admin-full-demo');
await record('MEMBER', MEMBER_ROUTES, 'hub-member-full-demo');
