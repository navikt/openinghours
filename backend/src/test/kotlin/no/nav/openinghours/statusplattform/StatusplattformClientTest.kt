package no.nav.openinghours.statusplattform

import no.nav.openinghours.model.db.ServiceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.util.UUID

class StatusplattformClientTest {

    private val restTemplate = RestTemplate()
    private val server = MockRestServiceServer.bindTo(restTemplate).build()
    private val client = StatusplattformClient(restTemplate, "http://statusplattform.test/")

    @Test
    fun `fetches services and components from Statusplattform v2 endpoint by type`() {
        val serviceId = UUID.randomUUID()
        val componentId = UUID.randomUUID()
        server.expect(requestTo("http://statusplattform.test/api/v1/services/minimal?type=TJENESTE"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                """[{"id":"$serviceId","name":"Gosys","type":"TJENESTE"}]""",
                MediaType.APPLICATION_JSON
            ))
        server.expect(requestTo("http://statusplattform.test/api/v1/services/minimal?type=KOMPONENT"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                """[{"id":"$componentId","name":"Database","type":"KOMPONENT"}]""",
                MediaType.APPLICATION_JSON
            ))

        val result = client.fetchAll()

        assertThat(result).containsExactly(
            StatusplattformService(ServiceType.TJENESTE, StatusplattformServiceDto(serviceId, "Gosys")),
            StatusplattformService(ServiceType.KOMPONENT, StatusplattformServiceDto(componentId, "Database"))
        )
        server.verify()
    }

    @Test
    fun `throws when Statusplattform responds with error`() {
        server.expect(requestTo("http://statusplattform.test/api/v1/services/minimal?type=TJENESTE"))
            .andRespond(withServerError())

        assertThrows<RestClientException> { client.fetchAll() }
    }
}
