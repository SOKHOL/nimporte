package dev.devforge.core.domain

import dev.devforge.core.model.Content
import dev.devforge.core.model.Progress

/*
 * Ports de l'architecture hexagonale : le domaine dit de quoi il a besoin,
 * les adaptateurs (module :core:data, app Android) le fournissent.
 */

/** Fournit tout le contenu (parcours, leçons, exercices). */
fun interface ContentSource {
    fun load(): Content
}

/** Lit et enregistre la progression de l'utilisateur. */
interface ProgressStore {
    fun load(): Progress
    fun save(progress: Progress)
}

/** Donne la date du jour, en « epoch day ». Injectable pour des tests déterministes. */
fun interface Today {
    fun epochDay(): Long
}
