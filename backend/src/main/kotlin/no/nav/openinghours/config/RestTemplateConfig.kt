package no.nav.openinghours.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import java.io.IOException
import java.nio.charset.StandardCharsets

@Configuration
class RestTemplateConfig {

    private val logger = LoggerFactory.getLogger(RestTemplateConfig::class.java)

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate().apply {

            // Lightweight request/response logging (INFO) + failures (ERROR)
            interceptors.add { request, body, execution ->
                val start = System.currentTimeMillis()
                logger.info("JIRA request method={} uri={}", request.method, request.uri)
                try {
                    val response = execution.execute(request, body)
                    val duration = System.currentTimeMillis() - start
                    logger.info(
                        "JIRA response method={} uri={} status={} durationMs={}",
                        request.method, request.uri, response.statusCode, duration
                    )
                    response
                } catch (e: Exception) {
                    val duration = System.currentTimeMillis() - start
                    logger.error(
                        "JIRA request failed method={} uri={} durationMs={} message={}",
                        request.method, request.uri, duration, e.message, e
                    )
                    throw e
                }
            }

            errorHandler = object : ResponseErrorHandler {
                override fun hasError(response: ClientHttpResponse): Boolean =
                    !response.statusCode.is2xxSuccessful

                @Throws(IOException::class)
                override fun handleError(response: ClientHttpResponse) {
                    val responseBody = try {
                        response.body.readBytes().toString(StandardCharsets.UTF_8)
                    } catch (e: Exception) {
                        "Unable to read response body: ${e.message}"
                    }

                    // Log error so it appears in NAIS logs
                    logger.error(
                        "JIRA error status={} bodyLength={} bodySnippet={}",
                        response.statusCode,
                        responseBody.length,
                        responseBody.take(500)
                    )

                    val msg = when (response.statusCode) {
                        HttpStatus.UNAUTHORIZED -> "Invalid Jira credentials"
                        HttpStatus.NOT_FOUND -> "Resource not found"
                        HttpStatus.BAD_REQUEST -> "Bad request"
                        HttpStatus.INTERNAL_SERVER_ERROR -> "Server error"
                        else -> "Error response: ${response.statusCode}"
                    }
                    throw IllegalStateException("$msg. Response: $responseBody")
                }
            }
        }
    }
}