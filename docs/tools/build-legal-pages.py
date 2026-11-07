#!/usr/bin/env python3
"""生成站点法律页的多语言版本。

内容来源（单一事实源）：
- docs/privacy.html / docs/terms.html            站点模板（zh-CN）
- app/src/main/assets/html/privacy-{lang}.html    应用内译文（与 APK 同步维护）
- app/src/main/assets/html/terms-{lang}.html

产出：docs/privacy-{lang}.html / docs/terms-{lang}.html（lang ∈ en/ja/ko/zh-TW/zh-HK/zh-MO）。

译文更新后重跑即可同步站点：
    python docs/tools/build-legal-pages.py

注意：站点模板中新增的区块（导航/页脚/语言切换器）若结构变化，
需同步调整本脚本的 NAV/FOOTER 映射与替换逻辑。
"""

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "html"

LANGS = ["en", "ja", "ko", "zh-TW", "zh-HK", "zh-MO"]

TITLES = {
    "privacy": {
        "en": "Privacy Policy — 忘言",
        "ja": "プライバシーポリシー — 忘言",
        "ko": "개인정보 처리방침 — 忘言",
        "zh-TW": "隱私權政策 — 忘言",
        "zh-HK": "私隱政策 — 忘言",
        "zh-MO": "私隱政策 — 忘言",
    },
    "terms": {
        "en": "Terms of Service — 忘言",
        "ja": "利用規約 — 忘言",
        "ko": "이용약관 — 忘言",
        "zh-TW": "使用者協議 — 忘言",
        "zh-HK": "用戶協議 — 忘言",
        "zh-MO": "用戶協議 — 忘言",
    },
}

NAV = {
    "en": {
        "features": "Features", "privacy": "Privacy", "tech": "Tech",
        "sponsor": "Sponsor", "feedback": "Feedback", "cta": "Get 忘言",
        "menu": "Menu", "aria_logo": "Wangyan home",
        "aria_nav": "Main navigation", "aria_menu": "Open navigation menu",
    },
    "ja": {
        "features": "機能", "privacy": "プライバシー", "tech": "技術",
        "sponsor": "スポンサー", "feedback": "フィードバック", "cta": "忘言を入手",
        "menu": "メニュー", "aria_logo": "忘言ホーム",
        "aria_nav": "メインナビゲーション", "aria_menu": "ナビゲーションメニューを開く",
    },
    "ko": {
        "features": "기능", "privacy": "개인정보", "tech": "기술",
        "sponsor": "후원", "feedback": "피드백", "cta": "忘言 받기",
        "menu": "메뉴", "aria_logo": "忘言 홈",
        "aria_nav": "주 내비게이션", "aria_menu": "내비게이션 메뉴 열기",
    },
    "zh-TW": {
        "features": "功能", "privacy": "隱私", "tech": "技術",
        "sponsor": "贊助", "feedback": "回饋", "cta": "取得忘言",
        "menu": "選單", "aria_logo": "忘言首頁",
        "aria_nav": "主導覽", "aria_menu": "開啟導覽選單",
    },
    "zh-HK": {
        "features": "功能", "privacy": "私隱", "tech": "技術",
        "sponsor": "贊助", "feedback": "意見反饋", "cta": "取得忘言",
        "menu": "選單", "aria_logo": "忘言主頁",
        "aria_nav": "主導覽", "aria_menu": "開啟導覽選單",
    },
}
NAV["zh-MO"] = NAV["zh-HK"]

FOOTER = {
    "en": {
        "copyright": "© 2026 忘言 · Based on the open-source Lumi project (GPL-3.0)",
        "home": "Home", "privacy": "Privacy Policy", "terms": "Terms of Service",
    },
    "ja": {
        "copyright": "© 2026 忘言 · オープンソースプロジェクト Lumi (GPL-3.0) ベース",
        "home": "ホーム", "privacy": "プライバシーポリシー", "terms": "利用規約",
    },
    "ko": {
        "copyright": "© 2026 忘言 · 오픈소스 프로젝트 Lumi(GPL-3.0) 기반",
        "home": "홈", "privacy": "개인정보 처리방침", "terms": "이용약관",
    },
    "zh-TW": {
        "copyright": "© 2026 忘言 · 基於開源專案 Lumi (GPL-3.0)",
        "home": "首頁", "privacy": "隱私權政策", "terms": "使用者協議",
    },
    "zh-HK": {
        "copyright": "© 2026 忘言 · 基於開源項目 Lumi (GPL-3.0)",
        "home": "主頁", "privacy": "私隱政策", "terms": "用戶協議",
    },
}
FOOTER["zh-MO"] = FOOTER["zh-HK"]

COPYRIGHT_ZH = "© 2026 忘言 · 基于开源项目 Lumi (GPL-3.0)"


def nav_block(lang: str) -> str:
    t = NAV[lang]
    return f"""<nav class="top-nav">
    <a href="index.html" class="nav-logo" aria-label="{t['aria_logo']}">
        <img src="icon.png" class="logo-icon-img" alt="忘言">
        <span class="logo-text">忘言</span>
    </a>
    <ul class="nav-links" aria-label="{t['aria_nav']}">
        <li><a href="features.html">{t['features']}</a></li>
        <li><a href="privacy-section.html" class="active" aria-current="page">{t['privacy']}</a></li>
        <li><a href="tech.html">{t['tech']}</a></li>
        <li><a href="sponsor.html">{t['sponsor']}</a></li>
        <li><a href="feedback.html">{t['feedback']}</a></li>
    </ul>
    <div class="nav-cta">
        <a href="https://github.com/a1242691089-source/wangyan/releases/latest" class="btn-nav-download">{t['cta']}</a>
    </div>
    <button class="nav-more-btn" type="button" aria-label="{t['aria_menu']}" aria-controls="lumi-nav-menu">{t['menu']}</button>
    <div class="nav-more-dropdown" id="lumi-nav-menu">
        <a href="features.html">{t['features']}</a>
        <a href="privacy-section.html" class="active" aria-current="page">{t['privacy']}</a>
        <a href="tech.html">{t['tech']}</a>
        <a href="sponsor.html">{t['sponsor']}</a>
        <a href="feedback.html">{t['feedback']}</a>
    </div>
</nav>"""


def extract_app_body(path: Path) -> str:
    text = path.read_text(encoding="utf-8")
    m = re.search(r"<body>\s*(.*?)\s*</body>", text, re.S)
    if not m:
        raise SystemExit(f"cannot locate <body> content in {path}")
    body = m.group(1).strip()
    # 应用版尾注用了内联样式（站点无 --text2 变量），统一换成 .endnote
    body = body.replace(
        ' style="font-size:13px; color:var(--text2); margin-top:28px;"',
        ' class="endnote"',
    )
    # 应用版表格无横向滚动包裹，站点版移动端需要 .table-scroll 防溢出
    body = re.sub(r"<table>.*?</table>",
                  lambda m: f'<div class="table-scroll">{m.group(0)}</div>',
                  body, flags=re.S)
    return body


def build(doc: str, lang: str) -> str:
    template = (DOCS / f"{doc}.html").read_text(encoding="utf-8")
    out = template

    # 1. lang 属性与标题
    out = out.replace('<html lang="zh-CN">', f'<html lang="{lang}">', 1)
    out = re.sub(r"<title>.*?</title>",
                 f"<title>{TITLES[doc][lang]}</title>", out, count=1)

    # 2. 导航整体替换（避免误伤正文里的同形词；re.S：导航块跨多行）
    out = re.sub(r'<nav class="top-nav">.*?</nav>',
                 lambda _: nav_block(lang), out, count=1, flags=re.S)

    # 3. 正文注入：替换 <div class="glass-card doc"> 与其闭合 </div> 之间
    start = out.index('<div class="glass-card doc">') + len('<div class="glass-card doc">')
    foot = out.index('<div class="doc-footer">')
    close = out.rindex("</div>", start, foot)
    body = extract_app_body(ASSETS / f"{doc}-{lang}.html")
    out = out[:start] + "\n" + body + "\n" + out[close:]

    # 4. 页脚文案
    f = FOOTER[lang]
    out = out.replace(f"<span>{COPYRIGHT_ZH}</span>", f"<span>{f['copyright']}</span>", 1)
    out = out.replace('<a href="index.html">首页</a>', f'<a href="index.html">{f["home"]}</a>', 1)
    out = out.replace(f'<a href="privacy.html">{FOOTER_COPYRIGHT_LINKS["privacy"]}</a>',
                      f'<a href="privacy.html">{f["privacy"]}</a>', 1)
    out = out.replace(f'<a href="terms.html">{FOOTER_COPYRIGHT_LINKS["terms"]}</a>',
                      f'<a href="terms.html">{f["terms"]}</a>', 1)

    # 5. 语言切换器当前项标记：移到当前语言
    out = out.replace(
        f'<a href="{doc}.html" data-legal-lang="zh-CN" aria-current="page">',
        f'<a href="{doc}.html" data-legal-lang="zh-CN">', 1)
    if lang != "zh-CN":
        out = out.replace(
            f'<a href="{doc}-{lang}.html" data-legal-lang="{lang}">',
            f'<a href="{doc}-{lang}.html" data-legal-lang="{lang}" aria-current="page">', 1)

    return out


FOOTER_COPYRIGHT_LINKS = {"privacy": "隐私政策", "terms": "用户协议"}


def main() -> None:
    for doc in ("privacy", "terms"):
        for lang in LANGS:
            target = DOCS / f"{doc}-{lang}.html"
            target.write_text(build(doc, lang), encoding="utf-8")
            print("wrote", target.relative_to(ROOT))


if __name__ == "__main__":
    main()
