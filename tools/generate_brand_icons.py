#!/usr/bin/env python3
"""生成忘言品牌图标与开屏标记（本机工具，不参与 Gradle 构建）。

字形来源：app/src/main/res/font/gen_ryu_min2_l.ttc 的 idx=2（GenRyuMin2 JP Light，
与 res/font/gen_ryu_min2_light.xml 的 ttcIndex 一致）。已验证该字体含「忘」；
source_serif4_regular.ttf 不含 CJK，渲染出来是豆腐框，不可用。

几何依据（adaptive icon 规范）：
  108dp 画布 / 72dp 可视圆（r=36）/ 66dp 安全区（r=33）
  72dp 圆内最大正方形仅 50.9dp，所以 ink 取 48dp 时四角已到 r≈34，不能再加角标。

输出全部是相对路径，从仓库根目录执行：python tools/generate_brand_icons.py
"""

from pathlib import Path

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTCollection
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
FONT_TTC = RES / "font/gen_ryu_min2_l.ttc"
FONT_INDEX = 2

INK = "#FFFDF8"      # 宣纸白
MOON = "#EFE7D2"     # 月白
INK_GREEN = "#1A6840"
MONO = "#FFFFFFFF"

# style_key -> (底色, 字色, ink dp, 中心 y, 预览图文件名)
STYLES = {
    "lumi2": ("#1A6840", INK, 48.0, 54.0),
    "lumi_chan": ("#14120F", MOON, 44.0, 58.0),
    "classic": ("#F6F1E4", INK_GREEN, 40.0, 54.0),
}


def glyph_path(size: float, cx: float, cy: float) -> str:
    """把「忘」缩放到 size×size 的框内并居中到 (cx, cy)，返回 VectorDrawable pathData。"""
    collection = TTCollection(str(FONT_TTC))
    font = collection.fonts[FONT_INDEX]
    cmap = font.getBestCmap()
    glyph_name = cmap[ord("忘")]
    glyph_set = font.getGlyphSet()

    bounds_pen = BoundsPen(glyph_set)
    glyph_set[glyph_name].draw(bounds_pen)
    x_min, y_min, x_max, y_max = bounds_pen.bounds
    width, height = x_max - x_min, y_max - y_min
    scale = size / max(width, height)

    # 字体坐标 Y 向上，Android 画布 Y 向下：d = -scale
    center_x = (x_min + x_max) / 2
    center_y = (y_min + y_max) / 2
    transform = (
        scale,
        0.0,
        0.0,
        -scale,
        cx - center_x * scale,
        cy + center_y * scale,
    )

    pen = SVGPathPen(glyph_set)
    glyph_set[glyph_name].draw(TransformPen(pen, transform))
    return tidy(pen.getCommands())


def tidy(path_data: str) -> str:
    """压掉多余小数位与空格，避免 pathData 过长。"""
    out = []
    number = ""
    for ch in path_data:
        if ch.isdigit() or ch in ".-":
            number += ch
            continue
        if number:
            out.append(f"{float(number):.2f}".rstrip("0").rstrip("."))
            number = ""
        out.append(" " if ch == " " else ch)
    if number:
        out.append(f"{float(number):.2f}".rstrip("0").rstrip("."))
    return "".join(out).replace("  ", " ").strip()


def vector(width_dp: int, height_dp: int, body: str) -> str:
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{width_dp}dp"\n'
        f'    android:height="{height_dp}dp"\n'
        f'    android:viewportWidth="{width_dp}"\n'
        f'    android:viewportHeight="{height_dp}">\n'
        f"{body}</vector>\n"
    )


def glyph_body(path_data: str, color: str, stroke: float) -> str:
    # stroke 用同色：明朝体在 24–48dp 下笔画会细到消失，加一点点描边等效增粗。
    return (
        f'    <path\n'
        f'        android:pathData="{path_data}"\n'
        f'        android:fillColor="{color}"\n'
        f'        android:strokeColor="{color}"\n'
        f'        android:strokeWidth="{stroke}"\n'
        f'        android:strokeLineJoin="round" />\n'
    )


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8", newline="\n")
    print(f"  {path.relative_to(ROOT)}  {len(content.encode())} B")


def adaptive(bg: str, fg: str, monochrome: str | None = None) -> str:
    mono = (
        f'    <monochrome android:drawable="{monochrome}" />\n' if monochrome else ""
    )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        f'    <background android:drawable="{bg}" />\n'
        f'    <foreground android:drawable="{fg}" />\n'
        f"{mono}"
        "</adaptive-icon>\n"
    )


def render_bitmap(path: Path, size_px: int, bg: str, color: str, ink_ratio: float,
                  center_y_ratio: float = 0.5, decoration: str | None = None) -> None:
    """按「72dp 可视区」的比例画方形位图，供设置页预览/媒体占位使用。

    自适应图标是 108dp 画布被遮罩裁出中央 72dp，所以位图画布 = 可视区：
      - ink_ratio = ink/72（默认 48/72 = 0.667）；
      - 108 画布里的坐标 y 需先减去可视区上沿 18dp 再除以 72。
    换算错参照系会让装饰压到字上（第一版就犯过：圆环画成 2/3 半径）。
    """
    scale = 4
    canvas = size_px * scale
    image = Image.new("RGB", (canvas, canvas), bg)
    draw = ImageDraw.Draw(image)
    ink_px = int(canvas * ink_ratio)
    font = ImageFont.truetype(str(FONT_TTC), size=ink_px, index=FONT_INDEX)
    center = (canvas / 2, canvas * center_y_ratio)
    # anchor="mm" 以字形包围盒居中，和矢量的 bbox 居中一致
    draw.text(center, "忘", font=font, fill=color, anchor="mm",
              stroke_width=max(1, int(ink_px * 0.012)), stroke_fill=color)
    dp = lambda v: canvas * (v / 72.0)  # 72dp 可视区 -> 像素
    if decoration:
        if decoration == "line":
            # 108 画布 y=30 -> 可视区 y=12；半宽 15dp、线宽 2dp
            y = dp(12)
            half = dp(15)
            width = max(2, int(dp(2)))
            draw.line([(canvas / 2 - half, y), (canvas / 2 + half, y)], fill=color, width=width)
        elif decoration == "ring":
            radius = dp(31)
            width = max(2, int(dp(3)))
            draw.ellipse([canvas / 2 - radius, canvas / 2 - radius,
                          canvas / 2 + radius, canvas / 2 + radius],
                         outline=color, width=width)
    image = image.resize((size_px, size_px), Image.LANCZOS)
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "WEBP", quality=92, method=6)
    print(f"  {path.relative_to(ROOT)}  {path.stat().st_size} B  {size_px}x{size_px}")


def main() -> None:
    print("生成矢量（108dp viewport）：")
    paths = {}
    for key, (_, color, ink, center_y) in STYLES.items():
        data = glyph_path(ink, 54.0, center_y)
        paths[key] = (data, color, ink, center_y)
        body = glyph_body(data, color, 1.1)
        if key == "lumi_chan":
            body += (
                '    <path\n'
                '        android:pathData="M39,30 L69,30"\n'
                f'        android:strokeColor="{color}"\n'
                '        android:strokeWidth="2"\n'
                '        android:strokeLineCap="round" />\n'
            )
        elif key == "classic":
            body += (
                '    <path\n'
                '        android:pathData="M23,54 A31,31 0 1 1 85,54 A31,31 0 1 1 23,54"\n'
                '        android:fillColor="#00000000"\n'
                f'        android:strokeColor="{color}"\n'
                '        android:strokeWidth="3" />\n'
            )
        write(RES / f"drawable/ic_launcher_foreground_{key}.xml", vector(108, 108, body))

    # Android 13 主题图标：只取 alpha，几何必须与默认套一致
    write(
        RES / "drawable/ic_launcher_monochrome.xml",
        vector(108, 108, glyph_body(paths["lumi2"][0], MONO, 1.1)),
    )

    # 开屏标记：与系统开屏 288dp 画布同构（ink 48/108 → 128dp）
    write(RES / "drawable/splash_mark.xml",
          vector(108, 108, glyph_body(paths["lumi2"][0], INK_GREEN, 1.1)))
    write(RES / "drawable-night/splash_mark.xml",
          vector(108, 108, glyph_body(paths["lumi2"][0], MOON, 1.1)))

    # 通知小图标：24dp viewport，ink 20dp
    stat = glyph_path(20.0, 12.0, 12.0)
    write(RES / "drawable/ic_stat_wangyan.xml", vector(24, 24, glyph_body(stat, MONO, 0.9)))

    print("生成自适应图标 XML：")
    write(RES / "mipmap-anydpi-v26/ic_launcher_lumi2.xml",
          adaptive("@color/brand_ink", "@drawable/ic_launcher_foreground_lumi2",
                   "@drawable/ic_launcher_monochrome"))
    write(RES / "mipmap-anydpi-v26/ic_launcher_lumi_chan.xml",
          adaptive("@color/brand_night", "@drawable/ic_launcher_foreground_lumi_chan"))
    write(RES / "mipmap-anydpi-v26/ic_launcher.xml",
          adaptive("@color/brand_paper", "@drawable/ic_launcher_foreground_classic"))
    # 系统开屏用：透明底，只露出标记
    write(RES / "mipmap-anydpi-v26/ic_splash_mark.xml",
          adaptive("@android:color/transparent", "@drawable/splash_mark"))

    print("生成位图：")
    # 预览按 72dp 可视区构图：ink_ratio = ink/72
    for key, (bg, color, ink, center_y) in STYLES.items():
        name = {"lumi2": "lumi2", "lumi_chan": "night", "classic": "classic"}[key]
        decoration = {"lumi_chan": "line", "classic": "ring"}.get(key)
        # 夜读在 108 画布里中心是 (54,58)，映射到 72dp 可视区 = (58-18)/72
        center_ratio = (center_y - 18.0) / 72.0
        render_bitmap(RES / f"drawable-xxhdpi/ic_preview_style_{name}.webp",
                      324, bg, color, ink / 72.0, center_ratio, decoration)
    render_bitmap(RES / "drawable-nodpi/ic_media_placeholder.webp",
                  512, "#1A6840", INK, 0.55, 0.5)


if __name__ == "__main__":
    main()
