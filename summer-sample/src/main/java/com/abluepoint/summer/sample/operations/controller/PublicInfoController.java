package com.abluepoint.summer.sample.operations.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
class PublicInfoController {

    @GetMapping({"/public/info", "/"})
    Map<String, String> info() {
        return Map.of("application", "summer-sample", "status", "ready");
    }
}
