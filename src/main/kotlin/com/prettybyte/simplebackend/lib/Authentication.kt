package com.prettybyte.simplebackend.lib

/*
import io.grpc.*
import io.grpc.kotlin.CoroutineContextServerInterceptor
import io.grpc.kotlin.GrpcContextElement
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import simplebackend.AuthenticationGrpcKt
import simplebackend.Simplebackend.AccessTokenRequest
import simplebackend.Simplebackend.AccessTokenResponse
import simplebackend.USER_IDENTITY
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.RSAPublicKeySpec
import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS
import java.util.*
import kotlin.coroutines.CoroutineContext

val secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS512)
val userIdentityKey = Context.key<UserIdentity>(USER_IDENTITY)
lateinit var ctx: Context


object AuthenticationInterceptor : ServerInterceptor {

    override fun <ReqT : Any?, RespT : Any?> interceptCall(
        call: ServerCall<ReqT, RespT>?,
        headers: Metadata?,
        next: ServerCallHandler<ReqT, RespT>?
    ): ServerCall.Listener<ReqT> {
        if (next == null) {
            throw RuntimeException("next == null")
        }
        if ((call?.methodDescriptor?.fullMethodName ?: "").contains("Authentication/GetAccessToken")) { // TODO: reflection?
            return next.startCall(call, headers)
        }

        val jwtString = headers?.get(Metadata.Key.of("jwt", Metadata.ASCII_STRING_MARSHALLER))
        if (jwtString == null) {
            call?.close(Status.UNAUTHENTICATED.withDescription("The header 'jwt' must be set"), headers)
            return object : ServerCall.Listener<ReqT>() {}
        }

        try {
            val simpleBackendJwt = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(jwtString)
            validateSimpleBackendJWT(simpleBackendJwt)
            return next.startCall(call, headers)
        } catch (e: Exception) {
            call?.close(Status.UNAUTHENTICATED.withDescription(e.message), headers)
            return object : ServerCall.Listener<ReqT>() {}
        }
    }

    private fun validateSimpleBackendJWT(jwt: Jws<Claims>) {
        // TODO 1.0: validate the claims (iss, sub, aud etc)
    }

}

object UserIdentityInjector : CoroutineContextServerInterceptor() {

    override fun coroutineContext(call: ServerCall<*, *>, headers: Metadata): CoroutineContext {
        val jwtString = headers.get(Metadata.Key.of("jwt", Metadata.ASCII_STRING_MARSHALLER))
        return try {
            val jws = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(jwtString)
            ctx = Context.current().withValue(
                userIdentityKey,
                UserIdentity.fromJws(jws)
            )   // TODO: pretty sure this is not how it should be done. See https://stackoverflow.com/questions/65861235/how-can-i-access-header-in-a-service
            GrpcContextElement(ctx)
        } catch (e: Exception) {
            GrpcContextElement(Context.current())    // TODO: see https://github.com/grpc/grpc-kotlin/issues/221
        }
    }

}

class AuthenticationService<E : IEvent>(private val authorizer: IAuthorizer<E>) : AuthenticationGrpcKt.AuthenticationCoroutineImplBase() {

    private var googleKeysExpires: Instant? = null
    private var googleKeysCached: GoogleKeys? = null
    private val httpClient = HttpClient.newBuilder().build();

    // Called when the client has logged in with Google and wants to exchange the Google-JWT for a SimpleBackend-JWT
    override suspend fun getAccessToken(request: AccessTokenRequest): AccessTokenResponse {
        if (request.externalToken.isEmpty()) {
            throw Status.INVALID_ARGUMENT.withDescription("JWT token missing").asRuntimeException()
        }
        if (request.provider.toLowerCase() != "google") {
            throw Status.INVALID_ARGUMENT.withDescription("Only JWTs from Google are currently supported").asRuntimeException()
        }

        try {
            val googleKeys = getGoogleKeys()
            val publicKey = googleKeys.keys.firstOrNull { it.kid == request.publicKeyId }
                ?: throw Status.INVALID_ARGUMENT.withDescription("There is no public key with id ${request.publicKeyId}").asRuntimeException()
            val externalJws = Jwts.parserBuilder()
                .setSigningKey(publicKey.getKey())
                .build()
                .parseClaimsJws(request.externalToken)
            validateGoogleJWT(externalJws)
            val errorMessage = authorizer.onExchangeJWT(externalJws)
            if (errorMessage != null) throw errorMessage.toGrpcException()
            return AccessTokenResponse.newBuilder().setAccessToken(createJwtToken(externalJws)).build()
        } catch (e: Exception) {
            throw Status.UNAUTHENTICATED.withDescription("Could not validate JWT").asRuntimeException()
        }
    }

    private fun validateGoogleJWT(externalJwt: Jws<Claims>) {
        if (externalJwt.body.subject.isEmpty()) {
            throw Status.UNAUTHENTICATED.withDescription("Could not validate JWT").asRuntimeException()
        }
        // TODO 1.0: validate the claims (iss, sub, aud etc)
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

    private fun createJwtToken(externalJwt: Jws<Claims>): String {
        val now = Instant.now()
        //val secretKey =
        //  Keys.hmacShaKeyFor("superseWEF23F23FEF<DSdsfg<sdgwegcretbiometricpdfgwqegwegweg8097342t3assword!".toByteArray(StandardCharsets.UTF_8))
        val id = externalJwt.body.subject
        return Jwts.builder()      // TODO 1.0: check this before doing anything serious
            .setIssuer("simpleBackend")
            .setSubject(id)  // same id
            .setAudience("you")
            .setExpiration(Date.from(now.plus(30, DAYS)))
            .setNotBefore(Date())
            .setIssuedAt(Date())
            .setId(UUID.randomUUID().toString())
            .signWith(secretKey)
            .compact()
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

 */