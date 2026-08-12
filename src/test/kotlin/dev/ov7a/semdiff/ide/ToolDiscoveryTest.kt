package dev.ov7a.semdiff.ide

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Discovery is what makes "enabled by default" mean something: without it the plugin ships on but
 * with nothing configured, which is indistinguishable from being off.
 */
class ToolDiscoveryTest : BasePlatformTestCase() {

    private lateinit var settings: SemanticDiffSettings

    override fun setUp() {
        super.setUp()
        settings = SemanticDiffSettings.instance
        settings.tools = mutableListOf()
        settings.activeToolName = ""
        settings.toolsDiscovered = false
    }

    override fun tearDown() {
        try {
            settings.tools = mutableListOf()
            settings.activeToolName = ""
            settings.toolsDiscovered = false
        } finally {
            super.tearDown()
        }
    }

    /**
     * Having a configured tool is the only switch. With none, the plugin must be completely inert —
     * that is what makes it safe to have no "enable" flag and no restart-gated plugin toggle.
     */
    fun `test a fresh install with no tool is inert`() {
        val fresh = SemanticDiffSettings()

        assertNull(fresh.activeTool())
        assertFalse(fresh.isUsable())
    }

    fun `test discovery runs once`() {
        ToolDiscovery.discoverOnce()
        val afterFirst = settings.tools.size
        assertTrue(settings.toolsDiscovered)

        ToolDiscovery.discoverOnce()

        assertEquals(afterFirst, settings.tools.size)
    }

    /**
     * A user who deleted every tool must not get them back on the next start; discovery only fills
     * an empty configuration, it does not keep re-asserting itself.
     */
    fun `test discovery does not run again after the user clears the tools`() {
        settings.toolsDiscovered = true

        ToolDiscovery.discoverOnce()

        assertEmpty(settings.tools)
    }

    fun `test a discovered tool is complete enough to run`() {
        ToolDiscovery.discoverOnce()
        val discovered = settings.tools.firstOrNull() ?: return // nothing installed on this machine

        assertNotEmpty(listOf(discovered.handlerId))
        assertFalse(discovered.executablePath.isNullOrBlank())
        assertFalse(discovered.arguments.isNullOrBlank())
        assertFalse(discovered.detectedVersion.isNullOrBlank())
        assertEquals(discovered.name, settings.activeToolName)

        // The whole point: settings that came only from discovery must already be runnable.
        assertNotNull(SemanticDiffService.instance.invocationFor(discovered))
        assertTrue(settings.isUsable())
    }
}
