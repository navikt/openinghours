package no.nav.openinghours.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class GlobalExceptionHandler(
    private val objectMapper: ObjectMapper
) {

    @ExceptionHandler(ResponseStatusException::class)
    fun handleRse(ex: ResponseStatusException): ResponseEntity<Map<String, Any>> {
        val statusCode = ex.statusCode
        val body = mutableMapOf<String, Any>(
            "status" to statusCode.value(),
            "error" to statusCode.toString(),
            "message" to (ex.reason ?: "")
        )

        // Try to extract original Jira error from cause chain
        findHttpStatusCodeException(ex)?.let { httpEx ->
            appendErrorMessages(body, httpEx.responseBodyAsString)
            if (body["message"].toString().isBlank()) {
                body["message"] = httpEx.responseBodyAsString
            }
        } ?: appendErrorMessages(body, ex.reason)

        // If still blank message but have errorMessages, set first
        if (body["message"].toString().isBlank() && body["errorMessages"] is List<*>) {
            body["message"] = (body["errorMessages"] as List<*>).first().toString()
        }

        return ResponseEntity.status(statusCode).body(body)
    }

    @ExceptionHandler(HttpStatusCodeException::class)
    fun handleHttp(ex: HttpStatusCodeException): ResponseEntity<Map<String, Any>> {
        val statusCode = ex.statusCode
        val body = mutableMapOf<String, Any>(
            "status" to statusCode.value(),
            "error" to statusCode.toString(),
            "message" to ex.responseBodyAsString.ifBlank { ex.message ?: "" }
        )
        appendErrorMessages(body, ex.responseBodyAsString)
        if (body["message"].toString().isBlank() && body["errorMessages"] is List<*>) {
            body["message"] = (body["errorMessages"] as List<*>).first().toString()
        }
        return ResponseEntity.status(statusCode).body(body)
    }

    private fun findHttpStatusCodeException(ex: Throwable?): HttpStatusCodeException? {
        var cur = ex
        while (cur != null) {
            if (cur is HttpStatusCodeException) return cur
            cur = cur.cause
        }
        return null
    }

    private fun appendErrorMessages(target: MutableMap<String, Any>, raw: String?) {
        if (raw.isNullOrBlank()) return
        try {
            val node = objectMapper.readTree(raw)
            val arr = node["errorMessages"]
            if (arr != null && arr.isArray && arr.size() > 0) {
                target["errorMessages"] = arr.map { it.asText() }
            }
        } catch (_: Exception) {
            // ignore
        }
    }
}
