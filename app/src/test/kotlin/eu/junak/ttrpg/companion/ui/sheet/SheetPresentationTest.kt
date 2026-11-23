package eu.junak.ttrpg.companion.ui.sheet

import eu.junak.ttrpg.companion.core.model.ClientServiceProvider
import eu.junak.ttrpg.companion.core.model.Role
import eu.junak.ttrpg.companion.core.model.character.ActiveCondition
import eu.junak.ttrpg.companion.core.model.character.CharacterAnswer
import eu.junak.ttrpg.companion.core.model.character.CharacterStatus
import eu.junak.ttrpg.companion.core.model.character.ClassLevel
import eu.junak.ttrpg.companion.core.model.character.ConditionOption
import eu.junak.ttrpg.companion.core.model.character.SheetHeader
import eu.junak.ttrpg.companion.core.network.HostFailure
import eu.junak.ttrpg.companion.core.network.character.CharacterSessionState
import eu.junak.ttrpg.companion.core.network.character.DeviceCopy
import eu.junak.ttrpg.companion.core.network.character.SaveState
import eu.junak.ttrpg.companion.core.network.services.CharacterService
import eu.junak.ttrpg.companion.core.network.services.ServiceAvailability
import eu.junak.ttrpg.companion.core.network.services.ServiceBinding
import eu.junak.ttrpg.companion.core.network.services.ServiceFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetPresentationTest {

    private fun answer(status: CharacterStatus = CharacterStatus.READY, rulesChanged: Boolean = false) =
        CharacterAnswer("bren-ashcroft", status, 10, rulesChanged, "", Role.PLAYER, state = null, evaluation = null)

    private fun header(hp: Int = 31, max: Int = 44, temporary: Int = 0, species: String? = "Human", classes: List<ClassLevel> = listOf(paladin(5))) =
        SheetHeader(species, classes, hp, max, temporary, 19, 30, 3, inspiration = false, concentration = null, conditions = emptyList())

    private fun paladin(level: Int) = ClassLevel("paladin", "Paladin", level, "oath-of-devotion", "Oath of Devotion")

    private val offline = ServiceFailure.Failed(HostFailure(HostFailure.Reason.OFFLINE, ""))

    @Test
    fun theSaveBadgeFollowsTheSession() {
        assertEquals(SaveBadge.SAVED, saveBadge(CharacterSessionState()))
        assertEquals(SaveBadge.SAVING, saveBadge(CharacterSessionState(save = SaveState.Saving)))
        assertEquals("changes waiting count as saving", SaveBadge.SAVING, saveBadge(CharacterSessionState(pending = 2)))
        assertEquals(SaveBadge.NOT_SAVED, saveBadge(CharacterSessionState(save = SaveState.NotSaved(offline, retrying = true), pending = 2)))
    }

    @Test
    fun theMostUrgentBannerWins() {
        val held = SaveState.NotSaved(offline, retrying = false)
        val copy = DeviceCopy(5_000L, offline)
        val changed = answer(rulesChanged = true)
        assertEquals(SheetBanner.NotSaved(offline, false), sheetBanner(CharacterSessionState(save = held, copy = copy, answer = changed)))
        assertEquals(SheetBanner.DeviceCopy(5_000L, offline), sheetBanner(CharacterSessionState(copy = copy, answer = changed)))
        assertEquals(SheetBanner.RulesChanged, sheetBanner(CharacterSessionState(answer = changed)))
        assertEquals(SheetBanner.RulesUnavailable, sheetBanner(CharacterSessionState(answer = answer(CharacterStatus.UNAVAILABLE))))
        assertNull(sheetBanner(CharacterSessionState(answer = answer())))
    }

    @Test
    fun theHeaderDimsWhileTheRulesCannotBeUsed() {
        assertTrue(headerDimmed(CharacterSessionState(answer = answer(rulesChanged = true))))
        assertTrue(headerDimmed(CharacterSessionState(answer = answer(CharacterStatus.UNAVAILABLE))))
        assertFalse(headerDimmed(CharacterSessionState(answer = answer(), copy = DeviceCopy(1L))))
    }

    @Test
    fun theClassLineNamesSpeciesAndClasses() {
        assertEquals("Human · Paladin 5", classLine(header()))
        assertEquals(
            "Human · Paladin 4 / Warlock 1",
            classLine(header(classes = listOf(paladin(4), ClassLevel("warlock", "Warlock", 1, null, null)))),
        )
        assertEquals("Paladin 5", classLine(header(species = null)))
        assertEquals("Human", classLine(header(classes = emptyList())))
    }

    @Test
    fun modifiersCarryTheirSign() {
        assertEquals("+3", signed(3))
        assertEquals("+0", signed(0))
        assertEquals("−1", signed(-1))
    }

    @Test
    fun theHitPointBarSharesCurrentAndTemporary() {
        assertEquals(31f / 44, hitPointShares(header()).first, 0.0001f)
        assertEquals(5f / 44, hitPointShares(header(temporary = 5)).second, 0.0001f)
        assertEquals("temporary fills at most the rest", 13f / 44, hitPointShares(header(temporary = 40)).second, 0.0001f)
        assertEquals(0f to 0f, hitPointShares(header(max = 0)))
    }

    @Test
    fun theKeypadBuildsANumber() {
        var number = ""
        for (key in listOf(KeypadKey.Digit(0), KeypadKey.Digit(7), KeypadKey.Digit(1))) number = pressKey(number, key)
        assertEquals("a leading zero is replaced", "71", number)
        number = pressKey(number, KeypadKey.Delete)
        assertEquals("7", number)
        for (digit in 1..6) number = pressKey(number, KeypadKey.Digit(digit))
        assertEquals("at most four digits", "7123", number)
        assertEquals("", pressKey(number, KeypadKey.Clear))
        assertEquals("", pressKey("", KeypadKey.Delete))

        assertNull("nothing typed: the actions are off", keypadAmount(""))
        assertEquals(0, keypadAmount("0"))
        assertEquals(7123, keypadAmount("7123"))
    }

    @Test
    fun thePickerOffersTheRulesConditions() {
        val options = listOf(
            ConditionOption("blinded", "Blinded", 1, canAdd = true),
            ConditionOption("exhaustion", "Exhaustion", 6, canAdd = true),
            ConditionOption("charmed", "Charmed", 1, canAdd = false),
        )
        val choices = conditionChoices(options, listOf(ActiveCondition("exhaustion", "Exhaustion", 2, active = true)))
        assertEquals(
            listOf(
                ConditionChoice("blinded", "Blinded", 1, enabled = true, level = null),
                ConditionChoice("exhaustion", "Exhaustion", 6, enabled = true, level = 2),
                ConditionChoice("charmed", "Charmed", 1, enabled = false, level = null),
            ),
            choices,
        )
        assertTrue(choices[1].levelled)
        assertFalse(choices[0].levelled)
    }

    @Test
    fun aRecordOffersTheSheetWhenTheHostOrThePhoneHasIt() {
        val available = ServiceAvailability.Available(ServiceBinding(CharacterService.CONTRACT, ClientServiceProvider("dnd-sheets", "4.0.0", "g")))
        val unknown = ServiceAvailability.Unknown(offline)
        assertTrue(offersSheet(signedIn = true, available, hasCopy = false))
        assertTrue("the host cannot be asked, the phone has a copy", offersSheet(signedIn = true, unknown, hasCopy = true))
        assertFalse(offersSheet(signedIn = true, unknown, hasCopy = false))
        assertFalse("the site does not offer sheets", offersSheet(signedIn = true, ServiceAvailability.NotOffered, hasCopy = true))
        assertFalse(offersSheet(signedIn = true, ServiceAvailability.HostTooOld, hasCopy = true))
        assertFalse("not yet answered", offersSheet(signedIn = true, null, hasCopy = true))
        assertFalse("reading signed out", offersSheet(signedIn = false, available, hasCopy = true))
    }
}
