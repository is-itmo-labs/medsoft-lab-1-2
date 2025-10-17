package itmo.daniill

import itmo.daniill.ssl.SslInitializer
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class HospitalChiefApplication

fun main(args: Array<String>) {
    val app = SpringApplication(HospitalChiefApplication::class.java)
    app.addInitializers(SslInitializer())
    app.run(*args)
}