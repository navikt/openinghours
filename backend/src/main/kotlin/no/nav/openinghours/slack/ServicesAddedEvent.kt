package no.nav.openinghours.slack

import no.nav.openinghours.model.db.ServiceType
import java.util.UUID

data class AddedService(
    val id: UUID,
    val name: String,
    val type: ServiceType,
    val team: String
)

enum class ServiceAddedSource { API, STATUSPLATTFORM_SYNC }

data class ServicesAddedEvent(
    val services: List<AddedService>,
    val source: ServiceAddedSource
)
