package no.nav.openinghours.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class ApiKeyAuthenticationFilter(
    @Value("\${api.security.enabled:true}") private val securityEnabled: Boolean,
    @Value("\${api.security.key:}") private val apiKey: String,
    @Value("\${api.security.header-name:X-API-Key}") private val headerName: String
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter::class.java)

    private val skipPrefixes = listOf("/swagger-ui", "/v3/api-docs", "/actuator", "/error", "/favicon", "/webjars")

    private val protectedPrefixes = listOf("/api/jira/", "/api/user/")

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val path = req.requestURI

        if (!securityEnabled || req.method.equals("OPTIONS", true)) {
            chain.doFilter(req, res); return
        }

        if (skipPrefixes.any { path.startsWith(it) } ||
            protectedPrefixes.none { path.startsWith(it) }) {
            chain.doFilter(req, res); return
        }

        val provided = req.getHeader(headerName)
        if (provided == null) {
            log.debug("API key missing path={}", path)
            res.status = HttpServletResponse.SC_UNAUTHORIZED
            return
        }
        if (provided != apiKey) {
            log.warn("API key invalid path={}", path)
            res.status = HttpServletResponse.SC_UNAUTHORIZED
            return
        }

        log.debug("API key auth OK path={}", path)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("api-user", null, emptyList())

        chain.doFilter(req, res)
    }
}
