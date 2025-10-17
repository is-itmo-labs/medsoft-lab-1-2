package lekton.deniill.controller

import ca.uhn.hl7v2.model.v23.message.ADT_A01
import ca.uhn.hl7v2.model.v23.segment.PID
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
    @Value("\${CORE_URL:http://localhost:8081/hl7}") private val coreUrl: String
) {
    private val parser = PipeParser()
    private val logger = LoggerFactory.getLogger(ApiHl7Controller::class.java)
    private val hl7Logger = LoggerFactory.getLogger("hl7")

    private val rest = RestTemplate()
    private val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd")

    @PostMapping("/api/register", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun register(@RequestBody body: Map<String, String>): String {
        val first = body["firstName"] ?: "Unknown"
        val last = body["lastName"] ?: "Unknown"
        val birthIso = body["birthDate"] ?: "1900-01-01"
        val birth = LocalDate.parse(birthIso)
        // Build ADT_A01
        val msg = ADT_A01()
        msg.initQuickstart("ADT", "A01", "P")
        val msh = msg.msh
        msh.sendingApplication.namespaceID.value = "ReceptionApp"
        msh.sendingFacility.namespaceID.value = "Reception"
        // PID
        val pid: PID = msg.pid
        pid.getPatientName(0).familyName.value = last
        pid.getPatientName(0).givenName.value = first
        //pid.dateOfBirth.message = birth.format(dateFormat)

        // Encode HL7
        val hl7 = parser.encode(msg)
        hl7Logger.info("SENDING HL7:\n{}", hl7)
        logger.info("Send HL7 to core: {}", coreUrl)
        // Send as text/plain to core
        val headers = HttpHeaders()
        headers.contentType = MediaType.TEXT_PLAIN
        val entity = HttpEntity(hl7, headers)
        val resp = rest.postForObject(coreUrl, entity, String::class.java)
        return resp ?: "No response from core"
    }
}