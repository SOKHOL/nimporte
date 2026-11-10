package dev.devforge

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test de fumée sur un vrai Android : l'app démarre, charge le contenu depuis les
 * assets, et chaque écran principal s'affiche sans planter.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, substring: Boolean = false) {
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openTrack(title: String) {
        waitForText("Défi du jour")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(title))
        compose.onNodeWithText(title).performClick()
    }

    @Test
    fun leContenuSeChargeEtLAccueilSAffiche() {
        waitForText("Défi du jour")
        waitForText("Révisions du jour")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("La JVM de l'intérieur"))
    }

    @Test
    fun uneLeconSOuvre() {
        openTrack("Programmation orientée objet")
        waitForText("Objets et classes")
        compose.onAllNodesWithText("L'encapsulation ·", substring = true).onFirst().performScrollTo().performClick()
        waitForText("un objet protège son état", substring = true)
    }

    @Test
    fun uneSessionDEntrainementDemarre() {
        openTrack("Threads et concurrence")
        waitForText("Faire plusieurs choses à la fois")
        compose.onAllNodesWithText("S'entraîner").onFirst().performScrollTo().performClick()
        waitForText("Exercice 1/", substring = true)
        compose.onNodeWithText("Valider").assertExists()
    }

    @Test
    fun leBossAfficheSonChronometre() {
        openTrack("Programmation orientée objet")
        waitForText("Objets et classes")
        compose.onAllNodesWithText("⚔️ Boss").onFirst().performScrollTo().performClick()
        waitForText("⏱", substring = true)
        waitForText("Exercice 1/6", substring = true)
    }

    @Test
    fun leDefiDuJourEtLeProfil() {
        waitForText("Défi du jour")
        compose.onNodeWithText("Relever le défi").performClick()
        waitForText("Exercice 1/1", substring = true)
        compose.onNodeWithContentDescription("Quitter").performClick()
        waitForText("Défi du jour")
        compose.onNodeWithContentDescription("Profil").performClick()
        waitForText("Radar de compétences")
    }
}
