package app.terminalssh.secure.model

/**
 * The connection details a user is allowed to copy (F007).
 *
 * There is deliberately no password or private-key field here, and the builder only ever
 * reads [HostProfile.host], [HostProfile.port], [HostProfile.username] and the label — so
 * "copy connection info" cannot leak a secret even by accident. A vault reference is the
 * closest thing to a credential in a [HostProfile] and it is not reachable from this file.
 */
object ConnectionInfo {

    enum class Field { HOST, PORT, USERNAME, USER_AT_HOST, SSH_COMMAND }

    fun value(profile: HostProfile, field: Field): String = when (field) {
        Field.HOST -> profile.host
        Field.PORT -> profile.port.toString()
        Field.USERNAME -> profile.username
        Field.USER_AT_HOST -> if (profile.port == 22) {
            "${profile.username}@${profile.host}"
        } else {
            "${profile.username}@${profile.host}:${profile.port}"
        }
        Field.SSH_COMMAND -> buildString {
            append("ssh ")
            if (profile.port != 22) append("-p ").append(profile.port).append(' ')
            append(profile.username).append('@').append(profile.host)
        }
    }

    fun label(field: Field): String = when (field) {
        Field.HOST -> "Host"
        Field.PORT -> "Port"
        Field.USERNAME -> "Username"
        Field.USER_AT_HOST -> "user@host"
        Field.SSH_COMMAND -> "ssh command"
    }

    /** In display order. Every entry is safe to copy; nothing here is secret. */
    val fields: List<Field> = listOf(
        Field.USER_AT_HOST, Field.HOST, Field.PORT, Field.USERNAME, Field.SSH_COMMAND,
    )
}
