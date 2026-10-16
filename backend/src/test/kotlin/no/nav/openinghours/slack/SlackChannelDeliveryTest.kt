package no.nav.openinghours.slack

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class SlackChannelDeliveryTest {
    private var now = 0L
    private val waits = mutableListOf<Long>()
    private val delivery = SlackChannelDelivery({ now }, { waits += it; now += it })

    private fun rateLimited(retryAfter: String? = "3") =
        HttpClientErrorException.create(
            HttpStatus.TOO_MANY_REQUESTS,
            "Too Many Requests",
            HttpHeaders().apply { retryAfter?.let { set("Retry-After", it) } },
            byteArrayOf(),
            null
        )

    @Test
    fun `waits for Retry-After before retrying and paces the next message`() {
        var attempts = 0
        val result = delivery.send {
            attempts++
            if (attempts == 1) throw rateLimited()
            "sent"
        }
        delivery.send { "next" }

        assertThat(result).isEqualTo("sent")
        assertThat(attempts).isEqualTo(2)
        assertThat(waits).containsExactly(3000L, 1000L)
    }

    @Test
    fun `bounds retries and retains the cooldown after exhaustion`() {
        var attempts = 0
        assertThrows<HttpClientErrorException.TooManyRequests> {
            delivery.send {
                attempts++
                throw rateLimited()
            }
        }
        assertThat(attempts).isEqualTo(1 + SlackChannelDelivery.MAX_RETRIES)
        assertThat(waits).containsExactly(3000L, 3000L, 3000L)

        delivery.send { "next" }
        assertThat(waits).containsExactly(3000L, 3000L, 3000L, 3000L)
    }

    @Test
    fun `invalid or absent Retry-After uses minimum pacing`() {
        listOf(null, "invalid", "-1", "9223372036854775807", "0").forEach { header ->
            var attempts = 0
            delivery.send {
                if (attempts++ == 0) throw rateLimited(header)
            }
        }
        assertThat(waits).allMatch { it == 1000L }
    }

    @Test
    fun `does not retry other HTTP errors`() {
        var attempts = 0
        assertThrows<HttpClientErrorException> {
            delivery.send {
                attempts++
                throw HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR)
            }
        }
        assertThat(attempts).isEqualTo(1)
        assertThat(waits).isEmpty()
    }

    @Test
    fun `interruption preserves interrupt status and prevents sending`() {
        val interrupted = SlackChannelDelivery({ 0L }, { throw InterruptedException() })
        interrupted.send { "first" }
        var sent = false
        try {
            assertThrows<RestClientException> {
                interrupted.send { sent = true }
            }
            assertThat(Thread.currentThread().isInterrupted).isTrue()
            assertThat(sent).isFalse()
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `concurrent events share pacing and rate limit cooldown`() {
        val time = AtomicLong()
        val firstAttempt = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val startTimes = mutableListOf<Long>()
        val shared = SlackChannelDelivery(time::get, { time.addAndGet(it) })
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit {
                var attempts = 0
                shared.send {
                    startTimes += time.get()
                    if (attempts++ == 0) {
                        firstAttempt.countDown()
                        check(secondStarted.await(5, TimeUnit.SECONDS))
                        throw rateLimited("4")
                    }
                }
            }
            val second = executor.submit {
                check(firstAttempt.await(5, TimeUnit.SECONDS))
                secondStarted.countDown()
                shared.send { startTimes += time.get() }
            }
            first.get(10, TimeUnit.SECONDS)
            second.get(10, TimeUnit.SECONDS)
            assertThat(startTimes).containsExactly(0L, 4000L, 5000L)
        } finally {
            executor.shutdownNow()
        }
    }
}
