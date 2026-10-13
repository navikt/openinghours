package no.nav.openinghours.statusplattform

import org.springframework.web.client.RestClientException

class StatusplattformApiException(statusCode: Int) :
    RestClientException("Statusplattform API request failed with HTTP status $statusCode")
