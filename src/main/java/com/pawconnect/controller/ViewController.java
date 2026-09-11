package com.pawconnect.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping({"/", "/shop"})
    public String shopPage() {
        return "shop/index"; // points to templates/shop/index.html
    }

    @GetMapping("/service")
    public String bookingPage() {
        return "service/booking"; // points to templates/service/booking.html
    }
}
