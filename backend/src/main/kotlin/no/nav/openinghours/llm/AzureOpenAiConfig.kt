package no.nav.openinghours.llm

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.client.RestTemplate
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

@Configuration
class AzureOpenAiConfig {

    @Bean("azureOpenAiRestTemplate")
    fun azureOpenAiRestTemplate(objectMapper: JsonMapper): RestTemplate {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(5))
            setReadTimeout(Duration.ofSeconds(60))
        }
        return RestTemplate(requestFactory).apply {
            messageConverters.replaceAll {
                if (it is JacksonJsonHttpMessageConverter) JacksonJsonHttpMessageConverter(objectMapper) else it
            }
        }
    }
}
