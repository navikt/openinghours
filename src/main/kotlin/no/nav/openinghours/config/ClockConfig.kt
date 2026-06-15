package no.nav.openinghours.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration
class ClockConfig {

    @Bean
    fun clock(@Value("\${openinghours.timezone}") timezone: String): Clock =
        Clock.system(ZoneId.of(timezone))
}
