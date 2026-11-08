package com.agricontract.contract.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * The signer's IP for the signature record (signature design §3, secondary evidence only). Behind the gateway the
 * socket address is the gateway's, and X-Forwarded-For can be set by the client, so neither is evidence. Until the
 * gateway forwards an IP it has checked, this returns null: the record says "not collected" instead of storing a
 * value that looks real. The follow-up only changes this class.
 */
@Component
public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        return null;
    }
}
