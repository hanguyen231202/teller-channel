package com.tellerchannel.coremock;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint quản trị để bật/tắt sự cố khi demo hoặc viết RCA. Core thật không có cái này. */
@RestController
@RequestMapping("/core/admin/chaos")
public class ChaosController {

    private final ChaosService chaosService;

    public ChaosController(ChaosService chaosService) {
        this.chaosService = chaosService;
    }

    @GetMapping
    public ChaosSettings get() {
        return chaosService.get();
    }

    @PutMapping
    public ChaosSettings update(@Valid @RequestBody ChaosSettings settings) {
        chaosService.update(settings);
        return chaosService.get();
    }

    @PostMapping("/reset")
    public ChaosSettings reset() {
        chaosService.reset();
        return chaosService.get();
    }
}
