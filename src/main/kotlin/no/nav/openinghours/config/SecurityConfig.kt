package no.nav.openinghours.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@Profile("!mock")
class SecurityConfig(
    private val apiKeyAuthenticationFilter: ApiKeyAuthenticationFilter
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/actuator/health/**").permitAll()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().authenticated()
            }
            //.exceptionHandling { it.authenticationEntryPoint(no.nav.openinghours.security.JsonAuthEntryPoint()) }
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .cors { cors -> cors.configurationSource(corsConfigurationSource()) }
            .csrf { csrf -> csrf.disable() }
            .headers { headers ->
                headers.frameOptions { frameOptions -> frameOptions.deny() }
            }
            .build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = listOf(
                "http://localhost:8081",
                "https://openinghours.nav.no",
                "https://openinghours.nav.no",
             //   "https://openinghours.prod-fss-pub.nais.io",
            )
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
            allowedHeaders = listOf("*")
            exposedHeaders = listOf("Location") // optional
            allowCredentials = false
            maxAge = 3600
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }
}