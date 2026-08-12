package dev.ov7a.semdiff.buildlogic

import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec

/**
 * Named [Spec] classes instead of lambdas.
 *
 * A lambda would capture the enclosing plugin instance, which the configuration cache then has to
 * serialize; these capture a single [Provider], which it handles natively.
 */
internal class NotOverridden(private val overridePresent: Provider<Boolean>) : Spec<Task> {
    override fun isSatisfiedBy(element: Task): Boolean = !overridePresent.get()
}

internal class NotUpdatingGoldens(private val updateGolden: Provider<Boolean>) : Spec<Task> {
    override fun isSatisfiedBy(element: Task): Boolean = !updateGolden.get()
}
