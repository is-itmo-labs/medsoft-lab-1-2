package itmo.daniil.controller

import ca.uhn.fhir.context.FhirContext
import jakarta.annotation.PostConstruct
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Encounter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.client.RestTemplate
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList

@Controller
class FhirReceiverController(
    @Value("\${hospital.url}") private val hospitalUrl: String
) {
    private val log = LoggerFactory.getLogger(FhirReceiverController::class.java)
    private val fhir = FhirContext.forR4()
    private val parser = fhir.newJsonParser().setPrettyPrint(true)
    private val rest = RestTemplate()
    private val visits = CopyOnWriteArrayList<Map<String, Any>>()

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        initCache() // выполняется после создания context и после инициализаций
    }

    @PostConstruct
    fun initCache() {
        try {
            val url = "${hospitalUrl.trimEnd('/')}/fhir/encounter"
            val resp = rest.getForEntity(url, String::class.java)
            val raw = resp.body ?: return

            val bundle = parser.parseResource(Bundle::class.java, raw)
            val encounters = bundle.entry.mapNotNull { it.resource as? Encounter }

            visits.clear()
            encounters.forEach { addEncounterToCache(it) }
            log.info("Doctor cache initialized with ${visits.size} encounters")
        } catch (ex: Exception) {
            log.error("Failed to initialize cache from hospital-chief", ex)
        }
    }

    @PostMapping("/fhir/encounter", consumes = ["application/fhir+json"])
    @ResponseBody
    fun receiveEncounter(@RequestBody raw: String): ResponseEntity<String> {
        return try {
            val enc = parser.parseResource(Encounter::class.java, raw)
            addEncounterToCache(enc)
            ResponseEntity.ok("{\"result\":\"ok\"}")
        } catch (ex: Exception) {
            log.error("Failed to parse FHIR", ex)
            ResponseEntity.badRequest().body("{\"error\":\"${ex.message}\"}")
        }
    }

    private fun addEncounterToCache(enc: Encounter) {
        val patientRef = enc.subject?.reference ?: "Unknown"
        val docName = enc.participant.firstOrNull()?.individual?.display ?: "Unknown"
        val reason = enc.reasonCodeFirstRep?.text ?: "Не указана"
        val date = enc.period?.start?.toInstant()
            ?.atZone(ZoneId.systemDefault())?.toLocalDate()?.toString() ?: "Unknown"
        val status = enc.status?.toCode() ?: "unknown"

        visits.add(
            mapOf(
                "patientRef" to patientRef,
                "doctorName" to docName,
                "visitDate" to date,
                "status" to status,
                "reason" to reason
            )
        )
    }

    @GetMapping("/doctor/{name}")
    fun doctorPage(@PathVariable name: String, model: Model): String {
        val filtered = visits.filter { it["doctorName"]?.toString()?.equals(name, true) == true }
        model.addAttribute("doctorName", name)
        model.addAttribute("visits", filtered)
        return "doctor"
    }
}