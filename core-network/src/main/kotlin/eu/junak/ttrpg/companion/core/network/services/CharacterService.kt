package eu.junak.ttrpg.companion.core.network.services

import eu.junak.ttrpg.companion.core.network.site.Site
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The character sheet module's service: `dnd5e.character` 4.x from whichever
 * add-on provides it on a site. This is the one place that names it.
 */
@Singleton
class CharacterService @Inject constructor(private val services: AddonServices) {

    /** Whether [site] offers character sheets, asking the host each time. */
    suspend fun availability(site: Site): ServiceAvailability = services.availability(site, CONTRACT)

    companion object {
        val CONTRACT = ServiceContract("dnd5e.character", "^4.0.0")
    }
}
