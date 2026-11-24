package com.uidai.securepayload.api;

/**
 * Public-key material and algorithm metadata exposed to trusted sandbox clients.
 *
 * @param public_key PEM-encoded public key
 * @param algorithm encryption algorithm expected by the service
 * @param key_id identifier of the active public key
 */
public record PublicKeyResponse(String public_key, String algorithm, String key_id) {}
