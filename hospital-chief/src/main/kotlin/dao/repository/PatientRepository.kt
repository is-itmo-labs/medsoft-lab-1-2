package lekton.deniill.dao.repository

import lekton.deniill.dao.model.Patient
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PatientRepository : JpaRepository<Patient, Long>