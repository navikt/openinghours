package no.nav.openinghours.statusplattform

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Configuration
class StatusplattformHttpClientConfig {

    @Bean("statusplattformRestTemplate")
    fun statusplattformRestTemplate(): RestTemplate {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(3))
            setReadTimeout(Duration.ofSeconds(10))
        }
        return RestTemplate(requestFactory).apply {
            errorHandler = StatusplattformResponseErrorHandler()
        }
    }
}

class StatusplattformResponseErrorHandler : ResponseErrorHandler {
    private val log = LoggerFactory.getLogger(StatusplattformResponseErrorHandler::class.java)

    override fun hasError(response: ClientHttpResponse): Boolean =
        !response.statusCode.is2xxSuccessful

    override fun handleError(response: ClientHttpResponse) {
        val statusCode = response.statusCode
        log.error("Statusplattform API returned HTTP status={}", statusCode.value())
        throw StatusplattformApiException(statusCode.value())
    }
}
