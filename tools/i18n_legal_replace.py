#!/usr/bin/env python3
"""把 tools/i18n_legal/*.tw.txt 的定稿写回繁体 strings.xml，并派生港澳版本。

用法：
    python tools/i18n_legal_replace.py check         # 只比对，不写盘
    python tools/i18n_legal_replace.py verify --pre  # 派生规则完备性 pre-flight
    python tools/i18n_legal_replace.py write --apply # 写三个文件

为什么不用 XML 解析再序列化：`&amp;`、字面量 `\\n`、`%%` 经过 ElementTree 回写都会被改写。
这里只按 `<string>` 的**值区间**做原文切片替换，其余字节一律不动。
"""
import io
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
RES = REPO / "app" / "src" / "main" / "res"
LEGAL = REPO / "tools" / "i18n_legal"

KEYS = ("welcome_privacy_content", "welcome_terms_content")
LOCALES = ("zh-rTW", "zh-rHK", "zh-rMO")

COMMENT = re.compile(r"<!--.*?-->", re.S)
ELEM = re.compile(r"<string[ \t\r\n]([^>]*)>(.*?)</string>", re.S)
NAME = re.compile(r'\bname="([^"]+)"')
HEADING = re.compile(r"^[一二三四五六七八九十]+、")
ASCII_TOKEN = re.compile(r"[A-Za-z0-9][A-Za-z0-9_./:%+\-]*")

# TW → HK 的术语规则，顺序敏感（长词、含「至」的形式要在前面）。
HK_RULES = (
    ("區域網路", "局域網"),
    ("本機詞典", "本地詞典"),
    ("本機網路", "本地網絡"),
    ("字型", "字體"),
    ("網路", "網絡"),
    ("使用者", "用戶"),
    ("上傳", "上載"),
    ("驗證", "認證"),
    ("連線至", "連接"),
    ("連線", "連接"),
    ("規範", "規管"),
    ("回饋", "反映"),
    ("簡訊", "短訊"),
)

PERMISSIONS = (
    "READ_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES", "INTERNET",
    "ACCESS_NETWORK_STATE", "WRITE_SETTINGS", "SYSTEM_ALERT_WINDOW", "WAKE_LOCK",
)

SPECS = {
    "welcome_privacy_content": dict(
        lines=45, bullets=18, headings=10, clauses=2, urls=2,
        dates=["2026年6月29日", "2026年10月1日"], version="政策版本：3",
    ),
    "welcome_terms_content": dict(
        lines=43, bullets=6, headings=13, clauses=5, urls=2,
        dates=["2026年10月1日"], version="協議版本：2",
    ),
}


def read(path):
    text = io.open(path, encoding="utf-8", newline="").read()
    return text.replace("\r\n", "\n").replace("\r", "\n")


def write(path, text):
    assert "\r" not in text, f"{path}: 出现 CR，会写成 CRLF"
    assert not text.startswith("\ufeff"), f"{path}: 有 BOM"
    io.open(path, "w", encoding="utf-8", newline="").write(text)


def locale_path(locale):
    return RES / f"values-{locale}" / "strings.xml"


def elements(text):
    """key -> {value, start, end}，注释内的同名内容忽略。"""
    comments = [m.span() for m in COMMENT.finditer(text)]
    out = {}
    for m in ELEM.finditer(text):
        if any(a <= m.start() < b for a, b in comments):
            continue
        name = NAME.search(m.group(1))
        if not name:
            continue
        out[name.group(1)] = {"value": m.group(2), "start": m.start(2), "end": m.end(2)}
    return out


def block(key):
    """读定稿块文件：一行一个逻辑行，用字面量 \\n 拼回去。"""
    path = LEGAL / f"{key}.tw.txt"
    lines = read(path).splitlines()
    return "\\n".join(lines), lines


def to_hongkong(text):
    for a, b in HK_RULES:
        text = text.replace(a, b)
    return text


def check_value(key, value):
    spec = SPECS[key]
    problems = []
    lines = value.split("\\n")

    def want(label, got, expected):
        if got != expected:
            problems.append(f"{key}: {label} = {got}，应为 {expected}")

    want("行数", len(lines), spec["lines"])
    want("字面量 \\n 数", value.count("\\n"), spec["lines"] - 1)
    want("项目符号数", value.count("•"), spec["bullets"])
    headings = [line for line in lines if HEADING.match(line)]
    want("章节数", len(headings), spec["headings"])
    want("章节序号不重复", len({h.split("、")[0] for h in headings}), spec["headings"])
    want("「條款」数", value.count("條款"), spec["clauses"])
    want("URL 数", value.count("https://github.com/a1242691089-source/wangyan/issues"), spec["urls"])

    for date in spec["dates"]:
        if date not in value:
            problems.append(f"{key}: 缺日期 {date}")
    if spec["version"] not in value:
        problems.append(f"{key}: 缺版本号 {spec['version']}")
    if "政策版本：4" in value or "協議版本：3" in value:
        problems.append(f"{key}: 版本号被递增了，本次不该动")

    for p in PERMISSIONS:
        expected = 1 if key == "welcome_privacy_content" else 0
        if value.count(p) != expected:
            problems.append(f"{key}: 权限名 {p} 出现 {value.count(p)} 次，应为 {expected}")

    if key == "welcome_privacy_content":
        # 「協議連結」指第三方协议链接，是合法用法；指代整份协议的写法不该出现。
        for bad in ("本協議", "用戶協議", "接受協議"):
            if bad in value:
                problems.append(f"{key}: 隐私政策里出现了指代整份协议的「{bad}」")
        if value.count("協議") != value.count("協議連結"):
            problems.append(
                f"{key}: 「協議」出现 {value.count('協議')} 次，"
                f"但只有「協議連結」{value.count('協議連結')} 处是合法的"
            )
        if value.count("仿宋") != 1:
            problems.append(f"{key}: 字体名「仿宋」应出现 1 次")

    for bad in ("質量", "應用（", "應用 "):
        if bad in value:
            problems.append(f"{key}: 出现不该有的写法「{bad}」")
    for ch in ("'", "&", "<"):
        if ch in value:
            problems.append(f"{key}: 值里出现非法字符 {ch!r}")
    if "\n" in value:
        problems.append(f"{key}: 值里出现真换行")
    for line in lines:
        if line != line.strip():
            problems.append(f"{key}: 有行首尾空白：{line[:20]!r}")
            break
    return problems


def cmd_check(args):
    bad = 0
    for key in KEYS:
        value, _ = block(key)
        problems = check_value(key, value)
        if problems:
            bad += len(problems)
            for p in problems:
                print("  ✗", p)
        else:
            print(f"  ✓ {key}: {len(value)} 字符，结构通过")
    if bad:
        print(f"\n{bad} 个问题")
        return 1
    print("\n定稿结构全部通过")
    return 0


def cmd_verify(args):
    """派生规则完备性：用旧 TW 套规则必须逐字节复现旧 HK。"""
    tw = elements(read(locale_path("zh-rTW")))
    hk = elements(read(locale_path("zh-rHK")))
    mo = elements(read(locale_path("zh-rMO")))
    ok = True
    for key in KEYS:
        old_tw, old_hk, old_mo = tw[key]["value"], hk[key]["value"], mo[key]["value"]
        derived = to_hongkong(old_tw)
        same = derived == old_hk
        print(f"  {'✓' if same else '✗'} {key}: R(TW) == HK -> {same}")
        if not same:
            for i, (a, b) in enumerate(zip(derived, old_hk), 1):
                if a != b:
                    print(f"      首个差异 @{i}: 派生 {a!r} vs 现有 {b!r}")
                    print(f"      派生 …{derived[max(0,i-25):i+25]}…")
                    print(f"      现有 …{old_hk[max(0,i-25):i+25]}…")
                    break
            ok = False
        same_mo = old_hk == old_mo
        print(f"  {'✓' if same_mo else '✗'} {key}: HK == MO -> {same_mo}")
        ok = ok and same_mo
    return 0 if ok else 1


def cmd_write(args):
    if "--apply" not in args:
        print("dry-run：加 --apply 才会写盘")
        return cmd_check(args)

    # 1) 改动前快照
    texts = {loc: read(locale_path(loc)) for loc in LOCALES}
    before = {loc: elements(texts[loc]) for loc in LOCALES}
    for loc in LOCALES:
        assert texts[loc].count("\n") == 1810, f"{loc}: 物理行数不是 1810"
        assert len(re.findall(r"<string ", texts[loc])) == 1739, f"{loc}: string 数不是 1739"
        assert texts[loc].count("<!--") == 34, f"{loc}: 注释数不是 34"

    # 2) 派生前 pre-flight
    for key in KEYS:
        assert to_hongkong(before["zh-rTW"][key]["value"]) == before["zh-rHK"][key]["value"], \
            f"{key}: 派生规则无法复现现有 HK，先修规则"
        assert before["zh-rHK"][key]["value"] == before["zh-rMO"][key]["value"], \
            f"{key}: 现有 HK/MO 不一致"

    # 3) 定稿 + 断言
    new = {}
    for key in KEYS:
        value, _ = block(key)
        problems = check_value(key, value)
        assert not problems, "\n".join(problems)
        new[key] = value

    # ASCII 词元多重集必须完全不变（守住标识符、URL、数字）
    for key in KEYS:
        old = sorted(ASCII_TOKEN.findall(before["zh-rTW"][key]["value"]))
        got = sorted(ASCII_TOKEN.findall(new[key]))
        assert old == got, f"{key}: ASCII 词元变化\n  少了 {set(old) - set(got)}\n  多了 {set(got) - set(old)}"

    new_hk = {key: to_hongkong(new[key]) for key in KEYS}

    def replace_values(text, values):
        spans = elements(text)
        for key in sorted(KEYS, key=lambda k: -spans[k]["start"]):
            s, e = spans[key]["start"], spans[key]["end"]
            text = text[:s] + values[key] + text[e:]
        return text

    # 4) 写盘（TW 用新定稿，HK/MO 用派生结果）
    result = {}
    result["zh-rTW"] = replace_values(texts["zh-rTW"], new)
    result["zh-rHK"] = replace_values(texts["zh-rHK"], new_hk)
    result["zh-rMO"] = replace_values(texts["zh-rMO"], new_hk)

    for loc, text in result.items():
        assert text.count("\n") == 1810, f"{loc}: 写后行数变了（CRLF？）"
        assert len(re.findall(r"<string ", text)) == 1739
        assert text.count("<!--") == 34
        ET.fromstring(text)  # 只读校验，不回写
        write(locale_path(loc), text)

    # 5) 写后复核：只有两条 key 的值区间变了
    for loc in LOCALES:
        after = elements(read(locale_path(loc)))
        assert after["welcome_privacy_content"]["value"] == (
            new["welcome_privacy_content"] if loc == "zh-rTW" else new_hk["welcome_privacy_content"]
        ), f"{loc}: privacy 回读不一致"
        assert after["welcome_terms_content"]["value"] == (
            new["welcome_terms_content"] if loc == "zh-rTW" else new_hk["welcome_terms_content"]
        ), f"{loc}: terms 回读不一致"

    assert read(locale_path("zh-rHK")) == read(locale_path("zh-rMO")), "HK 与 MO 不是逐字节相同"

    for key in KEYS:
        print(f"  ✓ {key}: TW {len(before['zh-rTW'][key]['value'])} → {len(new[key])} 字符")
    print("  ✓ 三个文件已写入，HK 与 MO 逐字节一致")
    return 0


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    cmd = sys.argv[1]
    if cmd == "check":
        return cmd_check(sys.argv[2:])
    if cmd == "verify":
        return cmd_verify(sys.argv[2:])
    if cmd == "write":
        return cmd_write(sys.argv[2:])
    print(f"未知子命令 {cmd}")
    return 2


if __name__ == "__main__":
    sys.exit(main())
