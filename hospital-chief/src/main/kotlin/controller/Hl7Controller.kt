package lekton.deniill.controller

import ca.uhn.hl7v2.model.v23.message.ADT_A01
import ca.uhn.hl7v2.parser.PipeParser
import ca.uhn.hl7v2.parser.Parser
import lekton.deniill.dao.model.Patient
import lekton.deniill.dao.repository.PatientRepository
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@RestController
class Hl7Controller(
    private val patientRepository: PatientRepository,
    private val simpMessagingTemplate: SimpMessagingTemplate
) {
    private val logger = LoggerFactory.getLogger(Hl7Controller::class.java)
    private val hl7Logger = LoggerFactory.getLogger("hl7") // dedicated HL7 logger
    private val parser: Parser = PipeParser()

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd") // common HL7 date format

    @PostMapping("/hl7", consumes = [MediaType.TEXT_PLAIN_VALUE], produces = [MediaType.TEXT_PLAIN_VALUE])
    fun receiveHl7(@RequestBody raw: String): String {
        // Log raw HL7 message exactly as received
        hl7Logger.info("RECEIVED HL7:\n{}", raw)

        try {
            val msg = parser.parse(raw) as ADT_A01 // naive cast; better to check message type
            // extract PID segment fields (v2.3)
            val pid = msg.pid
            val patientName = pid.getPatientName(0)
            val lastName = patientName.familyName.value ?: "UNKNOWN"
            val firstName = patientName.givenName.value ?: "UNKNOWN"
//            val dobStr = pid.dateOfBirth.toString()
//            val dob = LocalDate.parse(dobStr.take(8), DATE_FORMAT)

            // Check limit <=10
            val count = patientRepository.count()
            if (count >= 10) {
                logger.warn("Reject adding patient: limit reached ({})", count)
                // Respond with HL7 negative ACK text or plain 400
                val nack = "NACK|Too many patients"
                hl7Logger.info("SENT HL7 NACK:\n{}", nack)
                return nack
            }

            val patient = Patient(firstName = firstName, lastName = lastName, birthDate = LocalDate.now())
            val saved = patientRepository.save(patient)
            logger.info("Saved patient id={}", saved.id)

            // Broadcast to WebSocket topic
            val payload = mapOf(
                "event" to "NEW_PATIENT",
                "patient" to mapOf(
                    "id" to saved.id,
                    "firstName" to saved.firstName,
                    "lastName" to saved.lastName,
                    "birthDate" to saved.birthDate.toString()
                )
            )
            simpMessagingTemplate.convertAndSend("/topic/patients", payload)

            // send ACK (very simple)
            val ack = "ACK|Message received"
            hl7Logger.info("SENT HL7 ACK:\n{}", ack)
            return ack
        } catch (ex: Exception) {
            logger.error("Failed parse HL7", ex)
            val nack = "NACK|ParseError"
            hl7Logger.info("SENT HL7 NACK:\n{}", nack)
            return nack
        }
    }

    @PostMapping("/api/patient-from-json", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun addPatientFromJson(@RequestBody dto: Map<String, String>): Map<String, Any> {
        // Utility endpoint: in case we want to create patient by JSON (used by chief UI maybe)
        val count = patientRepository.count()
        if (count >= 10) {
            return mapOf("error" to "Too many patients")
        }
        val first = dto["firstName"] ?: "Unknown"
        val last = dto["lastName"] ?: "Unknown"
        val dob = LocalDate.parse(dto["birthDate"])
        val saved = patientRepository.save(Patient(firstName = first, lastName = last, birthDate = dob))
        val payload = mapOf("event" to "NEW_PATIENT", "patient" to saved)
        simpMessagingTemplate.convertAndSend("/topic/patients", payload)
        return mapOf("id" to saved.id)
    }
}