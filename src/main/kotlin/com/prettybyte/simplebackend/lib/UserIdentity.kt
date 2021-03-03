package com.prettybyte.simplebackend.lib

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws

private const val systemUserIdentityId = "The System User Identity Id"

data class UserIdentity(val id: String) {

    fun isSystem(): Boolean {
        return id == systemUserIdentityId
    }

    companion object {
        fun fromTest(name: String): UserIdentity {
            return UserIdentity(id = name)
        }

        fun fromJws(jws: Jws<Claims>): UserIdentity {
            if (jws.body.subject == systemUserIdentityId) {
                throw Exception()
            }
            return UserIdentity(id = jws.body.subject)
        }

        fun system(): UserIdentity {
            return UserIdentity(systemUserIdentityId)
        }
    }

}
