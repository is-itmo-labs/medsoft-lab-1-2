package configuration

import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

@Component
class VisitCache {
    private val visits = CopyOnWriteArrayList<Map<String, Any>>()

    fun getAll(): List<Map<String, Any>> = visits.toList()

    fun replaceAll(newData: List<Map<String, Any>>) {
        visits.clear()
        visits.addAll(newData)
    }

    fun add(visit: Map<String, Any>) {
        visits.add(visit)
    }
}
