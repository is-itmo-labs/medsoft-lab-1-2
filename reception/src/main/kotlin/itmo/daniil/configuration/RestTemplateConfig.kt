package itmo.daniil.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

@Configuration
class SslRestTemplateConfig {

    @Bean
    fun restTemplate(): RestTemplate {
        val trustStoreFile = File("certs/truststore.jks")
        val trustStorePassword = "changeit".toCharArray()

        val trustStore: KeyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            FileInputStream(trustStoreFile).use { load(it, trustStorePassword) }
        }

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(trustStore)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, tmf.trustManagers, SecureRandom())

        SSLContext.setDefault(sslContext)

        return RestTemplate()
    }
}
