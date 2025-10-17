package lekton.deniill.controller

import ca.uhn.fhir.context.FhirContext
import lekton.deniill.dao.model.Patient
import lekton.deniill.dao.model.Visit
import lekton.deniill.dao.repository.PatientRepository
import lekton.deniill.dao.repository.VisitRepository
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Period
import org.hl7.fhir.r4.model.Reference
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
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

    /**
     * Получает FHIR ресурсы (Patient или Encounter) от reception
     */
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

    private fun handlePatient(p: Patient): ResponseEntity<Any> {
        val first = p.firstName
        val last = p.lastName
        val birth = p.birthDate

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

    /**
     * Отправка Encounter в doctor
     */
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

    /**
     * Возвращает все Encounter в FHIR Bundle
     */
    @GetMapping("/fhir/encounter", produces = ["application/fhir+json"])
    fun getAllEncounters(): ResponseEntity<String> {
        val encounters = visitRepo.findAll().map { visit ->
            Encounter().apply {
                id = visit.id.toString()
                status = Encounter.EncounterStatus.fromCode(visit.status.lowercase())
                subject = Reference("Patient/${visit.patientId}")
                period = Period().apply {
                    start = Date.from(visit.startTime.atZone(ZoneId.systemDefault()).toInstant())
                }
                addParticipant().individual = Reference().apply { display = visit.doctorName }
            }
        }

        val bundle = Bundle().apply {
            type = Bundle.BundleType.COLLECTION
            entry.addAll(encounters.map {
                Bundle.BundleEntryComponent().apply { resource = it }
            })
        }

        val json = parser.encodeResourceToString(bundle)
        log.info("Returning ${encounters.size} FHIR Encounters in bundle to doctor")
        return ResponseEntity.ok(json)
    }
}
