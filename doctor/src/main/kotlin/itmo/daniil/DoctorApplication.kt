package itmo.daniil

import itmo.daniil.ssl.SslInitializer
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class DoctorApplication

fun main(args: Array<String>) {
    val app = SpringApplication(DoctorApplication::class.java)
    app.addInitializers(SslInitializer())
    app.run(*args)
}