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

//fun itmo.daniil.main(args: Array<String>) {
//
////    val log = LoggerFactory.getLogger("DoctorMain")
////
////    try {
////        // 1) Определяем путь к truststore: сначала системное свойство, потом env, потом ./certs/truststore.jks
////        val configured = System.getProperty("javax.net.ssl.trustStore")
////            ?: System.getenv("TRUSTSTORE_PATH")
////        val defaultPath = File("certs/truststore.jks").absolutePath
////        val trustStorePath = if (!configured.isNullOrBlank()) configured else defaultPath
////
////        // пароль: сначала системное свойство, потом env, потом changeit
////        val trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword")
////            ?: System.getenv("TRUSTSTORE_PASSWORD")
////            ?: "changeit"
////
////        val tsFile = File(trustStorePath)
////        if (tsFile.exists()) {
////            log.info("Loading truststore from: {}", tsFile.absolutePath)
////
////            // пробуем загрузить как PKCS12, потом как дефолтный тип, потом JKS
////            val tryTypes = listOf("PKCS12", KeyStore.getDefaultType(), "JKS")
////            var loadedKeyStore: KeyStore? = null
////            for (type in tryTypes) {
////                try {
////                    val ks = KeyStore.getInstance(type)
////                    FileInputStream(tsFile).use { fis -> ks.load(fis, trustStorePassword.toCharArray()) }
////                    loadedKeyStore = ks
////                    log.info("Loaded truststore as type: {}", type)
////                    break
////                } catch (ex: Exception) {
////                    log.debug("Can't load truststore {} as {}: {}", tsFile.absolutePath, type, ex.message)
////                }
////            }
////
////            if (loadedKeyStore != null) {
////                // логируем алиасы (полезно для отладки)
////                val aliases = loadedKeyStore.aliases()
////                while (aliases.hasMoreElements()) {
////                    val alias = aliases.nextElement()
////                    log.info("Truststore alias found: {} (certEntry={}, keyEntry={})",
////                        alias, loadedKeyStore.isCertificateEntry(alias), loadedKeyStore.isKeyEntry(alias))
////                }
////
////                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
////                tmf.init(loadedKeyStore)
////
////                val sslContext = SSLContext.getInstance("TLS")
////                sslContext.init(null, tmf.trustManagers, SecureRandom())
////
////                // установка JVM-wide SSLContext до запуска Spring
////                SSLContext.setDefault(sslContext)
////
////                // выставим системные свойства на всякий случай (некоторые либы читают их)
////                System.setProperty("javax.net.ssl.trustStore", tsFile.absolutePath)
////                System.setProperty("javax.net.ssl.trustStorePassword", trustStorePassword)
////
////                log.info("Default SSLContext set using truststore {}", tsFile.absolutePath)
////            } else {
////                log.warn("Failed to load truststore {} as any known type (PKCS12/JKS)", tsFile.absolutePath)
////            }
////        } else {
////            log.warn("Truststore file not found at {}, continuing without custom truststore", tsFile.absolutePath)
////        }
////    } catch (ex: Exception) {
////        log.error("Error while initializing SSL truststore, continuing. Exception:", ex)
////    }
//
//    runApplication<itmo.daniil.DoctorApplication>(*args)
//}