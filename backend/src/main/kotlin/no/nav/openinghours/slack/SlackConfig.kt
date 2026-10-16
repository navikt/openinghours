package no.nav.openinghours.slack

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Configuration
@EnableAsync
class SlackConfig {

    @Bean("slackRestTemplate")
    fun slackRestTemplate(): RestTemplate {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(3))
            setReadTimeout(Duration.ofSeconds(10))
        }
        return RestTemplate(requestFactory)
    }
}
