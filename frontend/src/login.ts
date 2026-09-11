const csrfToken = document.querySelector<HTMLMetaElement>('meta[name="_csrf"]')?.content ?? "";
const csrfHeader = document.querySelector<HTMLMetaElement>('meta[name="_csrf_header"]')?.content ?? "";
const memberLoginForm = document.getElementById("memberLoginForm") as HTMLFormElement | null;
const adminLoginForm = document.getElementById("adminLoginForm") as HTMLFormElement | null;
const memberSignupForm = document.getElementById("memberSignupForm") as HTMLFormElement | null;
const adminSignupForm = document.getElementById("adminSignupForm") as HTMLFormElement | null;
const message = document.getElementById("loginMessage") as HTMLElement | null;
const roleSelectPanel = document.getElementById("roleSelectPanel") as HTMLElement | null;
const authFlow = document.getElementById("authFlow") as HTMLElement | null;
const chooseRoleMember = document.getElementById("chooseRoleMember") as HTMLButtonElement | null;
const chooseRoleAdmin = document.getElementById("chooseRoleAdmin") as HTMLButtonElement | null;
const backToRoleBtn = document.getElementById("backToRoleBtn") as HTMLButtonElement | null;
const memberLoginPanel = document.getElementById("memberLoginPanel") as HTMLElement | null;
const adminLoginPanel = document.getElementById("adminLoginPanel") as HTMLElement | null;
const memberSignupPanel = document.getElementById("memberSignupPanel") as HTMLElement | null;
const adminSignupPanel = document.getElementById("adminSignupPanel") as HTMLElement | null;
const showLoginBtn = document.getElementById("showLoginBtn") as HTMLButtonElement | null;
const showMemberSignupBtn = document.getElementById("showMemberSignupBtn") as HTMLButtonElement | null;
const showAdminSignupBtn = document.getElementById("showAdminSignupBtn") as HTMLButtonElement | null;
const adminSignupSubmit = document.getElementById("adminSignupSubmit") as HTMLButtonElement | null;
const memberLoginId = document.getElementById("memberLoginId") as HTMLInputElement | null;
const adminLoginId = document.getElementById("adminLoginId") as HTMLInputElement | null;
const memberLoginIdCheckBtn = document.getElementById("memberLoginIdCheckBtn") as HTMLButtonElement | null;
const adminLoginIdCheckBtn = document.getElementById("adminLoginIdCheckBtn") as HTMLButtonElement | null;
const memberLoginIdStatus = document.getElementById("memberLoginIdStatus") as HTMLElement | null;
const adminLoginIdStatus = document.getElementById("adminLoginIdStatus") as HTMLElement | null;

if (!memberLoginForm || !adminLoginForm || !memberSignupForm || !adminSignupForm || !message || !roleSelectPanel || !authFlow
    || !chooseRoleMember || !chooseRoleAdmin || !backToRoleBtn
    || !memberLoginPanel || !adminLoginPanel || !memberSignupPanel || !adminSignupPanel
    || !showLoginBtn || !showMemberSignupBtn || !showAdminSignupBtn || !adminSignupSubmit
    || !memberLoginId || !adminLoginId || !memberLoginIdCheckBtn || !adminLoginIdCheckBtn
    || !memberLoginIdStatus || !adminLoginIdStatus) {
  throw new Error("Auth page elements are missing");
}

const memberLoginFormEl = memberLoginForm;
const adminLoginFormEl = adminLoginForm;
const memberSignupFormEl = memberSignupForm;
const adminSignupFormEl = adminSignupForm;
const messageEl = message;
const roleSelectPanelEl = roleSelectPanel;
const authFlowEl = authFlow;
const chooseRoleMemberEl = chooseRoleMember;
const chooseRoleAdminEl = chooseRoleAdmin;
const backToRoleBtnEl = backToRoleBtn;
const memberLoginPanelEl = memberLoginPanel;
const adminLoginPanelEl = adminLoginPanel;
const memberSignupPanelEl = memberSignupPanel;
const adminSignupPanelEl = adminSignupPanel;
const showLoginBtnEl = showLoginBtn;
const showMemberSignupBtnEl = showMemberSignupBtn;
const showAdminSignupBtnEl = showAdminSignupBtn;
const adminSignupSubmitEl = adminSignupSubmit;
const memberLoginIdEl = memberLoginId;
const adminLoginIdEl = adminLoginId;
const memberLoginIdCheckBtnEl = memberLoginIdCheckBtn;
const adminLoginIdCheckBtnEl = adminLoginIdCheckBtn;
const memberLoginIdStatusEl = memberLoginIdStatus;
const adminLoginIdStatusEl = adminLoginIdStatus;
type Role = "member" | "admin";
let currentRole: Role | null = null;
const requestedNextRaw = new URLSearchParams(location.search).get("next");
const requestedNext = requestedNextRaw?.startsWith("/") && !requestedNextRaw.startsWith("//") ? requestedNextRaw : null;

function headers(json = true): Record<string, string> {
  const value: Record<string, string> = json ? { "Content-Type": "application/json" } : {};
  if (csrfToken && csrfHeader) value[csrfHeader] = csrfToken;
  return value;
}

function showMessage(text: string, ok = false): void {
  messageEl.hidden = false;
  messageEl.textContent = text;
  messageEl.classList.toggle("ok", ok);
}

type PanelType = "login" | "signup";

function hashForState(role: Role | null, panel: PanelType): string {
  if (!role) return "";
  if (panel === "login") return `#${role}-login`;
  return `#${role}`;
}

function selectPanel(panel: PanelType, updateHash = true): void {
  memberLoginPanelEl.hidden = !(currentRole === "member" && panel === "login");
  adminLoginPanelEl.hidden = !(currentRole === "admin" && panel === "login");
  memberSignupPanelEl.hidden = !(currentRole === "member" && panel === "signup");
  adminSignupPanelEl.hidden = !(currentRole === "admin" && panel === "signup");
  showLoginBtnEl.classList.toggle("active", panel === "login");
  showLoginBtnEl.setAttribute("aria-selected", String(panel === "login"));
  showMemberSignupBtnEl.classList.toggle("active", panel === "signup");
  showMemberSignupBtnEl.setAttribute("aria-selected", String(panel === "signup"));
  showAdminSignupBtnEl.classList.toggle("active", panel === "signup");
  showAdminSignupBtnEl.setAttribute("aria-selected", String(panel === "signup"));
  messageEl.hidden = true;
  if (updateHash) history.replaceState(null, "", `${location.pathname}${location.search}${hashForState(currentRole, panel)}`);
}

function selectRole(role: Role, panel: PanelType = "login", updateHash = true): void {
  currentRole = role;
  roleSelectPanelEl.hidden = true;
  authFlowEl.hidden = false;
  showMemberSignupBtnEl.hidden = role !== "member";
  showAdminSignupBtnEl.hidden = role !== "admin";
  selectPanel(panel, updateHash);
}

function backToRoleSelect(updateHash = true): void {
  currentRole = null;
  authFlowEl.hidden = true;
  roleSelectPanelEl.hidden = false;
  messageEl.hidden = true;
  if (updateHash) history.replaceState(null, "", `${location.pathname}${location.search}`);
}

function applyFromHash(): void {
  switch (location.hash) {
    case "#member": selectRole("member", "signup", false); return;
    case "#admin": selectRole("admin", "signup", false); return;
    case "#member-login": selectRole("member", "login", false); return;
    case "#admin-login": selectRole("admin", "login", false); return;
    default: backToRoleSelect(false);
  }
}

async function loadSetupStatus(): Promise<void> {
  adminSignupSubmitEl.textContent = "관리자 계정 만들기";
}

type LoginIdCheckPayload = { loginId?: string; available?: boolean; message?: string };
async function checkLoginId(input: HTMLInputElement, status: HTMLElement, button: HTMLButtonElement): Promise<void> {
  const loginId = input.value.trim();
  status.classList.remove("available", "unavailable");
  if (!input.checkValidity()) {
    status.textContent = "아이디는 영문/숫자/._- 조합 4~40자로 입력해 주세요.";
    status.classList.add("unavailable");
    input.reportValidity();
    return;
  }
  button.disabled = true;
  status.textContent = "사용 가능 여부를 확인하고 있습니다...";
  try {
    const response = await fetch(`/api/auth/check-login-id?loginId=${encodeURIComponent(loginId)}`, { headers: { "Accept": "application/json" } });
    const payload = await response.json().catch(() => ({ message: "아이디를 확인하지 못했습니다." })) as LoginIdCheckPayload;
    if (!response.ok) throw new Error(payload.message || "아이디를 확인하지 못했습니다.");
    if (payload.loginId) input.value = payload.loginId;
    status.textContent = payload.message || (payload.available ? "사용 가능한 아이디입니다." : "이미 사용 중인 아이디입니다.");
    status.classList.add(payload.available ? "available" : "unavailable");
  } catch (error: unknown) {
    status.textContent = error instanceof Error ? error.message : "아이디 확인 중 오류가 발생했습니다.";
    status.classList.add("unavailable");
  } finally {
    button.disabled = false;
  }
}

function resetAvailability(status: HTMLElement): void {
  status.textContent = "영문/숫자/._- 조합 4~40자";
  status.classList.remove("available", "unavailable");
}

function bindPasswordMatch(form: HTMLFormElement): void {
  const password = form.elements.namedItem("password") as HTMLInputElement | null;
  const confirmation = form.elements.namedItem("passwordConfirm") as HTMLInputElement | null;
  const status = form.querySelector<HTMLElement>(".password-match");
  if (!password || !confirmation || !status) return;
  const update = (): void => {
    if (!confirmation.value) { status.textContent = ""; status.className = "password-match"; return; }
    const matches = password.value === confirmation.value;
    status.textContent = matches ? "비밀번호가 일치합니다." : "비밀번호가 일치하지 않습니다.";
    status.className = `password-match ${matches ? "available" : "unavailable"}`;
    confirmation.setCustomValidity(matches ? "" : "비밀번호 확인이 일치하지 않습니다.");
  };
  password.addEventListener("input", update);
  confirmation.addEventListener("input", update);
}

chooseRoleMemberEl.addEventListener("click", () => selectRole("member", "login"));
chooseRoleAdminEl.addEventListener("click", () => selectRole("admin", "login"));
backToRoleBtnEl.addEventListener("click", () => backToRoleSelect());
showLoginBtnEl.addEventListener("click", () => selectPanel("login"));
showMemberSignupBtnEl.addEventListener("click", () => selectPanel("signup"));
showAdminSignupBtnEl.addEventListener("click", () => selectPanel("signup"));
memberLoginIdCheckBtnEl.addEventListener("click", () => void checkLoginId(memberLoginIdEl, memberLoginIdStatusEl, memberLoginIdCheckBtnEl));
adminLoginIdCheckBtnEl.addEventListener("click", () => void checkLoginId(adminLoginIdEl, adminLoginIdStatusEl, adminLoginIdCheckBtnEl));
memberLoginIdEl.addEventListener("input", () => resetAvailability(memberLoginIdStatusEl));
adminLoginIdEl.addEventListener("input", () => resetAvailability(adminLoginIdStatusEl));
bindPasswordMatch(memberSignupFormEl);
bindPasswordMatch(adminSignupFormEl);
window.addEventListener("hashchange", () => applyFromHash());

async function submitLogin(form: HTMLFormElement, event: SubmitEvent): Promise<void> {
  event.preventDefault(); messageEl.hidden = true;
  const data = new FormData(form);
  try {
    const response = await fetch("/api/auth/login", { method: "POST", headers: headers(), body: JSON.stringify({ identifier: data.get("identifier"), password: data.get("password") }) });
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: "로그인에 실패했습니다." })) as { message?: string };
      throw new Error(error.message || "로그인에 실패했습니다.");
    }
    window.location.replace(requestedNext ?? "/");
  } catch (error: unknown) {
    showMessage(error instanceof Error ? error.message : "로그인에 실패했습니다.");
  }
}

memberLoginFormEl.addEventListener("submit", (event: SubmitEvent) => void submitLogin(memberLoginFormEl, event));
adminLoginFormEl.addEventListener("submit", (event: SubmitEvent) => void submitLogin(adminLoginFormEl, event));

async function submitSignup(form: HTMLFormElement, endpoint: string, extra: Record<string, unknown> = {}): Promise<void> {
  const data = new FormData(form);
  const password = String(data.get("password") ?? "");
  const loginId = String(data.get("loginId") ?? "").trim();
  if (password !== String(data.get("passwordConfirm") ?? "")) throw new Error("비밀번호 확인이 일치하지 않습니다.");
  const availabilityResponse = await fetch(`/api/auth/check-login-id?loginId=${encodeURIComponent(loginId)}`, { headers: { "Accept": "application/json" } });
  const availability = await availabilityResponse.json().catch(() => ({ available: false, message: "아이디 중복을 확인하지 못했습니다." })) as LoginIdCheckPayload;
  if (!availabilityResponse.ok || availability.available !== true) throw new Error(availability.message || "이미 사용 중인 아이디입니다.");
  const response = await fetch(endpoint, {
    method: "POST", headers: headers(), body: JSON.stringify({
      companyName: data.get("companyName"), departmentName: data.get("departmentName"), teamName: data.get("teamName"),
      displayName: data.get("displayName"), loginId, email: data.get("email"), password,
      jobTitle: data.get("jobTitle"), signupNote: data.get("signupNote"), ...extra
    })
  });
  const payload = await response.json().catch(() => ({ message: "가입 처리에 실패했습니다." })) as { message?: string; firstAdminCreated?: boolean };
  if (!response.ok) throw new Error(payload.message || "가입 처리에 실패했습니다.");
  form.reset();
  const targetLoginForm = currentRole === "admin" ? adminLoginFormEl : memberLoginFormEl;
  (targetLoginForm.elements.namedItem("identifier") as HTMLInputElement).value = loginId;
  selectPanel("login");
  const nextStep = payload.firstAdminCreated
    ? "최초 관리자 계정이 준비되었습니다. 지금 로그인하세요."
    : "가입 신청이 접수되었습니다. 관리자가 승인한 뒤 같은 아이디와 비밀번호로 로그인하세요.";
  showMessage(payload.message || nextStep, true);
  if (payload.firstAdminCreated) await loadSetupStatus();
}

memberSignupFormEl.addEventListener("submit", async (event: SubmitEvent): Promise<void> => {
  event.preventDefault(); messageEl.hidden = true;
  try { await submitSignup(memberSignupFormEl, "/api/auth/signup/member"); }
  catch (error: unknown) { showMessage(error instanceof Error ? error.message : "가입 신청 처리에 실패했습니다."); }
});

adminSignupFormEl.addEventListener("submit", async (event: SubmitEvent): Promise<void> => {
  event.preventDefault(); messageEl.hidden = true;
  try { await submitSignup(adminSignupFormEl, "/api/auth/signup/admin"); }
  catch (error: unknown) { showMessage(error instanceof Error ? error.message : "관리자 가입 처리에 실패했습니다."); }
});

applyFromHash();
void loadSetupStatus();
void resumeExistingSession();

async function resumeExistingSession(): Promise<void> {
  if (!requestedNext) return;
  try {
    let response = await fetch("/api/me", { headers: { "Accept": "application/json" } });
    if (response.ok) { window.location.replace(requestedNext ?? "/"); return; }
    if (response.status === 401) {
      const refreshed = await fetch("/api/auth/refresh", { method: "POST", headers: headers(false) });
      if (refreshed.ok) {
        response = await fetch("/api/me", { headers: { "Accept": "application/json" } });
        if (response.ok) window.location.replace(requestedNext ?? "/");
      }
    }
  } catch {
    // Offline/expired sessions simply stay on the login form.
  }
}
