package itmo.daniil.ssl

import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

object TrustStoreLoader {
    private val log = LoggerFactory.getLogger(TrustStoreLoader::class.java)

    fun initDefaultTrustStore(path: String? = null, password: String? = null): Boolean {
        val tsPath = path
            ?: System.getProperty("javax.net.ssl.trustStore")
            ?: System.getenv("TRUSTSTORE_PATH")
            ?: File("certs/truststore.jks").absolutePath

        val tsPassword = password
            ?: System.getProperty("javax.net.ssl.trustStorePassword")
            ?: System.getenv("TRUSTSTORE_PASSWORD")
            ?: "changeit"

        val file = File(tsPath)
        if (!file.exists()) {
            log.warn("Truststore not found at {}, skipping SSL init", file.absolutePath)
            return false
        }

        val ks = tryLoadKeyStore(file, tsPassword) ?: run {
            log.warn("Failed to load truststore {}", file.absolutePath)
            return false
        }

        // log aliases
        val aliases = ks.aliases()
        while (aliases.hasMoreElements()) {
            val a = aliases.nextElement()
            log.info("Truststore alias: {} certEntry={} keyEntry={}", a, ks.isCertificateEntry(a), ks.isKeyEntry(a))
        }

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(ks)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, tmf.trustManagers, SecureRandom())
        SSLContext.setDefault(sslContext)

        System.setProperty("javax.net.ssl.trustStore", file.absolutePath)
        System.setProperty("javax.net.ssl.trustStorePassword", tsPassword)
        log.info("SSLContext set from truststore {}", file.absolutePath)
        return true
    }

    private fun tryLoadKeyStore(file: File, password: String): KeyStore? {
        val types = listOf("PKCS12", KeyStore.getDefaultType(), "JKS")
        for (t in types) {
            try {
                val ks = KeyStore.getInstance(t)
                FileInputStream(file).use { ks.load(it, password.toCharArray()) }
                return ks
            } catch (ex: Exception) {
                // ignore, попробуем следующий тип
            }
        }
        return null
    }
}