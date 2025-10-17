package lekton.deniill.dao.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "visit")
data class Visit(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "patient_id", nullable = false)
    var patientId: Long,
    @Column(name = "doctor_name", nullable = false)
    var doctorName: String,
    @Column(name = "start_time", nullable = false)
    var startTime: LocalDateTime = LocalDateTime.now(),
    @Column(nullable = false)
    var status: String = "SCHEDULED"
)