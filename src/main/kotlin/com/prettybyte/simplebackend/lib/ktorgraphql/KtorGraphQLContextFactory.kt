package com.prettybyte.simplebackend.lib.ktorgraphql

import com.expediagroup.graphql.server.execution.GraphQLContextFactory
import com.prettybyte.simplebackend.lib.UserIdentity
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import io.jsonwebtoken.Jwts
import io.ktor.request.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.RSAPublicKeySpec
import java.time.Instant
import java.util.*

class KtorGraphQLContextFactory : GraphQLContextFactory<AuthorizedContext, ApplicationRequest> {

    private var googleKeysExpires: Instant? = null
    private var googleKeysCached: GoogleKeys? = null
    private val httpClient = HttpClient.newBuilder().build();

    override suspend fun generateContext(request: ApplicationRequest): AuthorizedContext {
        val jwtString =
            request.headers["Authorization"]?.substring("Bearer ".length) ?: throw RuntimeException("Authorization header with Bearer [JWT] is required")

        try {
            // parse the jwt (before verifying the signature) so we can figure out which key we should use to verify the signature
            val i = jwtString.lastIndexOf('.')
            val publicKeyId = Jwts.parserBuilder().build().parseClaimsJwt(jwtString.substring(0, i + 1)).header["kid"]
            val googleKeys = getGoogleKeys()
            val publicKey = googleKeys.keys.firstOrNull { it.kid == publicKeyId }
                ?: throw RuntimeException("There is no public key with id ${publicKeyId}")
            val externalJws = Jwts.parserBuilder()
                .setSigningKey(publicKey.getKey())
                .build()
                .parseClaimsJws(jwtString)
            validateGoogleJWT(externalJws)
            val errorMessage: String? = null // authorizer.onExchangeJWT(externalJws) TODO
            if (errorMessage != null) {
                println(errorMessage)
                throw RuntimeException()
            }
            return AuthorizedContext(UserIdentity.fromJws(externalJws))
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException()
        }
    }

    private fun validateGoogleJWT(externalJws: Jws<Claims>) {
        if (externalJws.body.subject.isEmpty()) {
            throw RuntimeException("Could not validate JWT")
        }
        // TODO
    }

    private fun getGoogleKeys(): GoogleKeys {   // TODO 1.0: see https://github.com/jwtk/jjwt/issues/236. Or else add error handling.
        val cached = googleKeysCached
        if (cached != null && googleKeysExpires?.isAfter(Instant.now().minusSeconds(60)) == true) {
            return cached
        }
        val httpRequest = HttpRequest.newBuilder().uri(URI.create("https://www.googleapis.com/oauth2/v3/certs")).build();
        val response = httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()).join()
        val maxAgeSeconds = extractMaxAgeSeconds(response.headers().firstValue("cache-control").orElseThrow())
        val responseJson = response.body()
        val keys = Json.decodeFromString<GoogleKeys>(responseJson)
        googleKeysCached = keys
        googleKeysExpires = Instant.now().plusSeconds(maxAgeSeconds)
        return keys
    }

    private fun extractMaxAgeSeconds(cacheControlHeader: String): Long {
        val startIndex = cacheControlHeader.indexOf("max-age=")
        val length = cacheControlHeader.substring(startIndex).indexOf(",")
        return cacheControlHeader.substring(startIndex + "max-age=".length, startIndex + length).toLong()
    }
}


@Serializable
data class GoogleKeys(val keys: List<GoogleKey>)

@Serializable
data class GoogleKey(val e: String, val n: String, val alg: String, val use: String, val kty: String, val kid: String) {
    fun getKey(): PublicKey {
        val modulus = BigInteger(1, Base64.getUrlDecoder().decode(n))
        val exponent = BigInteger(1, Base64.getUrlDecoder().decode(e))
        return KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
    }
}

