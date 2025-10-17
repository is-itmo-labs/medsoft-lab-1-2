package lekton.deniill.controller

import ca.uhn.fhir.context.FhirContext
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Patient as FhirPatient
import lekton.deniill.dao.model.Patient
import lekton.deniill.dao.model.Visit
import lekton.deniill.dao.repository.PatientRepository
import lekton.deniill.dao.repository.VisitRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@RestController
class FhirController(
    private val patientRepo: PatientRepository,
    private val visitRepo: VisitRepository,
    @Value("\${doctor.url}") private val doctorUrl: String
) {
    private val log = LoggerFactory.getLogger(FhirController::class.java)
    private val fhir = FhirContext.forR4()
    private val parser = fhir.newJsonParser().setPrettyPrint(true)
    private val rest = RestTemplate()

    @PostMapping("/fhir", consumes = ["application/fhir+json"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun receiveFhir(@RequestBody raw: String): ResponseEntity<Any> {
        log.info("=== RAW FHIR RECEIVED ===\n$raw")

        return try {
            val resource = parser.parseResource(raw)
            when (resource) {
                is FhirPatient -> handlePatient(resource)
                is Encounter -> handleEncounter(resource)
                else -> ResponseEntity.badRequest().body(mapOf("error" to "Unsupported resource: ${resource.fhirType()}"))
            }
        } catch (e: Exception) {
            log.error("Failed to parse/handle FHIR", e)
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    private fun handlePatient(p: FhirPatient): ResponseEntity<Any> {
        val first = p.nameFirstRep.givenAsSingleString ?: "Unknown"
        val last = p.nameFirstRep.family ?: "Unknown"
        val birth = p.birthDate?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: LocalDate.now()

        val saved = patientRepo.save(Patient(firstName = first, lastName = last, birthDate = birth))
        log.info("Saved FHIR patient id=${saved.id}")
        return ResponseEntity.ok(mapOf("patientId" to saved.id))
    }

    private fun handleEncounter(e: Encounter): ResponseEntity<Any> {
        val patientRef = e.subject?.reference ?: return ResponseEntity.badRequest().body(mapOf("error" to "Missing subject"))
        val patientId = patientRef.removePrefix("Patient/").toLongOrNull()
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "Bad subject reference: $patientRef"))

        val docName = e.participant.firstOrNull()?.individual?.display ?: "Unknown"
        val start = e.period?.start?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDateTime() ?: LocalDateTime.now()
        val status = e.status.toCode().uppercase()

        val visit = Visit(patientId = patientId, doctorName = docName, startTime = start, status = status)
        val saved = visitRepo.save(visit)
        log.info("Saved Visit ${saved.id} -> forwarding FHIR Encounter to doctor...")

        forwardToDoctor(e)

        return ResponseEntity.ok(mapOf("visitId" to saved.id))
    }

    private fun forwardToDoctor(e: Encounter) {
        try {
            val json = parser.encodeResourceToString(e)
            val headers = HttpHeaders()
            headers.contentType = MediaType.valueOf("application/fhir+json")
            val entity = HttpEntity(json, headers)
            val resp = rest.postForEntity(doctorUrl, entity, String::class.java)
            log.info("Sent FHIR Encounter to doctor -> response: ${resp.statusCode}")
        } catch (ex: Exception) {
            log.error("Failed to forward FHIR to doctor", ex)
        }
    }

    @GetMapping("/visits/doctor/{name}")
    fun getVisitsByDoctor(@PathVariable name: String): List<Visit> =
        visitRepo.findAllByDoctorNameIgnoreCase(name)
}
