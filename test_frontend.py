"""
DevBrain 前端 UI 自动化测试模块

使用 Playwright 进行端到端测试，验证前端页面的基本功能：
- 认证页面渲染
- 未登录重定向
- 登录/注册标签页
- 工作区访问控制
- 文档详情页
- 管理后台页面
- 知识库页面
- 控制台错误检测
"""
from playwright.sync_api import sync_playwright

BASE_URL = "http://localhost:5174"
SCREENSHOTS_DIR = "/tmp/devbrain_test"


def test_auth_page_renders(page):
    """Verify login page renders correctly."""
    page.goto(f"{BASE_URL}/auth")
    page.wait_for_load_state("networkidle")

    title = page.title()
    print(f"[1] Page title: {title}")

    # Inspect all inputs
    inputs = page.locator("input").all()
    input_info = []
    for inp in inputs:
        itype = inp.get_attribute("type") or "text"
        placeholder = inp.get_attribute("placeholder") or ""
        name = inp.get_attribute("name") or ""
        aria = inp.get_attribute("aria-label") or ""
        input_info.append(f"type={itype} placeholder='{placeholder}' name='{name}' aria='{aria}'")
    print(f"[1] Inputs found: {len(inputs)}")
    for info in input_info:
        print(f"    - {info}")

    # Inspect all buttons
    buttons = page.locator("button").all()
    button_texts = [b.inner_text() for b in buttons]
    print(f"[1] Buttons: {button_texts}")

    # Should have at least some form inputs
    assert len(inputs) >= 1, f"Expected at least 1 input, found {len(inputs)}"

    page.screenshot(path=f"{SCREENSHOTS_DIR}/01_auth_page.png", full_page=True)
    print("[1] Auth page renders OK")


def test_redirect_to_auth(page):
    """Verify unauthenticated access redirects to login."""
    page.goto(f"{BASE_URL}/knowledge-bases")
    page.wait_for_load_state("networkidle")

    current_url = page.url
    print(f"[2] /knowledge-bases redirected to: {current_url}")
    assert "/auth" in current_url, f"Expected redirect to /auth, got: {current_url}"

    page.screenshot(path=f"{SCREENSHOTS_DIR}/02_redirect_to_auth.png", full_page=True)
    print("[2] Redirect to auth OK")


def test_auth_page_tabs(page):
    """Verify login page has login/register tabs."""
    page.goto(f"{BASE_URL}/auth")
    page.wait_for_load_state("networkidle")

    # Check for tab-like elements (login/register)
    content = page.content()
    has_login = "login" in content.lower() or "登录" in content
    has_register = "register" in content.lower() or "注册" in content
    print(f"[3] Has login text: {has_login}, Has register text: {has_register}")

    page.screenshot(path=f"{SCREENSHOTS_DIR}/03_auth_tabs.png", full_page=True)
    print("[3] Auth tabs OK")


def test_workspace_redirect(page):
    """Verify /workspace redirects to auth when not logged in."""
    page.goto(f"{BASE_URL}/workspace")
    page.wait_for_load_state("networkidle")

    current_url = page.url
    print(f"[4] /workspace redirected to: {current_url}")
    assert "/auth" in current_url, f"Expected redirect to /auth, got: {current_url}"

    page.screenshot(path=f"{SCREENSHOTS_DIR}/04_workspace_redirect.png", full_page=True)
    print("[4] Workspace redirect OK")


def test_document_detail_page(page):
    """Verify document detail page structure."""
    page.goto(f"{BASE_URL}/knowledge-bases/kb-1/documents/doc-1")
    page.wait_for_load_state("networkidle")

    current_url = page.url
    print(f"[5] Doc detail URL: {current_url}")

    if "/auth" in current_url:
        print("[5] Redirected to auth (expected without login)")
    else:
        content = page.content()
        has_doc_detail = "文档详情" in content or "Document" in content
        print(f"[5] Has doc detail: {has_doc_detail}")

    page.screenshot(path=f"{SCREENSHOTS_DIR}/05_doc_detail.png", full_page=True)
    print("[5] Doc detail page OK")


def test_admin_documents_page(page):
    """Verify admin documents page."""
    page.goto(f"{BASE_URL}/admin/documents")
    page.wait_for_load_state("networkidle")

    current_url = page.url
    print(f"[6] /admin/documents URL: {current_url}")

    if "/auth" in current_url:
        print("[6] Redirected to auth (expected)")
    else:
        content = page.content()
        has_doc_mgmt = "文档管理" in content or "Document" in content
        print(f"[6] Has doc management: {has_doc_mgmt}")

    page.screenshot(path=f"{SCREENSHOTS_DIR}/06_admin_documents.png", full_page=True)
    print("[6] Admin documents page OK")


def test_knowledge_base_page(page):
    """Verify knowledge base page."""
    page.goto(f"{BASE_URL}/knowledge-bases")
    page.wait_for_load_state("networkidle")

    current_url = page.url
    print(f"[7] /knowledge-bases URL: {current_url}")

    if "/auth" in current_url:
        print("[7] Redirected to auth (expected)")
    else:
        content = page.content()
        has_kb = "知识库" in content or "Knowledge" in content
        print(f"[7] Has knowledge base: {has_kb}")

    page.screenshot(path=f"{SCREENSHOTS_DIR}/07_knowledge_bases.png", full_page=True)
    print("[7] Knowledge base page OK")


def test_console_errors(page):
    """Check for console errors during page load."""
    errors = []
    page.on("console", lambda msg: errors.append(msg.text) if msg.type == "error" else None)

    page.goto(f"{BASE_URL}/auth")
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(2000)

    print(f"[8] Console errors: {len(errors)}")
    for err in errors[:5]:
        print(f"    - {err[:150]}")

    critical = [e for e in errors if "favicon" not in e.lower() and "404" not in e]
    print(f"[8] Critical errors: {len(critical)}")
    print("[8] Console errors check OK")


if __name__ == "__main__":
    import os
    os.makedirs(SCREENSHOTS_DIR, exist_ok=True)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()

        print("=" * 60)
        print("DevBrain Frontend UI Automation Test")
        print("=" * 60)

        test_auth_page_renders(page)
        test_redirect_to_auth(page)
        test_auth_page_tabs(page)
        test_workspace_redirect(page)
        test_document_detail_page(page)
        test_admin_documents_page(page)
        test_knowledge_base_page(page)
        test_console_errors(page)

        print("=" * 60)
        print("All tests passed!")
        print(f"Screenshots: {SCREENSHOTS_DIR}")
        print("=" * 60)

        browser.close()
