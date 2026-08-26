package no.nav.openinghours.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        val cluster = System.getenv("NAIS_CLUSTER_NAME").orEmpty()
        val servers = mutableListOf<Server>()
        when {
            cluster.startsWith("prod") -> {
                servers += Server()
                    .url("https://openinghours.nav.no")
                    .description("Production server")
            }
            else -> { // running locally: show both remote environments
                servers += Server()
                    .url("http://localhost:8081")
                    .description("Local development server")
            }
        }

        return OpenAPI()
            .info(
                Info()
                    .title("openinghours")
                    .version("1.0")
                    .description("API for openinghours, provides opening hours for services.")
            )
            .servers(servers)
            .components(
                Components()
                    .addSecuritySchemes(
                        "api_key",
                        SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .`in`(SecurityScheme.In.HEADER)
                            .name("X-API-Key")
                    )
            )
            .security(listOf(io.swagger.v3.oas.models.security.SecurityRequirement().addList("api_key")))
    }
}