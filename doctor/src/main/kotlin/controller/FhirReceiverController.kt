package lekton.deniill.controller

import ca.uhn.fhir.context.FhirContext
import org.hl7.fhir.r4.model.Encounter
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList

@Controller
class FhirReceiverController {
    private val log = LoggerFactory.getLogger(FhirReceiverController::class.java)
    private val fhir = FhirContext.forR4()
    private val parser = fhir.newJsonParser().setPrettyPrint(true)
    private val visits = CopyOnWriteArrayList<Map<String, Any>>()

    @PostMapping("/fhir/encounter", consumes = ["application/fhir+json"])
    @ResponseBody
    fun receiveEncounter(@RequestBody raw: String): ResponseEntity<String> {
        log.info("=== FHIR ENCOUNTER RECEIVED ===\n$raw")
        try {
            val enc = parser.parseResource(Encounter::class.java, raw)
            val patientRef = enc.subject?.reference ?: "Unknown"
            val docName = enc.participant.firstOrNull()?.individual?.display ?: "Unknown"
            val start = enc.period?.start?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
            val status = enc.status?.toCode() ?: "unknown"

            val record = mapOf(
                "patientRef" to patientRef,
                "doctorName" to docName,
                "startTime" to start.toString(),
                "status" to status
            )
            visits.add(record)
            return ResponseEntity.ok("{\"result\":\"ok\"}")
        } catch (ex: Exception) {
            log.error("Failed to parse FHIR", ex)
            return ResponseEntity.badRequest().body("{\"error\":\"${ex.message}\"}")
        }
    }

    @GetMapping("/doctor/{name}")
    fun doctorPage(@PathVariable name: String, model: Model): String {
        val filtered = visits.filter { it["doctorName"]?.toString()?.equals(name, true) == true }
        model.addAttribute("doctorName", name)
        model.addAttribute("visits", filtered)
        return "doctor"
    }
}
