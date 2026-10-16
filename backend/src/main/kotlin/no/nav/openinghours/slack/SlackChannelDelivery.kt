package no.nav.openinghours.slack

import org.slf4j.LoggerFactory
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException

class SlackChannelDelivery(
    private val nowMillis: () -> Long,
    private val sleep: (Long) -> Unit
) {
    private val log = LoggerFactory.getLogger(SlackChannelDelivery::class.java)
    private var nextAllowedAt: Long? = null

    companion object {
        const val MAX_RETRIES = 3
        const val INTERVAL_MILLIS = 1000L
    }

    @Synchronized
    fun <T> send(request: () -> T): T {
        var retries = 0
        while (true) {
            awaitSlot()
            nextAllowedAt = nowMillis() + INTERVAL_MILLIS
            try {
                return request()
            } catch (e: HttpClientErrorException.TooManyRequests) {
                val seconds = e.responseHeaders?.getFirst("Retry-After")?.toLongOrNull()
                val delay = if (seconds != null && seconds >= 0 && seconds <= Long.MAX_VALUE / 1000) {
                    seconds * 1000
                } else {
                    log.warn("Slack returned HTTP 429 without a valid Retry-After; using one second")
                    INTERVAL_MILLIS
                }
                nextAllowedAt = nowMillis() + maxOf(INTERVAL_MILLIS, delay)
                if (retries >= MAX_RETRIES) throw e
                retries++
                log.warn("Slack rate limited delivery; retry={} delayMillis={}", retries, delay)
            }
        }
    }

    private fun awaitSlot() {
        while (true) {
            val remaining = nextAllowedAt?.let { it - nowMillis() } ?: return
            if (remaining <= 0) return
            try {
                sleep(remaining)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw RestClientException("Interrupted while waiting to send a Slack message", e)
            }
        }
    }
}
