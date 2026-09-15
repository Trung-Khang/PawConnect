package com.pawconnect.controller.community;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the adoption UI shell. Data and authorization remain exclusively in
 * the adoption REST API; the browser resolves the current page from its URL.
 */
@Controller
public class AdoptionViewController {

    @GetMapping({"/adoptions", "/adoptions/{postId:\\d+}", "/adoptions/my-applications", "/adoptions/manage"})
    public String adoptionPage() {
        return "community/adoptions";
    }
}
