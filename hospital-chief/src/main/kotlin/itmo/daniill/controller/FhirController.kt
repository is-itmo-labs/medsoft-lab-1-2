package itmo.daniill.controller

import ca.uhn.fhir.context.FhirContext
import org.hl7.fhir.r4.model.*
import itmo.daniill.dao.model.Visit
import itmo.daniill.dao.repository.PatientRepository
import itmo.daniill.dao.repository.VisitRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

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
                is Encounter -> handleEncounter(resource)
                else -> ResponseEntity.badRequest().body(mapOf("error" to "Unsupported resource: ${resource.fhirType()}"))
            }
        } catch (e: Exception) {
            log.error("Failed to parse/handle FHIR", e)
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    private fun handleEncounter(e: Encounter): ResponseEntity<Any> {
        val patientRef = e.subject?.reference ?: return ResponseEntity.badRequest().body(mapOf("error" to "Missing subject"))
        val patientId = patientRef.removePrefix("Patient/").toLongOrNull()
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "Bad subject reference: $patientRef"))

        val docName = e.participant.firstOrNull()?.individual?.display ?: "Unknown"
        val status = e.status.toCode().uppercase()
        val reason = e.reasonCodeFirstRep?.text ?: "Не указана"
        val date = e.dateTimeValue()?.value?.toInstant()
            ?.atZone(ZoneId.systemDefault())?.toLocalDateTime() ?: LocalDateTime.now()

        val visit = Visit(patientId = patientId, doctorName = docName, startTime = date, status = status, reason = reason)
        val saved = visitRepo.save(visit)
        log.info("Saved Visit ${saved.id} (${reason}) -> forwarding FHIR Encounter to doctor...")

        forwardToDoctor(e)

        return ResponseEntity.ok(mapOf("visitId" to saved.id))
    }

    private fun forwardToDoctor(e: Encounter) {
        try {
            val json = parser.encodeResourceToString(e)
            val headers = HttpHeaders().apply {
                contentType = MediaType.valueOf("application/fhir+json")
            }
            val entity = HttpEntity(json, headers)
            val fullUrl = "${doctorUrl.trimEnd('/')}/fhir/encounter"

            val resp = rest.postForEntity(fullUrl, entity, String::class.java)
            log.info("Sent FHIR Encounter to doctor -> response: ${resp.statusCode}")
        } catch (ex: Exception) {
            log.error("Failed to forward FHIR to doctor", ex)
        }
    }

    @GetMapping("/fhir/encounter", produces = ["application/fhir+json"])
    fun getAllEncounters(): ResponseEntity<String> {
        val encounters = visitRepo.findAll().map { visit ->
            Encounter().apply {
                id = visit.id.toString()
                status = Encounter.EncounterStatus.fromCode(visit.status.lowercase())
                subject = Reference("Patient/${visit.patientId}")
                addParticipant().individual = Reference().apply { display = visit.doctorName }
                reasonCode = listOf(CodeableConcept().apply { text = visit.reason })

                period = Period().apply {
                    start = Date.from(visit.startTime.atZone(ZoneId.systemDefault()).toInstant())
                }
            }
        }

        val bundle = Bundle().apply {
            type = Bundle.BundleType.COLLECTION
            entry.addAll(encounters.map {
                Bundle.BundleEntryComponent().apply { resource = it }
            })
        }

        val json = parser.encodeResourceToString(bundle)
        return ResponseEntity.ok(json)
    }
}
