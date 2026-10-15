package com.example.catalog;

@org.springframework.web.bind.annotation.RestController
public class CatalogExtensionController {
    @org.springframework.web.bind.annotation.GetMapping(value="/api/catalog-extension", params="mode=one")
    public String one() { return "one"; }
    @org.springframework.web.bind.annotation.GetMapping(value="/api/catalog-extension", params="mode=two")
    public String two() { return "two"; }    @org.springframework.web.bind.annotation.RequestMapping("/api/catalog-any")
    public String any() { return "any"; }

}
