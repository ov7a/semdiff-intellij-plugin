package dev.ov7a.semdiff.tools

import dev.ov7a.semdiff.tools.difftastic.DifftasticHandler
import dev.ov7a.semdiff.tools.diffsitter.DiffsitterHandler
import dev.ov7a.semdiff.tools.sem.SemHandler

/** Every handler the plugin knows about. Adding a tool means adding it to [all]. */
object HandlerRegistry {

    val all: List<SemanticDiffToolHandler> = listOf(
        DifftasticHandler(),
        DiffsitterHandler(),
        SemHandler(),
    )

    fun byId(id: String): SemanticDiffToolHandler? = all.firstOrNull { it.id == id }
}
