package no.nav.openinghours

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class Openinghours

fun main(args: Array<String>) {
    println("DB_DATABASE: ${System.getenv("DB_DATABASE")}")
    println("DB_USERNAME: ${System.getenv("DB_USERNAME")}")
        runApplication<Openinghours>(*args)
        }