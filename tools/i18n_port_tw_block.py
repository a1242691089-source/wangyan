#!/usr/bin/env python3
"""把 zh-rTW 尾部 226 条搬到 zh-rHK / zh-rMO，并做港澳书面语归一。

设计约束（见 devdocs/i18n-coverage.md 第 4 节）：
- 值按原文偏移切片搬运，不做 XML 解析后再序列化（否则 &amp; / \\n / %% 会被改写）。
- 读写都用 utf-8 + newline=""，Windows 下不产生 CRLF。
- 插入点固定在 </resources> 之前；写后断言「除插入段外逐字节不变」。
- 术语替换只按 key 定点做，不做整文件全局 replace。
"""

import argparse
import io
import re
import sys
from xml.etree import ElementTree

RES = "app/src/main/res"
LOCALES = ["en", "ja", "ko", "zh-rCN", "zh-rHK", "zh-rMO", "zh-rTW"]

COMMENT = re.compile(r"<!--.*?-->", re.S)
ELEM = re.compile(r"<string[ \t\r\n]([^>]*)>(.*?)</string>", re.S)
NAME = re.compile(r'\bname="([^"]+)"')
NOT_TR = re.compile(r'translatable\s*=\s*"false"')
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]")

# 术语归一：只在指定 locale 的既有条目里定点替换，顺序敏感（先长后短）。
TERM_RULES = [
    ("區域網路", "局域網"),
    ("字型", "字體"),
    ("網路", "網絡"),
    ("使用者", "用戶"),
    ("上傳", "上載"),
    ("內建", "內置"),
    ("無法復原", "無法還原"),
    ("點選", "點擊"),
]

# 去粤语口语 + 两 locale 对齐：key -> 最终完整文案。
# sponsor_quote 是引用语（"請我食個飯啦😊"），保留不改。
PROSE = {
    "import_books_hint": "按匯入按鈕加入你的電子書",
    "tap_to_change_nickname": "點擊修改暱稱",
    "tap_to_add_bookmark": "閱讀時點擊書籤圖示新增",
    "home_manage_hint": "在這裡管理你的本地 PDF 和 EPUB 圖書",
    "most_read_books": "最常閱讀的書籍",
    "search_results_found": "找到 %d 條結果",
    "search_no_results": "未找到匹配結果",
    "remaining_minutes": "尚差 %d 分鐘達標",
    "welcome_and": "和",
    "welcome_language_subtitle": "選擇你所使用的語言",
    "welcome_liquid_glass_subtitle": "精心設計並適配的液態玻璃主題，讓忘言每個場景都有出色的互動體驗。",
    "welcome_liquid_glass_preview_caption": "不再是簡單的純色和模糊，而是具有玻璃形態的折射效果。",
    "welcome_bookshelf_preview_subtitle": "多選書籍和搜尋功能得到增強，書籍再多也不怕。",
    "welcome_mineru_preview_subtitle": "MinerU 讓 PDF OCR 解析更加準確。",
    "welcome_support_message": "歡迎前往「設定 > 贊助開發」支持我們。每一筆捐贈都會全數用於忘言的持續開發與完善。",
    "txt_toc_help_intro_title": "甚麼是 TXT 目錄規則？",
    "knowledge_detail_missing": "這條知識已不存在",
    "knowledge_detail_no_evidence": "這條知識還沒有原文證據",
    "knowledge_detail_no_relations": "還沒有與其他知識建立關係",
    "knowledge_detail_relation_exists": "這個關係已存在",
    "knowledge_detail_pick_type": "選擇關係類型",
    "knowledge_detail_pick_node": "選擇關聯的知識",
    "knowledge_detail_no_targets": "沒有可選的知識節點",
    "knowledge_detail_add_relation": "新增關係",
    "knowledge_detail_delete_title": "刪除這條知識？",
    "knowledge_detail_delete_body": "原文證據與關聯關係會一併刪除，無法還原。",
}

# 归一后仍允许出现的口语标记（引用语）。
YUE_MARKERS = "撳嘅喺搵唔冇哋揀掣咩咁噉嚟佢睇諗咗啲嗰"
YUE_PHRASES = ["呢條", "呢個", "仲差"]
WHITELIST = {"sponsor_quote"}


def read(path):
    """读取并把行尾归一为 LF。

    `core.autocrlf=true` 的仓库在 checkout 后工作树是 CRLF，但仓库里存的是 LF，
    项目约定也是 LF（见 devdocs/i18n-coverage.md 第 4 节）。这里允许读入 CRLF，
    写出时统一成 LF——git 归一化后不会产生额外 diff。
    """
    raw = io.open(path, encoding="utf-8", newline="").read()
    assert not raw.startswith("\ufeff"), f"{path}: 有 BOM"
    assert "\r" not in raw.replace("\r\n", ""), f"{path}: 存在孤立 CR"
    return raw.replace("\r\n", "\n")


def write(path, text):
    assert "\r" not in text, "写入内容含 CR"
    assert not text.startswith("\ufeff"), "写入内容含 BOM"
    assert text.endswith("\n"), "文件未以换行结尾"
    ElementTree.fromstring(text)  # 良构校验，不做回写
    io.open(path, "w", encoding="utf-8", newline="").write(text)


def index(text):
    """name -> {indent, tag, value, line}，注释掉的条目跳过。

    line 含前导缩进，替换整行时不会把缩进叠成 8 空格。
    """
    comments = [m.span() for m in COMMENT.finditer(text)]
    out = {}
    for m in ELEM.finditer(text):
        if any(a <= m.start() < b for a, b in comments):
            continue
        name = NAME.search(m.group(0)).group(1)
        head = m.group(0)[: m.group(0).index(">") + 1]
        indent = text[text.rfind("\n", 0, m.start()) + 1 : m.start()]
        assert indent.strip() == "", f"{name}: 行首不是纯空白"
        out[name] = {
            "start": m.start(),
            "indent": indent,
            "tag": head,
            "value": m.group(2),
            "line": indent + m.group(0),
        }
    return out


def path_of(locale):
    return f"{RES}/values-{locale}/strings.xml"


def base_translatable():
    entries = index(read(f"{RES}/values/strings.xml"))
    return {k for k, v in entries.items() if not NOT_TR.search(v["tag"])}


def report():
    base = base_translatable()
    assert len(base) == 1737, f"可翻译基数应为 1737，实际 {len(base)}"
    gaps = {}
    for loc in LOCALES:
        have = set(index(read(path_of(loc))))
        gaps[loc] = base - have
        print(f"{loc:<8} 有 {len(have):>4} 条，缺 {len(gaps[loc]):>3} 条")
    assert gaps["zh-rHK"] == gaps["zh-rMO"], "HK 与 MO 缺口集合不一致"
    assert len(gaps["zh-rHK"]) == 267, f"HK 缺口应为 267，实际 {len(gaps['zh-rHK'])}"
    tw = index(read(path_of("zh-rTW")))
    port = [k for k in tw if k in gaps["zh-rHK"]]
    assert len(port) == 226, f"可搬运条数应为 226，实际 {len(port)}"
    diag = sorted(gaps["zh-rHK"] - set(port))
    assert all(k.startswith("diagnostic_") for k in diag), f"非 diagnostic 缺口：{diag[:5]}"
    print(f"→ 可搬运 {len(port)} 条；剩 {len(diag)} 条 diagnostic_*")
    return base, gaps, tw, port


def apply_terms(value):
    for old, new in TERM_RULES:
        value = value.replace(old, new)
    return value


def port(locale, apply):
    base = base_translatable()
    assert len(base) == 1737, f"可翻译基数应为 1737，实际 {len(base)}"
    tw = index(read(path_of("zh-rTW")))
    target_path = path_of(locale)
    text = read(target_path)
    entries = index(text)

    missing = base - set(entries)
    assert len(missing) == 267, f"{locale} 缺口应为 267，实际 {len(missing)}"
    keys = [k for k in tw if k in missing]
    assert len(keys) == 226, f"可搬运条数应为 226，实际 {len(keys)}"
    diag = sorted(missing - set(keys))
    assert all(k.startswith("diagnostic_") for k in diag), f"非 diagnostic 缺口：{diag[:5]}"

    clash = set(keys) & set(entries)
    assert not clash, f"{locale} 已存在同名 key：{sorted(clash)[:5]}"

    lines = []
    for k in keys:
        src = tw[k]
        assert src["indent"] == "    ", f"{k}: 缩进异常 {src['indent']!r}"
        value = apply_terms(src["value"])
        line = f"    {src['tag']}{value}</string>"
        if value == src["value"]:
            assert line == src["line"], f"{k}: 重建行与原文不一致"
        else:
            print(f"[術語] {k}")
        assert sorted(PLACEHOLDER.findall(value)) == sorted(
            PLACEHOLDER.findall(src["value"])
        ), f"{k}: 占位符被改动"
        lines.append(line)

    block = "".join(l + "\n" for l in lines)
    idx = text.rindex("</resources>")
    assert text.count("</resources>") == 1
    new = text[:idx] + block + text[idx:]
    assert new.replace(block, "", 1) == text, "除插入段外出现了其它改动"

    assert "\r" not in new and not new.startswith("\ufeff")
    assert new.count("<!--") == text.count("<!--"), "注释数量变化"
    assert len(index(new)) == len(entries) + 226

    print(f"{locale}: {text.count(chr(10))} 行 → {new.count(chr(10))} 行")
    if apply:
        write(target_path, new)
        print(f"已写入 {target_path}")
    else:
        print("dry-run，未写入")


def normalize(locale, apply):
    target_path = path_of(locale)
    text = read(target_path)
    entries = index(text)
    new = text
    term_hits, prose_hits = 0, 0

    for k, e in sorted(entries.items(), key=lambda kv: -kv[1]["start"]):
        old_line = e["line"]
        value = e["value"]
        if k in PROSE:
            value = PROSE[k]
        value2 = apply_terms(value)
        if value2 == e["value"]:
            continue
        line = f"    {e['tag']}{value2}</string>"
        if line == old_line:
            continue
        assert new.count(old_line) == 1, f"{k}: 原行不唯一，改用更精确定位"
        assert sorted(PLACEHOLDER.findall(value2)) == sorted(
            PLACEHOLDER.findall(e["value"])
        ), f"{k}: 占位符被改动"
        new = new.replace(old_line, line, 1)
        if k in PROSE:
            prose_hits += 1
        else:
            term_hits += 1

    assert new.count("<!--") == text.count("<!--"), "注释数量变化"
    print(f"{locale}: 术语归一 {term_hits} 条，口语/对齐 {prose_hits} 条")

    if apply:
        write(target_path, new)
        print(f"已写入 {target_path}")
    else:
        print("dry-run，未写入")


def verify():
    for loc in ("zh-rHK", "zh-rMO"):
        entries = index(read(path_of(loc)))
        bad = []
        for k, e in entries.items():
            if k in WHITELIST:
                continue
            hit = [c for c in YUE_MARKERS if c in e["value"]]
            hit += [p for p in YUE_PHRASES if p in e["value"]]
            if hit:
                bad.append((k, "".join(hit), e["value"][:50]))
        print(f"{loc}: 口语残留 {len(bad)} 条")
        for b in bad:
            print("  ", b)
        left = [k for k, e in entries.items() if any(o in e["value"] for o, _ in TERM_RULES)]
        print(f"{loc}: 术语残留 {len(left)} 条 {left[:8]}")
    hk = read(path_of("zh-rHK"))
    mo = read(path_of("zh-rMO"))
    print("HK 与 MO 完全一致:", hk == mo)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mode", choices=["check", "port", "normalize", "verify"])
    ap.add_argument("--locale", choices=["zh-rHK", "zh-rMO"])
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    if args.mode == "check":
        report()
    elif args.mode == "port":
        assert args.locale, "port 需要 --locale"
        port(args.locale, args.apply)
    elif args.mode == "normalize":
        assert args.locale, "normalize 需要 --locale"
        normalize(args.locale, args.apply)
    else:
        verify()


if __name__ == "__main__":
    sys.exit(main())
