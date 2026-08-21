package com.educloud.user.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 签发 RS256 JWT（Access Token 与服务 Token 共用）。
 * 依据：M03 设计规格第 4.2 节（claims 契约）与第 8 节（服务 Token claims）。
 */
@Component
public final class UserJwtEncoder {

    private final RSASSASigner signer;
    private final String keyId;

    public UserJwtEncoder(JwtKeyProvider keyProvider) {
        Objects.requireNonNull(keyProvider, "keyProvider");
        RSAKey signingKey = keyProvider.signingKey();
        try {
            this.signer = new RSASSASigner(signingKey);
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to initialize RS256 signer", exception);
        }
        this.keyId = signingKey.getKeyID();
    }

    public String encode(JWTClaimsSet claims) {
        try {
            SignedJWT signed = new SignedJWT(
                    new JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256)
                            .keyID(keyId)
                            .build(),
                    claims);
            signed.sign(signer);
            return signed.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to sign JWT", exception);
        }
    }
}
