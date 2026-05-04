package no.nav.openinghours.repository

import no.nav.openinghours.model.db.OhGroup
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OhGroupRepository : JpaRepository<OhGroup, UUID>