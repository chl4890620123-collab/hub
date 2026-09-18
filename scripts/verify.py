#!/usr/bin/env python3
"""Fast repository sanity check for the simplified v2.34 intranet-web VS Code layout."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []
checks = 0


def check(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        errors.append(message)


def text(path: str) -> str:
    p = ROOT / path
    check(p.is_file(), f"missing file: {path}")
    return p.read_text(encoding="utf-8") if p.is_file() else ""

# Configuration shape: one dotenv template, one Spring YAML, one Compose YAML, one bundled search-rule YAML.
check((ROOT / ".env.example").is_file(), ".env.example must exist")
check(len(list(ROOT.glob(".env.*"))) == 1, "only .env.example may be packaged")
check(len(list((ROOT / "backend/src/main/resources").glob("application*.yml"))) == 1, "Spring config must be one application.yml")
check(not list(ROOT.glob("compose*.yml")), "root compose YAML must not exist; deployment config belongs under deploy/")
check((ROOT / "deploy/compose.yml").is_file(), "deploy/compose.yml missing")
check(not (ROOT / "config").exists(), "duplicate root config/ directory must not exist")
check((ROOT / "backend/src/main/resources/config/search-rules.yml").is_file(), "bundled search-rules.yml missing")
check((ROOT / "Hub.code-workspace").is_file(), "VS Code workspace file missing")

app_yml = text("backend/src/main/resources/application.yml")
check("default: local" in app_yml, "local profile must be the default")
check("jdbc:h2:file:./data/hubdb" in app_yml, "local H2 file DB config missing")
check("classpath:config/search-rules.yml" in app_yml, "search rules must use bundled classpath resource")

# Desktop-web-first UI: no separate mobile client/PWA branch, and no compact bottom-nav pattern.
# The single stylesheet may use @media to keep the same layout usable on a narrow browser window;
# that is a plain responsive layout, not the mobile-app-shaped navigation this check still forbids.
index = text("backend/src/main/resources/templates/index.html")
app_js = text("backend/src/main/resources/static/js/app.js")
for marker in ["view-search", "view-meetings", "recordBtn", "audioFileInput", "view-todos", "view-connectors", "view-admin"]:
    check(marker in index, f"web feature missing: {marker}")
for forbidden in ["mobileClient", "X-Hub-Client", "data-mobile-only", "/mobile", "/manifest.webmanifest", "/sw.js"]:
    check(forbidden not in index + app_js, f"legacy mobile/PWA branch remains: {forbidden}")
app_css = text("backend/src/main/resources/static/css/app.css")
check('data-view="search"' in index and "dataset.view" in app_js, "sidebar navigation binding is broken")
for forbidden in ["compact-bottom-nav", "compactMoreSheet", "data-compact-view", "compact-feature-grid"]:
    check(forbidden not in index + app_js + app_css, f"compact mobile navigation remains: {forbidden}")
check("window.isSecureContext" in app_js, "secure-context recording guard missing")

# Scope decisions: no camera-capture path and no LangChain dependency/runtime.
joined_runtime = index + app_js + text("backend/src/main/java/com/hub/controller/DocumentController.java") + text("backend/src/main/java/com/hub/service/DocumentService.java")
for forbidden in ["cameraForm", "uploadCamera", "CAMERA_OCR", "capture=environment", "?camera=", "@RequestParam(defaultValue = \"false\") boolean camera"]:
    check(forbidden not in joined_runtime, f"removed camera path remains: {forbidden}")
requirements = text("ai-service/requirements.txt")
main_py = text("ai-service/app/main.py")
check("langchain" not in requirements.lower() and "langchain" not in main_py.lower(), "LangChain runtime dependency remains")
check(not (ROOT / "ai-service/app/loaders").exists(), "obsolete LangChain loader directory remains")
check(not (ROOT / "ai-service/app/providers/clova_ocr.py").exists(), "unused external OCR provider remains")

# Core application preservation.
for path in [
    "backend/src/main/java/com/hub/service/MaterialSearchService.java",
    "backend/src/main/java/com/hub/service/SearchQueryRouter.java",
    "backend/src/main/java/com/hub/repository/EvidenceRepository.java",
    "backend/src/main/java/com/hub/controller/TodoController.java",
    "backend/src/main/java/com/hub/controller/RegistrationController.java",
    "backend/src/main/java/com/hub/controller/AdminController.java",
    "backend/src/main/java/com/hub/controller/ConnectorController.java",
    "backend/src/main/java/com/hub/controller/DocumentController.java",
    "backend/src/main/java/com/hub/controller/MeetingController.java",
]:
    check((ROOT / path).is_file(), f"core feature file missing: {path}")
search = text("backend/src/main/java/com/hub/service/MaterialSearchService.java")
check("RRF_K" in search and "rerankBonus" in search, "hybrid ranking/rerank path missing")
evidence = text("backend/src/main/java/com/hub/repository/EvidenceRepository.java")
check("evidence_text" in evidence and "content_hash" in evidence, "immutable evidence snapshot contract missing")
security = text("backend/src/main/java/com/hub/config/SecurityConfig.java")
check("BCryptPasswordEncoder(12)" in security, "BCrypt cost contract missing")
check("SessionCreationPolicy.STATELESS" in security, "stateless auth contract missing")
check("CookieCsrfTokenRepository" in security, "CSRF cookie contract missing")

# Packaging consistency.
gradle = text("backend/build.gradle")
docker = text("backend/Dockerfile")
ci = text(".github/workflows/ci.yml")
workspace = text("Hub.code-workspace")
check("version = '2.34.0'" in gradle, "backend version is not v2.34")
check("Hub: 1. 로컬 시작" in workspace and "HUB.bat" in workspace, "VS Code local task contract missing")
check("Hub: 전체 테스트" in workspace and "Hub: 배포 JAR 빌드" in workspace, "VS Code test/build task contract missing")
check("archiveFileName = 'hub-backend.jar'" in gradle, "bootJar must have stable name")
check("build/libs/hub-backend.jar" in docker, "Dockerfile does not use stable JAR name")
check("hub-backend.jar" in ci, "CI artifact path is not stable")

if errors:
    print(f"[VERIFY FAIL] {len(errors)}/{checks} checks failed")
    for err in errors:
        print(f" - {err}")
    sys.exit(1)
print(f"[VERIFY PASS] {checks} checks")
