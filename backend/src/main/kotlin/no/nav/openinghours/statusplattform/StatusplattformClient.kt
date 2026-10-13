package no.nav.openinghours.statusplattform

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.openinghours.model.db.ServiceType
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class StatusplattformServiceDto(
    val id: UUID? = null,
    val name: String? = null,
    val team: String? = null,
    val monitorlink: String? = null
)

data class StatusplattformService(
    val type: ServiceType,
    val dto: StatusplattformServiceDto
)

@Component
class StatusplattformClient(
    private val restTemplate: RestTemplate,
    @Value("\${statusplattform.base-url}") private val baseUrl: String
) {
    fun fetchAll(): List<StatusplattformService> =
        ServiceType.entries.flatMap { type ->
            fetch("/api/v1/services/minimal?type=${type.name}")
                .map { StatusplattformService(type, it) }
        }

    private fun fetch(path: String): List<StatusplattformServiceDto> =
        restTemplate.exchange(
            baseUrl.trimEnd('/') + path,
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<List<StatusplattformServiceDto>>() {}
        ).body.orEmpty()
}
