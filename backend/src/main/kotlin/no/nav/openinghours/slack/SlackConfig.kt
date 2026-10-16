package no.nav.openinghours.slack

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.util.concurrent.TimeUnit

@Configuration
@EnableAsync
class SlackConfig {

    @Bean
    fun slackChannelDelivery(): SlackChannelDelivery =
        SlackChannelDelivery(
            { TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) },
            { Thread.sleep(it) }
        )

    @Bean("slackRestTemplate")
    fun slackRestTemplate(objectMapper: ObjectMapper): RestTemplate {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(3))
            setReadTimeout(Duration.ofSeconds(10))
        }
        return RestTemplate(requestFactory).apply {
            messageConverters.filterIsInstance<MappingJackson2HttpMessageConverter>()
                .forEach { it.objectMapper = objectMapper }
        }
    }
}
