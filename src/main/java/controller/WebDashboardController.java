package controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebDashboardController {

    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }
}