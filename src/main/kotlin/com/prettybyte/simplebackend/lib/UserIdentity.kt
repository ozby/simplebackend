package com.prettybyte.simplebackend.lib

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws

data class UserIdentity(val id: String) {

    companion object {
        fun fromTest(name: String): UserIdentity {
            return UserIdentity(id = name)
        }

        fun fromJws(jws: Jws<Claims>): UserIdentity {
            return UserIdentity(id = jws.body.subject)
        }
    }

}
