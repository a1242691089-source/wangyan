#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""拆分 strings 文件（`res/values/*.xml` 中除 strings.xml 之外的 13 个）的台账写入与校验。

与 `i18n_fill.py` 同源：直接 import 它的 XML 解析 / 写入原语（`entries` / `read` /
`write` / `placeholders` / `FORBIDDEN`），保证两条链的转义与占位符口径一致。
差别只有「文件维度」：每个 key 属于确定的一个文件，locale 侧按同名映射到
`values-<locale>/<file>`，目标文件不存在时按需创建（这不改变 aapt2 的合并语义）。

用法：
    python tools/i18n_split_fill.py list --file dictionary_strings.xml --locale ja
    python tools/i18n_split_fill.py fill --file dictionary_strings.xml --batch A          # dry-run
    python tools/i18n_split_fill.py fill --file dictionary_strings.xml --batch A --apply
    python tools/i18n_split_fill.py fillzh --file remote_library_strings.xml --batch A --locales zh-rTW
    python tools/i18n_split_fill.py fillzh --file remote_library_strings.xml --batch hk --derive-from zh-rTW
    python tools/i18n_split_fill.py fillzh --file dictionary_strings.xml --copy-values --locales zh-rCN
    python tools/i18n_split_fill.py verify

台账：`tools/i18n_split/<file-stem>/<batch>.tsv`
    fill   → 3 列 `key<TAB>ja<TAB>ko`
    fillzh → 2 列 `key<TAB>值`
`#` 开头为注释，空行忽略。

写入铁律（与 devdocs/i18n-coverage.md 一致）：按 `</resources>` 前的位置插入，
不 parse 再序列化；读写 `newline=""`；4 空格缩进、单行 `<string>`；写完断言
注释数不变、条目增量正确、值逐条回读一致、无重复 key。
"""

import argparse
import sys
from pathlib import Path

import i18n_fill as core

RES = core.RES
SPLIT = core.REPO / "tools" / "i18n_split"
LOCALES = core.LOCALES

HEADER = '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>\n'

# zh-rTW → zh-rHK / zh-rMO 的术语替换。规则与
# TraditionalChineseTextGuardTest.HK_TERM_RULES 保持同一口径；只收本项目语料里
# 真正出现的词，宁可少改也不要改坏语体。顺序敏感（长词在前）。
HK_TERMS = (
    ("本機詞典", "本地詞典"),
    ("本機詞庫", "本地詞庫"),
    ("字型", "字體"),
    ("網路", "網絡"),
    ("使用者", "用戶"),
    ("上傳", "上載"),
    ("驗證", "認證"),
    ("連線", "連接"),
    ("隱私", "私隱"),
    ("品質", "質素"),
    ("專案", "項目"),
)


def string_files():
    """values/ 下所有含 <string> 的文件名（含 strings.xml 自身，便于 verify 统一口径）。"""
    out = []
    for path in sorted((RES / "values").glob("*.xml")):
        if "<string " in core.read(path):
            out.append(path.name)
    return out


def split_files():
    return [f for f in string_files() if f != "strings.xml"]


def base_file(name):
    return RES / "values" / name


def locale_file(locale, name):
    return RES / f"values-{locale}" / name


def base_entries(name):
    return core.entries(core.read(base_file(name)))


def locale_entries(locale, name):
    path = locale_file(locale, name)
    return core.entries(core.read(path)) if path.exists() else {}


def missing(locale, name, base):
    have = locale_entries(locale, name)
    return [k for k, v in base.items() if v["translatable"] and k not in have]


def ledger_path(name, batch):
    return SPLIT / Path(name).stem / f"{batch}.tsv"


def read_ledger(name, batch, width):
    path = ledger_path(name, batch)
    assert path.exists(), f"台账不存在：{path}"
    rows = []
    for lineno, line in enumerate(core.read(path).split("\n"), 1):
        if not line.strip() or line.startswith("#"):
            continue
        parts = line.split("\t")
        assert len(parts) == width, f"{path}:{lineno} 应为 {width} 列，实际 {len(parts)} 列"
        cols = [p.strip() for p in parts]
        assert all(cols), f"{path}:{lineno} 有空列"
        rows.append(tuple(cols))
    assert len({r[0] for r in rows}) == len(rows), f"{path}: 台账里有重复 key"
    return rows


def check_value(name, key, value, kind):
    """占位符与禁用字符校验，与 i18n_fill 的规则保持同口径。"""
    problems = []
    want = core.placeholders(base_entries(name)[key]["value"])
    got = core.placeholders(value)
    if got != want:
        problems.append(f"{key} [{kind}] 占位符不一致：源 {dict(want)} → 译 {dict(got)}")
    for ch in core.FORBIDDEN:
        if ch in value:
            problems.append(f"{key} [{kind}] 含禁用字符 {ch!r}")
    if value != value.strip():
        problems.append(f"{key} [{kind}] 首尾有空白")
    return problems


def insert(locale, name, rows, apply):
    """rows: [(key, value)]，按 base 顺序插入 `values-<locale>/<name>`。"""
    base = base_entries(name)
    order = {k: i for i, k in enumerate(base)}
    path = locale_file(locale, name)
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        if apply:
            core.write(path, HEADER)
        print(f"{locale}/{name}: 文件不存在，按需创建")
    text = core.read(path) if path.exists() else HEADER
    before = core.entries(text)
    todo = [(k, v) for k, v in rows if k not in before]
    if not todo:
        return 0, len(before)
    todo.sort(key=lambda kv: order[kv[0]])
    block = "".join(f'    <string name="{k}">{v}</string>\n' for k, v in todo)
    idx = text.rfind("</resources>")
    assert idx > 0, f"{path}: 找不到 </resources>"
    head = text[:idx]
    assert head.endswith("\n"), f"{path}: </resources> 前不是换行"
    new_text = head + block + text[idx:]

    assert new_text.count("<!--") == text.count("<!--"), f"{path}: 注释数被改变"
    after = core.entries(new_text)
    assert len(after) == len(before) + len(todo), f"{path}: 条目数增量异常"
    for k, v in todo:
        assert after[k]["value"] == v, f"{path}: {k} 写入值不一致"
    if apply:
        core.write(path, new_text)
    return len(todo), len(after)


def cmd_list(args):
    base = base_entries(args.file)
    en = locale_entries("en", args.file)
    for locale in args.locale:
        keys = missing(locale, args.file, base)
        print(f"# {args.file} {locale}: 缺 {len(keys)} 条", file=sys.stderr)
        for k in keys:
            print(f"{k}\t{base[k]['value']}\t{en.get(k, {}).get('value', '')}")


def cmd_fill(args):
    base = base_entries(args.file)
    rows = read_ledger(args.file, args.batch, 3)
    problems = []
    for key, ja, ko in rows:
        if key not in base:
            problems.append(f"{key}: 不在 values/{args.file}")
            continue
        if not base[key]["translatable"]:
            problems.append(f"{key}: base 标记 translatable=false，不应翻译")
        problems += check_value(args.file, key, ja, "ja")
        problems += check_value(args.file, key, ko, "ko")
    if problems:
        print("\n".join("  " + p for p in problems))
        raise SystemExit(f"台账校验失败，共 {len(problems)} 条")

    for idx, locale in enumerate(("ja", "ko")):
        todo = [(r[0], r[idx + 1]) for r in rows]
        wrote, total = insert(locale, args.file, todo, args.apply)
        print(f"{locale}: 写入 {wrote} 条 -> {total} 条")
    if not args.apply:
        print("（dry-run，未写盘；加 --apply 生效）")


def cmd_fillzh(args):
    base = base_entries(args.file)

    if args.copy_values:
        # zh-rCN 与 values/ 同为简体，直接照抄可译条目，不做任何改写。
        rows = [(k, v["value"]) for k, v in base.items() if v["translatable"]]
        locales = args.locales
    else:
        rows = read_ledger(args.file, args.batch, 2)
        locales = args.locales
        problems = []
        for key, value in rows:
            if key not in base:
                problems.append(f"{key}: 不在 values/{args.file}")
                continue
            if not base[key]["translatable"]:
                problems.append(f"{key}: base 标记 translatable=false，不应翻译")
            problems += check_value(args.file, key, value, "zh")
        if problems:
            print("\n".join("  " + p for p in problems))
            raise SystemExit(f"台账校验失败，共 {len(problems)} 条")

    if args.derive_from:
        source = locale_entries(args.derive_from, args.file)
        derived = []
        for key, _ in rows:
            text = source[key]["value"]
            for tw, hk in HK_TERMS:
                text = text.replace(tw, hk)
            derived.append((key, text))
        rows = derived

    for locale in locales:
        wrote, total = insert(locale, args.file, rows, args.apply)
        print(f"{locale}: 写入 {wrote} 条 -> {total} 条")
    if not args.apply:
        print("（dry-run，未写盘；加 --apply 生效）")


def cmd_verify(args):
    bad = 0
    grand = 0
    for name in split_files():
        base = base_entries(name)
        translatable = sum(1 for v in base.values() if v["translatable"])
        line = [f"{name:34} 基数 {translatable:4}"]
        for locale in LOCALES:
            keys = missing(locale, name, base)
            grand += len(keys)
            exists = locale_file(locale, name).exists()
            mark = "" if not keys else ("整文件缺失" if not exists else "缺")
            line.append(f"{locale}:{len(keys):2}{mark}")
            if keys:
                bad += 1
        print("  ".join(line))
    print(f"\n合计缺口 {grand} 个 locale×key；有问题文件×语言组合 {bad} 个")
    if bad:
        raise SystemExit("拆分 strings 文件仍有缺口")


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("list")
    p.add_argument("--file", required=True)
    p.add_argument("--locale", nargs="+", default=["ja", "ko"])
    p.set_defaults(func=cmd_list)

    p = sub.add_parser("fill")
    p.add_argument("--file", required=True)
    p.add_argument("--batch", required=True)
    p.add_argument("--apply", action="store_true")
    p.set_defaults(func=cmd_fill)

    p = sub.add_parser("fillzh")
    p.add_argument("--file", required=True)
    p.add_argument("--batch")
    p.add_argument("--locales", nargs="+", required=True)
    p.add_argument("--derive-from", dest="derive_from")
    p.add_argument("--copy-values", dest="copy_values", action="store_true")
    p.add_argument("--apply", action="store_true")
    p.set_defaults(func=cmd_fillzh)

    p = sub.add_parser("verify")
    p.set_defaults(func=cmd_verify)

    args = ap.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
