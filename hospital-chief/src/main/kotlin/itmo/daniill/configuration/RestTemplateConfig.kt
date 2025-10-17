package itmo.daniill.configuration

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
        // путь к truststore, созданному скриптом (можешь положить в certs/ или в resources/keystore)
        val trustStoreFile = File("certs/truststore.jks")
        val trustStorePassword = "changeit".toCharArray()

        // загружаем truststore (JKS)
        val trustStore: KeyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            FileInputStream(trustStoreFile).use { load(it, trustStorePassword) }
        }

        // создаём TrustManagerFactory на основе этого truststore
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(trustStore)

        // создаём SSLContext, доверяющий нашему truststore
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, tmf.trustManagers, SecureRandom())

        // делаем этот SSLContext JVM-wide (влияет на HttpsURLConnection и RestTemplate по умолчанию)
        SSLContext.setDefault(sslContext)

        // возвращаем обычный RestTemplate — он будет использовать JVM SSLContext
        return RestTemplate()
    }
}
