package configuration

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod

@Component
class StartupCacheLoader(
    private val cache: VisitCache,
    @Value("\${doctor.name}") private val doctorName: String,
    @Value("\${hospital-chief.url}") private val chiefUrl: String
) {
    private val log = LoggerFactory.getLogger(StartupCacheLoader::class.java)
    private val rest = RestTemplate()

    @PostConstruct
    fun init() {
        try {
            val url = "$chiefUrl/visits/doctor/${doctorName}"
            log.info("Doctor startup: fetching initial visits from $url")

            val response = rest.exchange(
                url,
                HttpMethod.GET,
                null,
                object : ParameterizedTypeReference<List<Map<String, Any>>>() {}
            )

            val visits = response.body ?: emptyList()
            cache.replaceAll(visits)
            log.info("Doctor cache initialized with ${visits.size} visits")
        } catch (e: Exception) {
            log.error("Failed to load initial visits from hospital-chief", e)
        }
    }
}
