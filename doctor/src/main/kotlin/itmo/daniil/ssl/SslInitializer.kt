package itmo.daniil.ssl

import itmo.daniil.ssl.TrustStoreLoader
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext

class SslInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    private val log = LoggerFactory.getLogger(SslInitializer::class.java)
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        log.info("SslInitializer: initializing truststore before context refresh")
        val ok = TrustStoreLoader.initDefaultTrustStore()
        if (!ok) log.warn("SslInitializer: failed to init truststore (continuing without custom truststore)")
    }
}