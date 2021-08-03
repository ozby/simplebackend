package com.prettybyte.simplebackend.lib

data class UserIdentity(val id: String) {

    fun isSystem(): Boolean {
        return id == systemUserIdentityId
    }

    companion object {
        val systemUserIdentityId = "The System User Identity Id"

        fun fromTest(name: String): UserIdentity {
            return UserIdentity(id = name)
        }

        fun system(): UserIdentity {
            return UserIdentity(systemUserIdentityId)
        }
    }

}
