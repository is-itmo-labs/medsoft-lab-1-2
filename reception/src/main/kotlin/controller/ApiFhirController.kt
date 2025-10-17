package lekton.deniill.controller

import ca.uhn.fhir.context.FhirContext
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Period
import org.hl7.fhir.r4.model.Reference
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

@RestController
class ApiController(
    @Value("\${reception.core-fhir-url}") private val coreFhirUrl: String
) {
    private val log = LoggerFactory.getLogger(ApiController::class.java)
    private val fhir = FhirContext.forR4()
    private val parser = fhir.newJsonParser().setPrettyPrint(true)
    private val rest = RestTemplate()
    private val objectMapper = jacksonObjectMapper()

    @PostMapping("/api/sendEncounter")
    fun sendEncounter(@RequestBody payload: Map<String, Any>): ResponseEntity<String> {
        return try {
            // payload may already be a FHIR Encounter (constructed by client)
            val resourceType = payload["resourceType"] as? String
            val jsonPayload = objectMapper.writeValueAsString(payload)
            val encounterJson: String = if (resourceType == "Encounter") {
                // normalize via HAPI to ensure FHIR-compliant JSON
                val res = parser.parseResource(jsonPayload)
                parser.encodeResourceToString(res)
            } else {
                // build Encounter from minimal fields
                val patientRef = (payload["subject"] as? Map<*, *>)?.get("reference") as? String
                    ?: (payload["patientId"]?.toString() ?: "")
                val docName = ((payload["participant"] as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("display") as? String
                val reasonText = (payload["reason"] as? String) ?: (payload["reasonCode"]?.toString() ?: "Не указана")
                val dateStr = (payload["period"] as? Map<*, *>)?.get("start") as? String ?: (payload["visitDate"]?.toString() ?: "")

                val enc = Encounter().apply {
                    if (patientRef.isNotBlank()) subject = Reference(patientRef)
                    if (!docName.isNullOrBlank()) addParticipant().individual = Reference().apply { display = docName }
                    status = Encounter.EncounterStatus.INPROGRESS
                    if (dateStr.isNotBlank()) {
                        // parse date string (YYYY-MM-DD or ISO); set as period.start (Date)
                        val localDate = LocalDate.parse(dateStr)
                        val date = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
                        period = Period().apply { start = date }
                    }
                    reasonCode = listOf(CodeableConcept().apply { text = reasonText })
                }
                parser.encodeResourceToString(enc)
            }

            log.info("Sending FHIR Encounter to hospital-chief: {}", coreFhirUrl)
            val headers = HttpHeaders().apply {
                contentType = MediaType.valueOf("application/fhir+json")
                accept = listOf(MediaType.APPLICATION_JSON)
            }

            val entity = HttpEntity(encounterJson, headers)
            val resp = rest.postForObject(coreFhirUrl, entity, String::class.java)
            ResponseEntity.ok(resp ?: "no response")
        } catch (e: Exception) {
            log.error("Failed to send encounter", e)
            ResponseEntity.status(500).body("error: ${e.message}")
        }
    }
}
