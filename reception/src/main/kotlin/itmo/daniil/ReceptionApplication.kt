package itmo.daniil

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ReceptionApplication

fun main(args: Array<String>) {
    runApplication<ReceptionApplication>(*args)
}