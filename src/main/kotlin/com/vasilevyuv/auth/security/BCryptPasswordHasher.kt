package com.vasilevyuv.auth.security

import at.favre.lib.crypto.bcrypt.BCrypt
import com.vasilevyuv.auth.domain.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class BCryptPasswordHasher : PasswordHasher {
    override suspend fun hash(password: String): String = withContext(Dispatchers.Default) {
        val chars = password.toCharArray()
        try {
            BCrypt.withDefaults().hashToString(COST, chars)
        } finally {
            chars.fill('\u0000')
        }
    }

    override suspend fun verify(password: String, hash: String?): Boolean = withContext(Dispatchers.Default) {
        val chars = password.toCharArray()
        try {
            val result = BCrypt.verifyer().verify(chars, hash ?: dummyHash)
            hash != null && result.verified
        } finally {
            chars.fill('\u0000')
        }
    }

    private companion object {
        const val COST = 12

        // Reused only for checks of unknown users; never stored as a user's password.
        val dummyHash: String by lazy {
            BCrypt.withDefaults().hashToString(COST, UUID.randomUUID().toString().toCharArray())
        }
    }
}
