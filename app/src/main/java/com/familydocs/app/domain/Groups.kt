package com.familydocs.app.domain

import java.security.SecureRandom

/** Group codes: 9 random letters, digits and symbols in threes, e.g. "K7#-Q2@-XM4" (no 0/O, 1/I/L look-alikes). */
object GroupCodes {
    const val CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789#@$%&+*"
    const val LENGTH = 9
    private val rnd = SecureRandom()
    fun new(): String = pretty((1..LENGTH).map { CHARS[rnd.nextInt(CHARS.length)] }.joinToString(""))
    /** "K7#Q2@XM4" → "K7#-Q2@-XM4". */
    fun pretty(code: String): String = code.chunked(3).joinToString("-")
    /** Codes compare without case, spaces or dashes ("k7# q2@ xm4" = "K7#-Q2@-XM4"). */
    fun normalize(code: String) = code.uppercase().filter { it != ' ' && it != '-' }
}
