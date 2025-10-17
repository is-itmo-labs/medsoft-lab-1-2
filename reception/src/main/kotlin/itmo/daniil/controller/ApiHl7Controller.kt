package itmo.daniil.controller

import ca.uhn.hl7v2.model.v23.message.ADT_A01
import ca.uhn.hl7v2.model.v23.message.ADT_A23
import ca.uhn.hl7v2.parser.PipeParser
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RestController
class ApiHl7Controller(
    @Value("\${reception.core-hl7-url}") private val coreUrl: String
) {
    private val parser = PipeParser()
    private val logger = LoggerFactory.getLogger(ApiHl7Controller::class.java)
    private val hl7Logger = LoggerFactory.getLogger("hl7")

    private val rest = RestTemplate()
    private val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd")

    // register patient
    @PostMapping("/api/register", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun register(@RequestBody body: Map<String, String>): String {
        val first = body["firstName"] ?: "Unknown"
        val last = body["lastName"] ?: "Unknown"
        val birthIso = body["birthDate"] ?: "1900-01-01"
        val birth = LocalDate.parse(birthIso)

        val msg = ADT_A01().apply {
            initQuickstart("ADT", "A01", "P")
            msh.sendingApplication.namespaceID.value = "ReceptionApp"
            msh.sendingFacility.namespaceID.value = "Reception"
            pid.getPatientName(0).familyName.value = last
            pid.getPatientName(0).givenName.value = first
            pid.dateOfBirth.timeOfAnEvent.value = birth.format(dateFormat)
        }

        val hl7 = parser.encode(msg)

        logFullHl7("SENDING HL7 (REGISTER)", hl7)

        val headers = HttpHeaders().apply { contentType = MediaType.TEXT_PLAIN }
        val entity = HttpEntity(hl7, headers)
        val resp = rest.postForObject(coreUrl, entity, String::class.java)

        logger.info("HL7 A01 sent to core: response='{}'", resp)
        return resp ?: "No response from core"
    }

    // delete patient
    @PostMapping("/api/delete", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun deletePatient(@RequestBody body: Map<String, String>): String {
        val id = body["id"] ?: return "Missing id"

        val msg = ADT_A23().apply {
            initQuickstart("ADT", "A23", "P")
            msh.sendingApplication.namespaceID.value = "ReceptionApp"
            msh.sendingFacility.namespaceID.value = "Reception"
            pid.patientIDExternalID.id.value = id
        }

        val hl7 = parser.encode(msg)
        logFullHl7("SENDING HL7 (DELETE)", hl7)

        val headers = HttpHeaders().apply { contentType = MediaType.TEXT_PLAIN }
        val entity = HttpEntity(hl7, headers)
        val resp = rest.postForObject(coreUrl, entity, String::class.java)

        logger.info("HL7 A23 sent to core: response='{}'", resp)
        return resp ?: "No response from core"
    }

    private fun logFullHl7(prefix: String, message: String) {
        val normalized = message.replace("\r", "\n").trim()
        hl7Logger.info(
            "\n======================= [{}] =======================\n{}\n====================================================",
            prefix,
            normalized
        )
    }
}
