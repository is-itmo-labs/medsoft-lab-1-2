package lekton.deniill.controller

import ca.uhn.fhir.context.FhirContext
import lekton.deniill.dao.model.Patient
import lekton.deniill.dao.model.Visit
import lekton.deniill.dao.repository.PatientRepository
import lekton.deniill.dao.repository.VisitRepository
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Patient as FhirPatient
import org.hl7.fhir.instance.model.api.IBaseResource
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@RestController
class FhirController(
    private val patientRepo: PatientRepository,
    private val visitRepo: VisitRepository,
    private val messaging: SimpMessagingTemplate
) {
    private val log = LoggerFactory.getLogger(FhirController::class.java)
    private val fhirLogger = LoggerFactory.getLogger("fhir")
    private val parser = FhirContext.forR4().newJsonParser().setPrettyPrint(true)

    @PostMapping("/fhir", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun receiveFhir(@RequestBody raw: String): ResponseEntity<Any> {
        fhirLogger.info("RECEIVED FHIR RAW:\n{}", raw)

        try {
            val res: IBaseResource = parser.parseResource(raw)

            when (res) {
                is Encounter -> {
                    val patientRef = res.subject?.reference
                        ?: return ResponseEntity.badRequest().body(mapOf("error" to "No patient reference"))
                    val patientId = patientRef.removePrefix("Patient/").toLongOrNull()
                        ?: return ResponseEntity.badRequest().body(mapOf("error" to "Bad patient reference: $patientRef"))

                    val patientOpt = patientRepo.findById(patientId)
                    if (patientOpt.isEmpty) {
                        return ResponseEntity.badRequest()
                            .body(mapOf("error" to "Patient with id $patientId not found"))
                    }

                    val docName = res.participant.firstOrNull()?.individual?.display ?: "Unknown"
                    val startLocal = res.period?.start?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
                        ?: LocalDateTime.now()
                    val status = res.status?.toCode()?.uppercase() ?: "UNKNOWN"

                    val visit = Visit(
                        patientId = patientId,
                        doctorName = docName,
                        startTime = startLocal,
                        status = status
                    )
                    val saved = visitRepo.save(visit)

                    messaging.convertAndSend("/topic/visits", mapOf("event" to "NEW_VISIT", "visit" to saved))
                    log.info("Saved Visit id=${saved.id}")
                    return ResponseEntity.ok(mapOf("status" to "ok", "visitId" to saved.id))
                }

                is FhirPatient -> {
                    val given = res.nameFirstRep.givenAsSingleString ?: "Unknown"
                    val family = res.nameFirstRep.family ?: "Unknown"
                    val birth = res.birthDate?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()
                        ?: LocalDate.now()
                    val saved = patientRepo.save(Patient(firstName = given, lastName = family, birthDate = birth))
                    messaging.convertAndSend("/topic/patients", mapOf("event" to "NEW_PATIENT", "patient" to saved))
                    log.info("Saved Patient id=${saved.id}")
                    return ResponseEntity.ok(mapOf("status" to "ok", "patientId" to saved.id))
                }

                else -> {
                    return ResponseEntity.badRequest()
                        .body(mapOf("error" to "Unsupported FHIR resource: ${res.fhirType()}"))
                }
            }
        } catch (e: Exception) {
            log.error("Failed parse/handle FHIR", e)
            fhirLogger.error("FHIR ERROR: ${e.message}", e)
            return ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }
}
