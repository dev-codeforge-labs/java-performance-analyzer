package com.devmanchego.performanceanalyzer.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaController {

    // Forward all non-API, non-actuator, non-static routes to Angular's index.html
    @RequestMapping(value = {
            "/",
            "/dashboard",
            "/hotspots",
            "/call-tree",
            "/timeline",
            "/diff",
            "/gc",
            "/heap",
            "/settings"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
