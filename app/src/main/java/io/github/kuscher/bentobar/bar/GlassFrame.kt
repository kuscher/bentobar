package io.github.kuscher.bentobar.bar

/**
 * What a popup's window must do before a frame so the blur follows the glass (bar/MenuWindow.kt). Pure, so the rule
 * is tested (`GlassFrameTest`): each step asks for another frame, so a popup at rest must do none of them.
 */
internal object GlassFrame {
    /** Set the root view's frame to the glass ([bounds]); give the blur new corners ([outline]). */
    data class Work(val bounds: Boolean, val outline: Boolean)

    /**
     * The root view's frame now ([rootRight], [rootBottom]) against the glass's ([width], [bottom]); the blur's corner
     * radius now ([corner]) against the last one given ([lastCorner]). New bounds take new corners with them: the
     * platform reads the outline from the background, whose bounds follow the root's only when it next draws.
     */
    fun work(rootRight: Int, rootBottom: Int, width: Int, bottom: Int, corner: Float, lastCorner: Float): Work {
        val bounds = rootRight != width || rootBottom != bottom
        return Work(bounds = bounds, outline = bounds || corner != lastCorner)
    }
}
