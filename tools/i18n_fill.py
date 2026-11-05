#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ja/ko strings 台账写入与校验工具。

台账格式：tools/i18n_ja_ko/<batch>.tsv，每行 `key<TAB>ja<TAB>ko`，无表头，`#` 开头为注释。

用法：
    python tools/i18n_fill.py list [--locale ja]     # 打印缺失 key 与 zh/en 参考
    python tools/i18n_fill.py fill --batch A [--apply]
    python tools/i18n_fill.py verify                 # 各 locale 缺失统计

写入规则（与 devdocs/i18n-coverage.md 一致）：
  - 插入点固定在最后一个 </resources> 之前，4 空格缩进，单行 <string>
  - 按 values/strings.xml 的 key 顺序写入
  - 读写统一 LF、无 BOM；工作区因 core.autocrlf 可能是 CRLF，读入时归一
"""

import argparse
import io
import re
import sys
from collections import Counter
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
RES = REPO / "app" / "src" / "main" / "res"
LOCALES = ("en", "ja", "ko", "zh-rCN", "zh-rHK", "zh-rMO", "zh-rTW")

COMMENT = re.compile(r"<!--.*?-->", re.S)
ELEM = re.compile(r"<string[ \t\r\n]([^>]*)>(.*?)</string>", re.S)
NAME = re.compile(r'\bname="([^"]+)"')
NOT_TRANSLATABLE = re.compile(r'translatable\s*=\s*"false"')
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[A-Za-z%]")
FORBIDDEN = ("&", "<", "'", "\r")


def read(path):
    text = io.open(path, encoding="utf-8", newline="").read()
    if text.startswith("\ufeff"):
        text = text[1:]
    return text.replace("\r\n", "\n")


def write(path, text):
    with io.open(path, "w", encoding="utf-8", newline="") as fh:
        fh.write(text)


def entries(text):
    """name -> {value, translatable, line}，注释里的条目跳过。"""
    spans = [m.span() for m in COMMENT.finditer(text)]
    out = {}
    for m in ELEM.finditer(text):
        if any(a <= m.start() < b for a, b in spans):
            continue
        name = NAME.search(m.group(1))
        if not name:
            continue
        out[name.group(1)] = {
            "value": m.group(2),
            "translatable": not NOT_TRANSLATABLE.search(m.group(1)),
            "line": m.group(0),
        }
    return out


def base_entries():
    return entries(read(RES / "values" / "strings.xml"))


def locale_path(locale):
    return RES / f"values-{locale}" / "strings.xml"


def missing(locale, base):
    have = entries(read(locale_path(locale)))
    return [k for k, v in base.items() if v["translatable"] and k not in have]


def placeholders(text):
    return Counter(PLACEHOLDER.findall(text))


def cmd_list(args):
    base = base_entries()
    en = entries(read(locale_path("en")))
    for locale in args.locale:
        keys = missing(locale, base)
        print(f"# {locale}: 缺 {len(keys)} 条", file=sys.stderr)
        for k in keys:
            print(f"{k}\t{base[k]['value']}\t{en.get(k, {}).get('value', '')}")


def read_ledger(batch):
    path = REPO / "tools" / "i18n_ja_ko" / f"{batch}.tsv"
    assert path.exists(), f"台账不存在：{path}"
    rows = []
    for lineno, line in enumerate(read(path).split("\n"), 1):
        if not line.strip() or line.startswith("#"):
            continue
        parts = line.split("\t")
        assert len(parts) == 3, f"{path}:{lineno} 应为 3 列（key/ja/ko），实际 {len(parts)} 列"
        key, ja, ko = (p.strip() for p in parts)
        assert key and ja and ko, f"{path}:{lineno} 有空列"
        rows.append((key, ja, ko))
    return rows


def cmd_fill(args):
    base = base_entries()
    order = {k: i for i, k in enumerate(base)}
    rows = read_ledger(args.batch)
    keys = [r[0] for r in rows]
    assert len(set(keys)) == len(keys), "台账里有重复 key"

    problems = []
    for key, ja, ko in rows:
        if key not in base:
            problems.append(f"{key}: 不在 values/strings.xml")
            continue
        if not base[key]["translatable"]:
            problems.append(f"{key}: base 标记 translatable=false，不应翻译")
        want = placeholders(base[key]["value"])
        for lang, value in (("ja", ja), ("ko", ko)):
            got = placeholders(value)
            if got != want:
                problems.append(f"{key} [{lang}] 占位符不一致：源 {dict(want)} → 译 {dict(got)}")
            for ch in FORBIDDEN:
                if ch in value:
                    problems.append(f"{key} [{lang}] 含禁用字符 {ch!r}")
    if problems:
        print("\n".join("  " + p for p in problems))
        raise SystemExit(f"台账校验失败，共 {len(problems)} 条")

    ordered = sorted(rows, key=lambda r: order[r[0]])
    for loc_index, locale in enumerate(("ja", "ko")):
        path = locale_path(locale)
        text = read(path)
        before = entries(text)
        todo = [(r[0], r[loc_index + 1]) for r in ordered if r[0] not in before]
        same = [k for k, r in ((r[0], r) for r in ordered)
                if k in before and before[k]["value"] == r[loc_index + 1]]
        if not todo:
            print(f"{locale}: 本批已全部存在（{len(same)} 条值一致），跳过")
            continue
        block = "".join(f'    <string name="{k}">{v}</string>\n' for k, v in todo)
        idx = text.rfind("</resources>")
        assert idx > 0, f"{path}: 找不到 </resources>"
        head = text[:idx]
        assert head.endswith("\n"), f"{path}: </resources> 前不是换行"
        new_text = head + block + text[idx:]

        assert new_text.count("<!--") == text.count("<!--"), f"{path}: 注释数被改变"
        after = entries(new_text)
        assert len(after) == len(before) + len(todo), f"{path}: 条目数增量异常"
        for k, v in todo:
            assert after[k]["value"] == v, f"{path}: {k} 写入值不一致"
        assert len(after) == len(set(after)), f"{path}: 写入后出现重复 key"

        print(f"{locale}: 写入 {len(todo)} 条 -> {len(after)} 条")
        if args.apply:
            write(path, new_text)
    if not args.apply:
        print("（dry-run，未写盘；加 --apply 生效）")


def cmd_verify(args):
    base = base_entries()
    total = sum(1 for v in base.values() if v["translatable"])
    bad = 0
    for locale in LOCALES:
        keys = missing(locale, base)
        count = len(entries(read(locale_path(locale))))
        flag = "OK " if not keys else "MISS"
        print(f"  {flag} {locale:8} 有 {count:5} / 基数 {total}  缺 {len(keys):3}")
        if keys:
            bad += 1
            print("        " + ", ".join(keys[:8]) + (" ..." if len(keys) > 8 else ""))
    if bad:
        raise SystemExit(f"{bad} 个 locale 仍有缺口")


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("list")
    p.add_argument("--locale", nargs="+", default=["ja", "ko"])
    p.set_defaults(func=cmd_list)
    p = sub.add_parser("fill")
    p.add_argument("--batch", required=True)
    p.add_argument("--apply", action="store_true")
    p.set_defaults(func=cmd_fill)
    p = sub.add_parser("verify")
    p.set_defaults(func=cmd_verify)
    args = ap.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
