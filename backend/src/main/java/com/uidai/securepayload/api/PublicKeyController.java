package com.uidai.securepayload.api;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/keys")
@CrossOrigin(origins = "http://localhost:5173")
public class PublicKeyController {
    private static final String MOCK_PUBLIC_KEY = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtwsLHJkgHMzFihzDDxMY
            IClGw2meDlw1AZLIX2E6nH/Y1TETN/EMAYnMzdH3lML181208XEKi7kyBEVtGAyv
            vdaEhobxyKycOL47MCsYOdTvErd5V93HLAmJzB+7/e6FeYEnFrqBzU2fHFpVCxv3
            UWDbxOEN3/rRfkQzMN4tbdbA8ChcP5TZNtSHIN+7cIYJHkX7iQnQ13RlIFu0+xHW
            UKRGvj4z3bhPJQkmW2lLiVpyKAdPN91CZZVWro2OK61iidOPKWUVGLo0aNUwJeLn
            VGlH9Ks0RC0czyi2AoqokOd09bzVlczWM39rp9/j5JlUXdsj5bYHV2r23u/qWV+d
            +QIDAQAB
            -----END PUBLIC KEY-----
            """;

    @GetMapping("/public")
    public PublicKeyResponse publicKey() {
        return new PublicKeyResponse(MOCK_PUBLIC_KEY, "RSA-OAEP-SHA-256-MGF1-SHA-256");
    }
}
