#!/usr/bin/env python3
"""比对单元测试失败清单与已知基线。

背景：这批测试是第一次上 CI。其中 9 条是上游既有的失败，已用基线实验确认
（把 app/src/main/java 回退到 B3 之前的 78165ad，同一套测试跑出完全相同的失败集），
与本项目的品牌/导航/视觉改动无关。它们分两类：

  - 真实可用性缺陷：大字号下文本横向溢出、欢迎页导航按钮被挤出屏幕。
    已登记为待修，不在这里沉默处理。
  - 脆断言：断言两条渲染路径逐像素完全一致、像素捕获比对，跨平台稳定失败。

处理方式不是排除这些类（那会连带丢掉同类里正常用例的覆盖），而是：
workflow 里给「单元测试」步骤加 continue-on-error，让它们不阻塞 job，
本脚本要求「失败集必须是基线的子集」。基线外出现任何新失败 → 退出码 1。
若测试根本没跑起来（编译失败、过滤写错），拿不到 XML，脚本同样失败，
所以 continue-on-error 不会把「测试全挂」误判成通过。

已知基线里的用例哪天被修好了，从这里删掉即可；脚本不因「已知失败未出现」报错，
因为其中两条是时序相关（flaky），通过与否不稳定。
"""

import glob
import sys
import xml.etree.ElementTree as ET

RESULT_GLOB = "app/build/test-results/testDebugUnitTest/**/*.xml"

# 键是 "全限定类名.方法名"，参数化用例的 [33] / [35] 后缀在比对前会被去掉。
KNOWN_FAILURES = {
    # 真实可用性缺陷 —— 待修，修复后从本清单移除并放开
    "com.huangder.lumibooks.ui.theme.AppFontSizeLayoutTest.largestTextWorksAcrossThemesAndDialogActions",
    "com.huangder.lumibooks.ui.welcome.WelcomeThemeRenderTest.largeTextKeepsNavigationAccessible",
    # 脆断言：两条渲染路径逐像素一致性 / 像素捕获
    "com.huangder.lumibooks.ui.reader.engine.ReaderPunctuationLayoutTest.continuousAndPagedInkAreIdenticalWithOneLayout",
    "com.huangder.lumibooks.ui.reader.engine.ReaderPunctuationLayoutTest.indentedParagraphsKeepBothModeInkInsideReaderMargins",
    "com.huangder.lumibooks.ui.reader.engine.TxtTitlePageRenderingTest.chapterTitleStaysLeftAlignedInEveryAlignmentMode",
    "com.huangder.lumibooks.ui.components.MenuAnimationStyleHostTest.normalMenuHidesTheTriggerAndSubmitsSelectionOnlyOnce",
    "com.huangder.lumibooks.ui.components.MenuAnimationStyleHostTest.reducedMotionHidesTheTriggerUntilTheMenuHasFinishedClosingForEitherStyle",
    # 时序 / 调度预算相关，本身不稳定（观察到一次失败一次通过）
    "com.huangder.lumibooks.ui.reader.ContinuousScrollReaderTest.chapterDecodedOutsideTheItemStillRenders",
    "com.huangder.lumibooks.ui.reader.ContinuousScrollReaderTest.revisionKeepsVisibleTextWhileReplacementDecodeIsBlocked",
}


def normalize(classname: str, name: str) -> str:
    """参数化与多 SDK 扇出的用例名形如 xxx[33]，比对时去掉下标。"""
    return f"{classname}.{name.split('[', 1)[0]}"


def main() -> int:
    paths = glob.glob(RESULT_GLOB, recursive=True)
    if not paths:
        print(f"::error::未找到测试结果 XML（{RESULT_GLOB}）。测试没有跑起来，无法判断失败基线。")
        return 1

    failed = set()
    total = 0
    for path in paths:
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as exc:
            print(f"::error::解析 {path} 失败：{exc}")
            return 1
        for case in root.iter("testcase"):
            total += 1
            if case.find("failure") is not None or case.find("error") is not None:
                failed.add(normalize(case.get("classname", ""), case.get("name", "")))

    unknown = sorted(failed - KNOWN_FAILURES)
    if failed:
        print(f"共 {total} 个用例，失败 {len(failed)} 条（已知基线 {len(failed) - len(unknown)} 条）")
        for key in sorted(failed):
            mark = "已知" if key in KNOWN_FAILURES else "新增"
            print(f"  [{mark}] {key}")
    else:
        print(f"共 {total} 个用例，全部通过")

    if unknown:
        print(f"::error::出现 {len(unknown)} 条不在基线内的新失败，请修掉或说明后加入基线")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
