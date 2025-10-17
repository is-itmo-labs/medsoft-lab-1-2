package lekton.deniill.dao.repository

import lekton.deniill.dao.model.Visit
import org.springframework.data.jpa.repository.JpaRepository

interface VisitRepository : JpaRepository<Visit, Long> {
    fun findAllByDoctorNameIgnoreCase(doctorName: String): List<Visit>
}
