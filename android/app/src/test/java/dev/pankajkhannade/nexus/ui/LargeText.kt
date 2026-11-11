package dev.pankajkhannade.nexus.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertTrue

/**
 * "Nothing clipped" at large text (UX sprint 2, principle 9): no text is cut short (ellipsis or lines lost,
 * [TextLayoutResult.hasVisualOverflow]) and no text runs past the screen's right edge. Scrolling is allowed;
 * cutting words off is not.
 */
fun ComposeContentTestRule.assertNoClippedText() {
    val rootWidth = onRoot(useUnmergedTree = true).fetchSemanticsNode().size.width
    val nodes = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
    assertTrue("no text found", nodes.isNotEmpty())
    nodes.forEach { node ->
        val text = node.config[SemanticsProperties.Text].joinToString(" ")
        val layouts = mutableListOf<TextLayoutResult>()
        if (SemanticsActions.GetTextLayoutResult in node.config) node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        layouts.forEach {
            assertTrue(
                "clipped text: “$text” (width over: ${it.didOverflowWidth}, height over: ${it.didOverflowHeight}, lines ${it.lineCount}, " +
                    "size ${it.size}, paragraph ${it.multiParagraph.width} x ${it.multiParagraph.height}, max ${it.layoutInput.constraints})",
                // Lines cut off, an ellipsis, or a word wider than its box by more than rounding (Robolectric
                // can report a fraction of a pixel, so 2 px is allowed). didOverflowWidth is not used: Compose sets it
                // whenever the paragraph was laid out wider than the final box, even when every line fits.
                !it.didOverflowHeight && (0 until it.lineCount).none { l -> it.isLineEllipsized(l) } &&
                    (0 until it.lineCount).maxOf { l -> it.getLineRight(l) - it.getLineLeft(l) } <= it.size.width + 2f,
            )
        }
        assertTrue("text runs off the right edge: “$text” (${node.boundsInRoot.right} > $rootWidth)", node.boundsInRoot.right <= rootWidth + 1f)
    }
}
