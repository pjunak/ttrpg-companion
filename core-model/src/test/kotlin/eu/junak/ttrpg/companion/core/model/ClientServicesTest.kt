package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ClientServicesTest {

    private val generation = "a".repeat(64)
    private val provider = ClientServiceProvider("sheets", "4.0.0", generation)

    private fun connection(providers: String, contractVersion: String = "client-service-connection.v1") =
        ClientServiceConnection.parse(
            Json.parseToJsonElement("""{"contractVersion":"$contractVersion","contract":"dnd5e.character","range":"^4.0.0","providers":$providers}"""),
            "dnd5e.character",
            "^4.0.0",
        )

    private val call = ClientServiceCall(
        "dnd5e.character", provider, "load", buildJsonObject { put("key", JsonPrimitive("kael")) }, 5000,
    )

    @Test
    fun connectAsksForAContractAndRange() {
        assertEquals(
            """{"contractVersion":"client-service-connect.v1","contract":"dnd5e.character","range":"^4.0.0"}""",
            ClientServiceConnection.request("dnd5e.character", "^4.0.0").toString(),
        )
    }

    @Test
    fun connectionListsProvidersInTheHostsOrder() {
        assertEquals(
            listOf(provider, ClientServiceProvider("tables", "4.2.1", "b".repeat(64))),
            connection(
                """[{"addonId":"sheets","contractVersion":"4.0.0","generation":"$generation"},""" +
                    """{"addonId":"tables","contractVersion":"4.2.1","generation":"${"b".repeat(64)}","extra":1}]""",
            ).providers,
        )
        assertEquals(emptyList<ClientServiceProvider>(), connection("[]").providers)
    }

    @Test
    fun connectionRejectsAnotherFormat() {
        val provider = """[{"addonId":"sheets","contractVersion":"4.0.0","generation":"$generation"}]"""
        assertThrows(HostFormatException::class.java) { connection(provider, contractVersion = "client-service-connection.v2") }
        assertThrows(HostFormatException::class.java) { connection("""[{"addonId":"sheets","contractVersion":"4.0.0"}]""") }
        assertThrows(HostFormatException::class.java) { connection("""[{"addonId":"","contractVersion":"4.0.0","generation":"$generation"}]""") }
        assertThrows(HostFormatException::class.java) { connection("null") }
        assertThrows(HostFormatException::class.java) {
            ClientServiceConnection.parse(
                Json.parseToJsonElement("""{"contractVersion":"client-service-connection.v1","contract":"dnd5e.rules-engine","range":"^4.0.0","providers":[]}"""),
                "dnd5e.character",
                "^4.0.0",
            )
        }
    }

    @Test
    fun callNamesTheExactProvider() {
        assertEquals(
            """{"contractVersion":"client-service-call.v1","contract":"dnd5e.character","providerAddonId":"sheets",""" +
                """"providerVersion":"4.0.0","providerGeneration":"$generation","method":"load","params":{"key":"kael"},"deadlineMs":5000}""",
            call.body().toString(),
        )
        assertThrows(IllegalArgumentException::class.java) { call.copy(deadlineMs = 0) }
        assertThrows(IllegalArgumentException::class.java) { call.copy(deadlineMs = 30_001) }
        assertThrows(IllegalArgumentException::class.java) { call.copy(method = "") }
    }

    @Test
    fun resultIsTheProvidersAnswer() {
        fun answer(contractVersion: String = "client-service-result.v1", addonId: String = "sheets", result: String = ""","result":{"name":"Kael"}""") =
            Json.parseToJsonElement("""{"contractVersion":"$contractVersion","providerAddonId":"$addonId","providerGeneration":"$generation"$result}""")

        assertEquals(JsonObject(mapOf("name" to JsonPrimitive("Kael"))), call.result(answer()))
        assertThrows(HostFormatException::class.java) { call.result(answer(contractVersion = "client-service-result.v2")) }
        assertThrows(HostFormatException::class.java) { call.result(answer(addonId = "tables")) }
        assertThrows(HostFormatException::class.java) { call.result(answer(result = "")) }
    }
}
