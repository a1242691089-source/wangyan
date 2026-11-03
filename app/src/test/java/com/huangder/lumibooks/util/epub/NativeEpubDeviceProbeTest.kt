package com.huangder.lumibooks.util.epub

import android.app.Application
import android.graphics.*
import com.huangder.lumibooks.ui.reader.engine.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeEpubDeviceProbeTest {
    @Test fun actualDeviceBookChaptersCompileAndDraw() {
        // 样本 epub 放在 app/build/device-epubs/ 下，被 .gitignore 的 **/build/ 排除，不入库。
        // 这条是「有样本就跑」的本地台架探针，不是可重复的回归测试，缺样本时跳过而非失败。
        val probe = File("build/device-epubs/mao.epub")
        assumeTrue("缺少样本 $probe；放入该书后本用例才执行", probe.isFile)
        val session = EpubRenderSession.open(probe.absolutePath)
        try {
            for (chapter in 184..186) {
                val source = session.nativeChapterSource(chapter)!!
                val result = NativeEpubCompiler.compile(source)
                println("DEVICE_PROBE chapter=$chapter href=${source.href} engine=${result.decision.engine} reason=${result.decision.reason}")
                assertEquals(result.decision.reason, ChapterRenderEngine.NATIVE, result.decision.engine)
                val doc = result.document!!
                val cfg = NativeEpubRenderConfig(1200, 2670, 54f, Typeface.SERIF, null, Color.WHITE, true, 60f, 72f, 60f, 72f)
                val layout = NativeEpubChapterRenderer.layout(doc, cfg) { session.nativeResource(it)?.bytes }
                try {
                    assertTrue(layout.pages.isNotEmpty())
                    val bitmap = Bitmap.createBitmap(cfg.width, cfg.height, Bitmap.Config.ARGB_8888)
                    try { layout.draw(Canvas(bitmap), 0) } finally { bitmap.recycle() }
                    println("DEVICE_PROBE ready chapter=$chapter pages=${layout.pages.size}")
                } finally { layout.close() }
            }
        } finally { session.close() }
    }
}
