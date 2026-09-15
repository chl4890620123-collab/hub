const csrfToken = document.querySelector<HTMLMetaElement>('meta[name="_csrf"]')?.content ?? "";
const csrfHeader = document.querySelector<HTMLMetaElement>('meta[name="_csrf_header"]')?.content ?? "";
const loginForm = document.getElementById("loginForm") as HTMLFormElement | null;
const memberSignupForm = document.getElementById("memberSignupForm") as HTMLFormElement | null;
const adminSignupForm = document.getElementById("adminSignupForm") as HTMLFormElement | null;
const message = document.getElementById("loginMessage") as HTMLElement | null;
const loginPanel = document.getElementById("loginPanel") as HTMLElement | null;
const signupRolePanel = document.getElementById("signupRolePanel") as HTMLElement | null;
const memberSignupPanel = document.getElementById("memberSignupPanel") as HTMLElement | null;
const adminSignupPanel = document.getElementById("adminSignupPanel") as HTMLElement | null;
const goToSignupBtn = document.getElementById("goToSignupBtn") as HTMLButtonElement | null;
const backToLoginBtn = document.getElementById("backToLoginBtn") as HTMLButtonElement | null;
const chooseSignupMember = document.getElementById("chooseSignupMember") as HTMLButtonElement | null;
const chooseSignupAdmin = document.getElementById("chooseSignupAdmin") as HTMLButtonElement | null;
const backToRoleButtons = document.querySelectorAll<HTMLButtonElement>("[data-back-to-role]");
const adminSignupSubmit = document.getElementById("adminSignupSubmit") as HTMLButtonElement | null;
const memberLoginId = document.getElementById("memberLoginId") as HTMLInputElement | null;
const adminLoginId = document.getElementById("adminLoginId") as HTMLInputElement | null;
const memberLoginIdCheckBtn = document.getElementById("memberLoginIdCheckBtn") as HTMLButtonElement | null;
const adminLoginIdCheckBtn = document.getElementById("adminLoginIdCheckBtn") as HTMLButtonElement | null;
const memberLoginIdStatus = document.getElementById("memberLoginIdStatus") as HTMLElement | null;
const adminLoginIdStatus = document.getElementById("adminLoginIdStatus") as HTMLElement | null;

if (!loginForm || !memberSignupForm || !adminSignupForm || !message
    || !loginPanel || !signupRolePanel || !memberSignupPanel || !adminSignupPanel
    || !goToSignupBtn || !backToLoginBtn || !chooseSignupMember || !chooseSignupAdmin || !adminSignupSubmit
    || !memberLoginId || !adminLoginId || !memberLoginIdCheckBtn || !adminLoginIdCheckBtn
    || !memberLoginIdStatus || !adminLoginIdStatus) {
  throw new Error("Auth page elements are missing");
}

const loginFormEl = loginForm;
const memberSignupFormEl = memberSignupForm;
const adminSignupFormEl = adminSignupForm;
const messageEl = message;
const loginPanelEl = loginPanel;
const signupRolePanelEl = signupRolePanel;
const memberSignupPanelEl = memberSignupPanel;
const adminSignupPanelEl = adminSignupPanel;
const goToSignupBtnEl = goToSignupBtn;
const backToLoginBtnEl = backToLoginBtn;
const chooseSignupMemberEl = chooseSignupMember;
const chooseSignupAdminEl = chooseSignupAdmin;
const adminSignupSubmitEl = adminSignupSubmit;
const memberLoginIdEl = memberLoginId;
const adminLoginIdEl = adminLoginId;
const memberLoginIdCheckBtnEl = memberLoginIdCheckBtn;
const adminLoginIdCheckBtnEl = adminLoginIdCheckBtn;
const memberLoginIdStatusEl = memberLoginIdStatus;
const adminLoginIdStatusEl = adminLoginIdStatus;

type AuthScreen = "login" | "signup-role" | "signup-member" | "signup-admin";
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

function hashForScreen(screen: AuthScreen): string {
  switch (screen) {
    case "signup-role": return "#signup";
    case "signup-member": return "#signup-member";
    case "signup-admin": return "#signup-admin";
    default: return "";
  }
}

/** Login and signup are two separate screens - signup itself asks member-vs-admin one step in. */
function showScreen(screen: AuthScreen, updateHash = true): void {
  loginPanelEl.hidden = screen !== "login";
  signupRolePanelEl.hidden = screen !== "signup-role";
  memberSignupPanelEl.hidden = screen !== "signup-member";
  adminSignupPanelEl.hidden = screen !== "signup-admin";
  messageEl.hidden = true;
  if (updateHash) history.replaceState(null, "", `${location.pathname}${location.search}${hashForScreen(screen)}`);
}

function applyFromHash(): void {
  switch (location.hash) {
    case "#signup": showScreen("signup-role", false); return;
    case "#signup-member": showScreen("signup-member", false); return;
    case "#signup-admin": showScreen("signup-admin", false); return;
    default: showScreen("login", false);
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

/**
 * A native browser reveal icon (Edge/Chrome) is tied to that browser's own state, not ours, so it can
 * vanish under conditions this page never controls. Wiring our own toggle keeps "show password" a
 * plain DOM button whose behavior never changes across a failed login or any other page state.
 */
function bindPasswordToggles(): void {
  document.querySelectorAll<HTMLButtonElement>(".password-toggle").forEach(button => {
    const input = button.previousElementSibling as HTMLInputElement | null;
    if (!input || input.tagName !== "INPUT") return;
    const setState = (visible: boolean): void => {
      input.type = visible ? "text" : "password";
      button.classList.toggle("is-visible", visible);
      button.setAttribute("aria-pressed", String(visible));
      button.setAttribute("aria-label", visible ? "비밀번호 숨기기" : "비밀번호 표시");
    };
    setState(false);
    button.addEventListener("click", () => setState(input.type === "password"));
  });
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

const FIELD_LABELS: Record<string, string> = {
  displayName: "이름", jobTitle: "직급", loginId: "아이디", email: "이메일", password: "비밀번호",
  passwordConfirm: "비밀번호 확인", identifier: "아이디", privacyConsent: "개인정보 수집·이용 동의",
};

type ProjectOption = { id: number; name: string };

/** Populates the MEMBER signup project picker. A failed fetch just leaves "관리자가 나중에 배정합니다". */
async function loadSignupProjects(): Promise<void> {
  const select = document.getElementById("memberRequestedProject") as HTMLSelectElement | null;
  if (!select) return;
  try {
    const response = await fetch("/api/auth/signup/projects", { headers: { "Accept": "application/json" } });
    if (!response.ok) return;
    const projectList = await response.json() as ProjectOption[];
    for (const project of projectList) {
      const option = document.createElement("option");
      option.value = String(project.id);
      option.textContent = project.name;
      select.appendChild(option);
    }
  } catch {
    // Offline: the applicant can still submit without picking a project.
  }
}

/** Restates the browser's own rule in the wording the server uses, so both paths read the same. */
function validationMessage(field: HTMLInputElement): string {
  const label = FIELD_LABELS[field.name] ?? "입력값";
  const state = field.validity;
  if (field.name === "privacyConsent" && state.valueMissing) return "개인정보 수집·이용에 동의해야 가입할 수 있습니다.";
  if (state.valueMissing) return `${label}을(를) 입력해 주세요.`;
  if (field.name === "loginId" && (state.patternMismatch || state.tooShort))
    return "아이디는 영문/숫자/._- 조합 4~40자로 입력해 주세요.";
  if (field.name === "password" && state.tooShort) return "비밀번호는 12자 이상으로 입력해 주세요.";
  if (state.typeMismatch && field.type === "email") return "올바른 이메일을 입력해 주세요.";
  return field.validationMessage || `${label}을(를) 다시 확인해 주세요.`;
}

/**
 * A form that fails constraint validation never reaches its submit handler, so without this the
 * button looks dead: no request, no message, only a native bubble that is easy to miss.
 * 'invalid' does not bubble, so it is captured on the way down and the first failing field wins.
 */
function bindValidationFeedback(form: HTMLFormElement): void {
  form.addEventListener("invalid", (event: Event) => {
    const field = event.target as HTMLInputElement;
    if (form.querySelector(":invalid") !== field) return;
    showMessage(validationMessage(field));
  }, true);
}

bindValidationFeedback(memberSignupFormEl);
bindValidationFeedback(adminSignupFormEl);
bindValidationFeedback(loginFormEl);

goToSignupBtnEl.addEventListener("click", () => showScreen("signup-role"));
backToLoginBtnEl.addEventListener("click", () => showScreen("login"));
chooseSignupMemberEl.addEventListener("click", () => showScreen("signup-member"));
chooseSignupAdminEl.addEventListener("click", () => showScreen("signup-admin"));
backToRoleButtons.forEach(button => button.addEventListener("click", () => showScreen("signup-role")));
memberLoginIdCheckBtnEl.addEventListener("click", () => void checkLoginId(memberLoginIdEl, memberLoginIdStatusEl, memberLoginIdCheckBtnEl));
adminLoginIdCheckBtnEl.addEventListener("click", () => void checkLoginId(adminLoginIdEl, adminLoginIdStatusEl, adminLoginIdCheckBtnEl));
memberLoginIdEl.addEventListener("input", () => resetAvailability(memberLoginIdStatusEl));
adminLoginIdEl.addEventListener("input", () => resetAvailability(adminLoginIdStatusEl));
bindPasswordMatch(memberSignupFormEl);
bindPasswordMatch(adminSignupFormEl);
bindPasswordToggles();
window.addEventListener("hashchange", () => applyFromHash());

async function submitLogin(event: SubmitEvent): Promise<void> {
  event.preventDefault(); messageEl.hidden = true;
  const data = new FormData(loginFormEl);
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

loginFormEl.addEventListener("submit", (event: SubmitEvent) => void submitLogin(event));

async function submitSignup(form: HTMLFormElement, endpoint: string, extra: Record<string, unknown> = {}): Promise<void> {
  const data = new FormData(form);
  const password = String(data.get("password") ?? "");
  const loginId = String(data.get("loginId") ?? "").trim();
  const privacyConsent = data.get("privacyConsent") === "on";
  if (!privacyConsent) throw new Error("개인정보 수집·이용에 동의해야 가입할 수 있습니다.");
  if (password !== String(data.get("passwordConfirm") ?? "")) throw new Error("비밀번호 확인이 일치하지 않습니다.");
  const availabilityResponse = await fetch(`/api/auth/check-login-id?loginId=${encodeURIComponent(loginId)}`, { headers: { "Accept": "application/json" } });
  const availability = await availabilityResponse.json().catch(() => ({ available: false, message: "아이디 중복을 확인하지 못했습니다." })) as LoginIdCheckPayload;
  if (!availabilityResponse.ok || availability.available !== true) throw new Error(availability.message || "이미 사용 중인 아이디입니다.");
  const requestedProjectRaw = String(data.get("requestedProjectId") ?? "").trim();
  const response = await fetch(endpoint, {
    method: "POST", headers: headers(), body: JSON.stringify({
      displayName: data.get("displayName"), loginId, email: data.get("email"), password,
      jobTitle: data.get("jobTitle"), signupNote: data.get("signupNote"), privacyConsent,
      requestedProjectId: requestedProjectRaw ? Number(requestedProjectRaw) : null, ...extra
    })
  });
  const payload = await response.json().catch(() => ({ message: "가입 처리에 실패했습니다." })) as { message?: string; firstAdminCreated?: boolean };
  if (!response.ok) throw new Error(payload.message || "가입 처리에 실패했습니다.");
  form.reset();
  (loginFormEl.elements.namedItem("identifier") as HTMLInputElement).value = loginId;
  showScreen("login");
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
void loadSignupProjects();
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
