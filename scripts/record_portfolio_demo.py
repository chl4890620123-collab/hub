#!/usr/bin/env python3
"""
Record reproducible portfolio demo clips from the real Hub React/Spring application.

The workflow starts Hub with HUB_DEMO_MODE=true on an isolated H2 database, so this script can
exercise the actual UI and permissions without touching production data. It intentionally records
separate clips for:
  1) administrator workflow,
  2) search success vs. no-result behavior,
  3) member workflow,
  4) administrator completion approval,
  5) short portfolio explanation cards.
"""
from __future__ import annotations

import os
import re
import shutil
from pathlib import Path
from typing import Callable

from playwright.sync_api import Browser, Page, TimeoutError as PlaywrightTimeoutError, sync_playwright

BASE_URL = os.getenv("HUB_DEMO_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
ADMIN_ID = os.getenv("HUB_DEMO_ADMIN_LOGIN_ID", "video-admin")
MEMBER_ID = os.getenv("HUB_DEMO_MEMBER_LOGIN_ID", "video-member")
ADMIN_PASSWORD = os.environ["HUB_DEMO_ADMIN_PASSWORD"]
MEMBER_PASSWORD = os.environ["HUB_DEMO_MEMBER_PASSWORD"]
OUT_DIR = Path(os.getenv("HUB_DEMO_VIDEO_DIR", "artifacts/portfolio-demo/raw")).resolve()
VIEWPORT = {"width": 1600, "height": 900}

OUT_DIR.mkdir(parents=True, exist_ok=True)


def wait(page: Page, ms: int = 1200) -> None:
    try:
        page.wait_for_load_state("networkidle", timeout=8_000)
    except PlaywrightTimeoutError:
        pass
    page.wait_for_timeout(ms)


def goto(page: Page, path: str, ms: int = 1200) -> None:
    page.goto(f"{BASE_URL}{path}", wait_until="domcontentloaded")
    wait(page, ms)


def add_caption(page: Page, title: str, subtitle: str = "", ms: int = 2300) -> None:
    page.evaluate(
        """({title, subtitle}) => {
          document.getElementById('__portfolio_caption')?.remove();
          const el = document.createElement('div');
          el.id = '__portfolio_caption';
          el.style.cssText = [
            'position:fixed','left:36px','bottom:34px','z-index:2147483647',
            'max-width:720px','padding:16px 20px','border-radius:14px',
            'background:rgba(13,18,28,.92)','box-shadow:0 14px 38px rgba(0,0,0,.24)',
            'color:white','font-family:system-ui,-apple-system,BlinkMacSystemFont,"Noto Sans KR",sans-serif',
            'pointer-events:none','backdrop-filter:blur(8px)'
          ].join(';');
          const h = document.createElement('div');
          h.textContent = title;
          h.style.cssText = 'font-size:24px;font-weight:800;line-height:1.25;letter-spacing:-.02em';
          el.appendChild(h);
          if (subtitle) {
            const p = document.createElement('div');
            p.textContent = subtitle;
            p.style.cssText = 'margin-top:6px;font-size:14px;line-height:1.55;color:#d8dee9';
            el.appendChild(p);
          }
          document.body.appendChild(el);
        }""",
        {"title": title, "subtitle": subtitle},
    )
    page.wait_for_timeout(ms)
    page.evaluate("document.getElementById('__portfolio_caption')?.remove()")


def login(page: Page, identifier: str, password: str, role_name: str) -> None:
    goto(page, "/login", 700)
    add_caption(page, f"{role_name} 로그인", "같은 서비스에서도 역할에 따라 보이는 기능과 결정 권한이 달라집니다.", 1600)
    page.get_by_label("아이디 또는 이메일").fill(identifier)
    page.get_by_label("비밀번호").fill(password)
    page.get_by_role("button", name="로그인", exact=True).click()
    page.wait_for_url(lambda url: "/login" not in url, timeout=15_000)
    wait(page, 1400)


def record(browser: Browser, filename: str, action: Callable[[Page], None]) -> None:
    temp_dir = OUT_DIR / f".{filename}"
    if temp_dir.exists():
        shutil.rmtree(temp_dir)
    temp_dir.mkdir(parents=True)
    context = browser.new_context(
        viewport=VIEWPORT,
        record_video_dir=str(temp_dir),
        record_video_size=VIEWPORT,
        color_scheme="light",
    )
    page = context.new_page()
    page.set_default_timeout(12_000)
    try:
        action(page)
    finally:
        video = page.video
        context.close()
    if video is None:
        raise RuntimeError(f"Playwright did not create video for {filename}")
    source = Path(video.path())
    target = OUT_DIR / f"{filename}.webm"
    if target.exists():
        target.unlink()
    shutil.move(str(source), target)
    shutil.rmtree(temp_dir, ignore_errors=True)
    print(f"recorded {target}")


def card_page(page: Page, kicker: str, title: str, body: str, chips: list[str], ms: int = 4200) -> None:
    chip_html = "".join(f"<span>{c}</span>" for c in chips)
    page.set_content(
        f"""<!doctype html><html lang="ko"><head><meta charset="utf-8">
        <style>
          *{{box-sizing:border-box}} body{{margin:0;width:100vw;height:100vh;display:flex;align-items:center;
          justify-content:center;background:linear-gradient(135deg,#0f172a,#1e293b 55%,#334155);color:white;
          font-family:system-ui,-apple-system,BlinkMacSystemFont,'Noto Sans KR',sans-serif}}
          main{{width:1260px;padding:72px 82px;border:1px solid rgba(255,255,255,.12);border-radius:34px;
          background:rgba(15,23,42,.76);box-shadow:0 28px 90px rgba(0,0,0,.3)}}
          .k{{font-size:24px;font-weight:700;color:#93c5fd;margin-bottom:18px}} h1{{font-size:58px;line-height:1.13;
          letter-spacing:-.045em;margin:0 0 24px}} p{{font-size:25px;line-height:1.65;color:#dbeafe;margin:0;max-width:1080px}}
          .chips{{display:flex;flex-wrap:wrap;gap:12px;margin-top:34px}} .chips span{{font-size:18px;padding:10px 16px;
          border-radius:999px;background:rgba(255,255,255,.1);border:1px solid rgba(255,255,255,.12)}}
        </style></head><body><main><div class="k">{kicker}</div><h1>{title}</h1><p>{body}</p>
        <div class="chips">{chip_html}</div></main></body></html>"""
    )
    page.wait_for_timeout(ms)


def intro(page: Page) -> None:
    card_page(
        page,
        "PORTFOLIO · HUB",
        "회의와 문서를 실제 업무로 연결하는 AI 협업 워크플로",
        "검색·문서·회의·할 일을 한 프로젝트 안에서 연결하고, AI가 만든 후보는 관리자가 확정한 뒤 실제 업무로 전환합니다.",
        ["관리자 / 일반 사용자", "근거 기반 검색", "AI 후보 → 관리자 확정", "업무 제출 → 완료 승인"],
        5200,
    )


def admin_flow(page: Page) -> None:
    login(page, ADMIN_ID, ADMIN_PASSWORD, "관리자")
    add_caption(page, "관리자 대시보드", "가입 대기, 재배정, 프로젝트 참여자와 최근 업무 흐름을 한 화면에서 확인합니다.")
    page.mouse.wheel(0, 360)
    page.wait_for_timeout(1100)

    goto(page, "/admin/members")
    add_caption(page, "1. 가입 승인", "일반 사용자의 가입 신청을 확인하고 프로젝트까지 배정합니다.")
    applicant = page.locator("li").filter(has_text="최민지").first
    if applicant.count() and applicant.is_visible():
        applicant.scroll_into_view_if_needed()
        page.wait_for_timeout(800)
        approve = applicant.get_by_role("button", name="승인", exact=True)
        if approve.count() and approve.is_enabled():
            approve.click()
            try:
                page.get_by_text(re.compile("가입을 승인")).wait_for(timeout=6_000)
            except PlaywrightTimeoutError:
                pass
            wait(page, 900)

    goto(page, "/meetings")
    add_caption(page, "2. 회의 → AI 후보", "녹음·오디오를 STT로 변환하고 할 일, 담당자, 기한 후보를 추출합니다.")
    page.mouse.wheel(0, 420)
    page.wait_for_timeout(1300)

    goto(page, "/review")
    add_caption(page, "3. AI 검토함", "AI 결과는 바로 업무가 되지 않습니다. 관리자가 근거를 보고 최종 담당자와 기한을 확정합니다.", 2800)
    confirm = page.get_by_role("button", name="확정", exact=True).first
    if confirm.count() and confirm.is_visible() and confirm.is_enabled():
        confirm.click()
        try:
            page.get_by_text("할 일을 확정했습니다.").wait_for(timeout=6_000)
        except PlaywrightTimeoutError:
            pass
        wait(page, 1000)

    goto(page, "/todos")
    add_caption(page, "4. 확정된 업무", "확정된 항목만 할 일·일정으로 이동합니다. 완료는 담당자 제출 후 관리자가 승인합니다.")
    todo_title = page.get_by_text("[촬영] 회의 후 베타 일정 공지", exact=True)
    if todo_title.count():
        todo_title.first.scroll_into_view_if_needed()
    page.wait_for_timeout(1600)

    goto(page, "/documents")
    add_caption(page, "5. 문서 요약과 원본", "문서 목록에서 AI 요약, 원본 다운로드, 수정·버전 비교·외부 전송을 분리해 제공합니다.")
    page.wait_for_timeout(1400)

    goto(page, "/admin/reassign")
    add_caption(page, "6. 담당자 재배정", "담당자가 프로젝트에서 빠져도 업무를 잃지 않고 관리자 재배정 목록으로 넘깁니다.")
    page.wait_for_timeout(1400)

    goto(page, "/sheets")
    add_caption(page, "7. 공유 자료표", "프로젝트 안에서 팀이 함께 쓰는 표와 업무 자료를 별도 공간에서 관리합니다.", 1800)

    goto(page, "/connectors")
    add_caption(page, "8. 연결 서비스", "GitHub·Google Drive·Slack·Notion 자료를 프로젝트 검색 범위로 연결할 수 있습니다.", 2200)


def search_comparison(page: Page) -> None:
    login(page, ADMIN_ID, ADMIN_PASSWORD, "관리자")
    goto(page, "/search")
    add_caption(page, "검색 비교", "먼저 실제 프로젝트 문서에 존재하는 키워드를 검색합니다.", 1900)
    box = page.get_by_placeholder("예: 계약서, 전달받은 파일 이름, API 변경")
    box.fill("Atlas 베타")
    page.get_by_role("button", name="검색", exact=True).click()
    try:
        page.get_by_text(re.compile("촬영용 Atlas 베타 운영 계획|Atlas")).first.wait_for(timeout=8_000)
    except PlaywrightTimeoutError:
        pass
    wait(page, 900)
    add_caption(page, "검색됨 · 근거가 있는 자료", "프로젝트에서 볼 권한이 있는 Hub 문서·첨부파일·연결 자료만 결과로 보여줍니다.", 2600)

    box.fill("존재하지않는촬영검색어-20261005")
    page.get_by_role("button", name="검색", exact=True).click()
    try:
        page.get_by_text("선택한 범위에서 검색 결과가 없습니다.").wait_for(timeout=8_000)
    except PlaywrightTimeoutError:
        pass
    wait(page, 700)
    add_caption(page, "검색 안 됨 · 근거가 없는 경우", "없는 자료를 억지로 만들어내지 않고 결과 없음으로 분리합니다.", 2600)

    goto(page, "/admin/search")
    add_caption(page, "관리자 검색 규칙", "관리자는 대표 문서 규칙과 검색 데이터 준비 상태를 확인하고 필요하면 다시 만들 수 있습니다.", 2500)
    test_input = page.get_by_placeholder("테스트 검색어")
    if test_input.count():
        test_input.fill("Atlas 베타 일정")
        page.get_by_role("button", name="테스트", exact=True).click()
        wait(page, 1300)


def member_flow(page: Page) -> None:
    login(page, MEMBER_ID, MEMBER_PASSWORD, "일반 사용자")
    add_caption(page, "일반 사용자 대시보드", "본인이 참여한 프로젝트와 담당 업무를 중심으로 필요한 정보만 확인합니다.", 2400)

    goto(page, "/search")
    box = page.get_by_placeholder("예: 계약서, 전달받은 파일 이름, API 변경")
    box.fill("Atlas 베타")
    page.get_by_role("button", name="검색", exact=True).click()
    wait(page, 1000)
    add_caption(page, "1. 프로젝트 범위 검색", "같은 검색 화면이라도 사용자가 볼 수 있는 프로젝트 자료와 파일만 노출됩니다.", 2400)

    goto(page, "/todos")
    add_caption(page, "2. 담당 업무 시작", "관리자가 확정한 업무를 담당자가 시작하고 상태를 직접 갱신합니다.", 2000)
    title = page.get_by_text("[촬영] 회의 후 베타 일정 공지", exact=True)
    if title.count():
        title.first.scroll_into_view_if_needed()
        card = title.first.locator("xpath=ancestor::div[contains(@class,'rounded-md')][1]")
        start_btn = card.get_by_role("button", name=re.compile("시작 전.*진행 중"))
        if start_btn.count() and start_btn.is_enabled():
            start_btn.click()
            wait(page, 1100)
        add_caption(page, "3. 완료 제출", "담당자는 결과 URL이나 첨부파일을 근거로 제출하고, 스스로 완료 확정할 수는 없습니다.", 2100)
        complete = card.get_by_role("button", name="완료 제출", exact=True)
        if complete.count() and complete.is_visible():
            complete.click()
            dialog = page.get_by_role("dialog")
            try:
                dialog.get_by_placeholder(re.compile("https://")).fill("https://example.com/hub-demo-result")
                dialog.get_by_role("button", name="관리자에게 제출", exact=True).click()
                wait(page, 1200)
            except PlaywrightTimeoutError:
                pass

    blocked = page.get_by_text("[촬영] 검색 결과 출처 배지 추가", exact=True)
    if blocked.count():
        blocked.first.scroll_into_view_if_needed()
        add_caption(page, "4. 도움 필요", "막힌 업무는 담당자가 도움 요청 상태와 사유를 남겨 같은 프로젝트 팀에 공유할 수 있습니다.", 2400)

    goto(page, "/documents")
    add_caption(page, "5. 자료 확인", "일반 사용자는 프로젝트 문서의 요약과 원본을 확인하되 관리자 전용 결정 권한은 갖지 않습니다.", 2200)

    goto(page, "/meetings")
    add_caption(page, "6. 회의 입력", "직접 녹음하거나 오디오 파일을 드래그해 올려 다음 업무 후보를 만들 수 있습니다.", 2200)

    goto(page, "/sheets")
    add_caption(page, "7. 공유 자료표", "프로젝트 구성원이 같은 자료표를 기준으로 협업합니다.", 1800)

    goto(page, "/notifications")
    add_caption(page, "8. 알림", "업무·승인·연결 서비스 이벤트는 문서와 섞지 않고 알림으로 분리합니다.", 2200)


def admin_finish(page: Page) -> None:
    login(page, ADMIN_ID, ADMIN_PASSWORD, "관리자")
    goto(page, "/todos")
    title = page.get_by_text("[촬영] 회의 후 베타 일정 공지", exact=True)
    if title.count():
        title.first.scroll_into_view_if_needed()
        card = title.first.locator("xpath=ancestor::div[contains(@class,'rounded-md')][1]")
        add_caption(page, "최종 승인", "담당자가 제출한 업무는 '완료 승인 대기'가 되고 관리자만 최종 완료 처리할 수 있습니다.", 2500)
        approve = card.get_by_role("button", name="승인", exact=True)
        if approve.count() and approve.is_visible():
            approve.click()
            wait(page, 1200)
            add_caption(page, "업무 사이클 완료", "회의 → AI 후보 → 관리자 확정 → 담당자 수행·제출 → 관리자 완료 승인", 2600)

    goto(page, "/context")
    add_caption(page, "관련 업무 모아보기", "문서·결정·변경·할 일을 하나의 업무 맥락으로 묶어 다음 행동을 찾기 쉽게 합니다.", 2200)

    goto(page, "/admin/history")
    add_caption(page, "관리 이력", "승인·배정·상태 변경처럼 중요한 결정은 이력으로 남겨 추적 가능하게 합니다.", 2200)


def explainer(page: Page) -> None:
    card_page(
        page,
        "설계 포인트 01",
        "AI는 결정을 대신하지 않고 후보를 만든다",
        "회의·문서에서 AI가 할 일과 결정 후보를 만들지만 실제 업무 반영은 관리자 확정 이후에만 일어납니다.",
        ["원문 근거", "AI 후보", "최종 담당자", "관리자 확정"],
        4700,
    )
    card_page(
        page,
        "설계 포인트 02",
        "검색 정확성은 권한과 대표 문서 규칙으로 높인다",
        "프로젝트 멤버십과 파일 공개 범위를 먼저 적용하고, 관리자가 지정한 대표 문서 규칙을 AI 답변의 근거 경계로 사용합니다.",
        ["프로젝트 범위", "비공개 파일", "관리자 검색 규칙", "근거 없는 답변 보류"],
        5000,
    )
    card_page(
        page,
        "설계 포인트 03",
        "업무는 제출과 승인으로 닫힌다",
        "담당자는 URL·첨부파일 같은 근거를 제출하고, 관리자가 검토해 승인하거나 보류합니다. 역할의 책임을 UI와 백엔드 권한으로 분리했습니다.",
        ["담당자 수행", "근거 제출", "관리자 검토", "완료 승인"],
        5000,
    )
    card_page(
        page,
        "운영 구조",
        "GitHub CI → Server 중앙 배포 → Docker → yellow.it.kr",
        "코드 빌드 성공뿐 아니라 공개 health와 기능 스모크 테스트까지 통과해야 배포 성공으로 판단하도록 강화합니다.",
        ["React", "Spring Boot", "FastAPI", "PostgreSQL + pgvector", "GitHub Actions"],
        5000,
    )


def main() -> None:
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        try:
            record(browser, "00_intro", intro)
            record(browser, "01_admin_flow", admin_flow)
            record(browser, "02_search_comparison", search_comparison)
            record(browser, "03_member_flow", member_flow)
            record(browser, "04_admin_finish", admin_finish)
            record(browser, "05_explainer", explainer)
        finally:
            browser.close()


if __name__ == "__main__":
    main()
