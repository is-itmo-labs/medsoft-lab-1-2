package lekton.deniill.controller

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class UiController {
    @GetMapping("/")
    fun index(model: Model): String {
        return "reception"
    }

    @GetMapping("/visit")
    fun visit(model: Model): String {
        return "visit"
    }
}