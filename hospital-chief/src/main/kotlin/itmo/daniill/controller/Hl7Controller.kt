package itmo.daniill.controller

import ca.uhn.hl7v2.model.Message
import ca.uhn.hl7v2.model.v23.message.ADT_A01
import ca.uhn.hl7v2.model.v23.message.ADT_A23
import ca.uhn.hl7v2.model.v23.segment.MSH
import ca.uhn.hl7v2.parser.PipeParser
import ca.uhn.hl7v2.parser.Parser
import itmo.daniill.dao.model.Patient
import itmo.daniill.dao.repository.PatientRepository
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RestController
class Hl7Controller(
    private val patientRepository: PatientRepository,
    private val simpMessagingTemplate: SimpMessagingTemplate
) {
    private val logger = LoggerFactory.getLogger(Hl7Controller::class.java)
    private val hl7Logger = LoggerFactory.getLogger("hl7")
    private val parser: Parser = PipeParser()

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

    @PostMapping("/hl7", consumes = [MediaType.TEXT_PLAIN_VALUE], produces = [MediaType.TEXT_PLAIN_VALUE])
    fun receiveHl7(@RequestBody raw: String): String {
        hl7Logger.info("RECEIVED HL7:\n{}", raw)

        return try {
            val parsed: Message = parser.parse(raw)

            val msh = parsed.get("MSH") as? MSH
            val trigger = msh?.messageType?.triggerEvent?.value ?: "UNKNOWN"

            when (trigger) {
                "A01" -> {
                    if (parsed is ADT_A01) {
                        handleA01(parsed)
                    } else {
                        val nack = "NACK|Message class mismatch for A01"
                        hl7Logger.info("SENT HL7 NACK:\n{}", nack)
                        nack
                    }
                }
                "A23" -> {
                    if (parsed is ADT_A23) {
                        handleA23(parsed)
                    } else {
                        val nack = "NACK|Message class mismatch for A23"
                        hl7Logger.info("SENT HL7 NACK:\n{}", nack)
                        nack
                    }
                }
                else -> {
                    hl7Logger.info("Unknown HL7 trigger event: {}", trigger)
                    "NACK|Unknown trigger event"
                }
            }
        } catch (ex: Exception) {
            logger.error("Failed parse HL7", ex)
            val nack = "NACK|ParseError"
            hl7Logger.info("SENT HL7 NACK:\n{}", nack)
            nack
        }
    }

    // register patient
    private fun handleA01(msg: ADT_A01): String {
        val pid = msg.pid
        val name = try {
            pid.getPatientName(0)
        } catch (e: Exception) {
            logger.trace(e.message)
            null
        }

        val lastName = name?.familyName?.value ?: "UNKNOWN"
        val firstName = name?.givenName?.value ?: "UNKNOWN"

        val birthStr = pid.dateOfBirth?.timeOfAnEvent?.value
        val birthDate = try {
            if (!birthStr.isNullOrBlank()) LocalDate.parse(birthStr.take(8), DATE_FORMAT)
            else LocalDate.now()
        } catch (e: Exception) {
            logger.warn("Cannot parse DOB from HL7 '{}'", birthStr)
            LocalDate.now()
        }

        if (patientRepository.count() >= 10) {
            val nack = "NACK|Too many patients"
            hl7Logger.info("SENT HL7 NACK:\n{}", nack)
            return nack
        }

        val patient = Patient(firstName = firstName, lastName = lastName, birthDate = birthDate)
        val saved = patientRepository.save(patient)
        logger.info("Saved patient id={}", saved.id)

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

        val ack = "ACK|A01 received"
        hl7Logger.info("SENT HL7 ACK:\n{}", ack)
        return ack
    }

    // delete patient
    private fun handleA23(msg: ADT_A23): String {
        val pid = msg.pid

        val cx = try {
            pid.patientIDExternalID.id.value
        } catch (e: Exception) {
            logger.trace(e.message)
            null
        }

        val id = cx?.toLongOrNull()

        if (id == null) {
            val nack = "NACK|Invalid patient ID"
            hl7Logger.info("SENT HL7 NACK:\n{}", nack)
            return nack
        }

        if (!patientRepository.existsById(id)) {
            val nack = "NACK|Patient not found"
            hl7Logger.info("SENT HL7 NACK:\n{}", nack)
            return nack
        }

        patientRepository.deleteById(id)
        logger.info("Deleted patient id={}", id)

        val payload = mapOf("event" to "DELETE_PATIENT", "id" to id)
        logger.info("Broadcasting WebSocket message: {}", payload)
        simpMessagingTemplate.convertAndSend("/topic/patients", payload)

        val ack = "ACK|A23 received"
        hl7Logger.info("SENT HL7 ACK:\n{}", ack)
        return ack
    }

    @PostMapping("/api/patient-from-json", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun addPatientFromJson(@RequestBody dto: Map<String, String>): Map<String, Any> {
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
