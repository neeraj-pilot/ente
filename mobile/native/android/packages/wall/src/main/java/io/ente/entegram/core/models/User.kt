package io.ente.entegram.core.models

data class AuthenticatedUser(
    val id: Long,
    val email: String,
    // Legacy auth username if the backend still returns one.
    // User-facing handles belong to walls and should come from wall state.
    val username: String?,
    val sessionToken: String,
)

data class LoginResult(
    val user: AuthenticatedUser,
    val masterKey: ByteArray,
    val secretKey: ByteArray,
    val publicKey: ByteArray = byteArrayOf(),
    val recoveryKey: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LoginResult) return false
        return user == other.user &&
            masterKey.contentEquals(other.masterKey) &&
            secretKey.contentEquals(other.secretKey) &&
            publicKey.contentEquals(other.publicKey) &&
            recoveryKey == other.recoveryKey
    }

    override fun hashCode(): Int {
        var result = user.hashCode()
        result = 31 * result + masterKey.contentHashCode()
        result = 31 * result + secretKey.contentHashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + (recoveryKey?.hashCode() ?: 0)
        return result
    }
}
