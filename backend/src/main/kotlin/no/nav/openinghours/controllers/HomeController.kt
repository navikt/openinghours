package no.nav.openinghours.controllers

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * Backenden er et rent API uten egen forside. Uten denne ville et besøk på
 * `/` (f.eks. for å sjekke at appen kjører lokalt) blitt avvist med et
 * forvirrende "Whitelabel Error Page" (403), siden `/` verken er en kjent
 * ressurs eller tillatt uautentisert i [no.nav.openinghours.config.SecurityConfig].
 * I stedet sender vi videre til Swagger-UI.
 */
@Controller
class HomeController {

    @GetMapping("/")
    fun home(): String = "redirect:/swagger-ui.html"
}
