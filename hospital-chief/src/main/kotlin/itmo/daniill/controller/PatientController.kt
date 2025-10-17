package itmo.daniill.controller

import itmo.daniill.dao.repository.PatientRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class PatientController(private val repo: PatientRepository) {

    @GetMapping("/api/patients")
    fun listPatients(): List<Map<String, Any>> {
        return repo.findAll().map {
            mapOf(
                "id" to it.id,
                "firstName" to it.firstName,
                "lastName" to it.lastName,
                "birthDate" to it.birthDate.toString()
            )
        }
    }
}