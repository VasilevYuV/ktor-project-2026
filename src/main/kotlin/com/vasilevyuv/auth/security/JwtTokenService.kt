package com.vasilevyuv.auth.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import java.time.Instant

class JwtTokenService(private val settings: JwtSettings) {
    private val algorithm = Algorithm.HMAC256(settings.secret)
    val expiresInSeconds: Long get() = settings.expiresInSeconds

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(settings.issuer)
        .withAudience(settings.audience)
        .withClaimPresence("exp")
        .withClaimPresence("userId")
        .build()

    fun issue(userId: Long): String {
        val now = Instant.now()
        return JWT.create()
            .withIssuer(settings.issuer)
            .withAudience(settings.audience)
            .withIssuedAt(now)
            .withExpiresAt(now.plusSeconds(expiresInSeconds))
            .withClaim("userId", userId.toString())
            .sign(algorithm)
    }
}
