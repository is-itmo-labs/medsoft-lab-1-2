package itmo.daniill.controller

import itmo.daniill.dao.repository.PatientRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class UiController(
    private val patientRepository: PatientRepository
) {
    private val logger = LoggerFactory.getLogger(UiController::class.java)

    @GetMapping("/chief")
    fun chief(model: Model): String {
        val allPatients = patientRepository.findAll()
        logger.info("=== ПАЦИЕНТЫ ===")
        allPatients.forEach {
            logger.info("id: ${it.id} name ${it.firstName} ${it.lastName} birthDate ${it.birthDate}")
        }
        model.addAttribute("patients", allPatients)
        return "chief"
    }
}