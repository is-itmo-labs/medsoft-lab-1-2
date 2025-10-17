package itmo.daniill.controller

import itmo.daniill.dao.repository.PatientRepository
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class UiController(
    private val patientRepository: PatientRepository
) {
    @GetMapping("/chief")
    fun chief(model: Model): String {
        val allPatients = patientRepository.findAll()
        println("=== ПАЦИЕНТЫ ===")
        allPatients.forEach { println(it) }
        model.addAttribute("patients", allPatients)
        return "chief"
    }
}