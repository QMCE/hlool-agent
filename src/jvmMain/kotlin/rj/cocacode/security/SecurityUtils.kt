package rj.cocacode.security

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

object SecurityUtils {
    private const val AES_GCM_TAG_LENGTH = 128
    private const val AES_KEY_SIZE = 256
    
    fun hashSHA256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return Base64.getEncoder().encodeToString(hash)
    }
    
    fun hashMD5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    fun encryptAesGcm(plaintext: String, key: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key.toByteArray().copyOf(32), "AES")
        val iv = ByteArray(12)
        java.security.SecureRandom().nextBytes(iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(AES_GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray())
        
        val combined = iv + ciphertext
        return Base64.getEncoder().encodeToString(combined)
    }
    
    fun decryptAesGcm(encrypted: String, key: String): String {
        val combined = Base64.getDecoder().decode(encrypted)
        val iv = combined.copyOfRange(0, 12)
        val ciphertext = combined.copyOfRange(12, combined.size)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key.toByteArray().copyOf(32), "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(AES_GCM_TAG_LENGTH, iv))
        
        return String(cipher.doFinal(ciphertext))
    }
    
    fun generateSecureToken(length: Int = 32): String {
        val bytes = ByteArray(length)
        java.security.SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

object SecretScanner {
    private val patterns = listOf(
        Pattern("GitHub Token", Regex("(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9_]{36,}")),
        Pattern("AWS Access Key", Regex("AKIA[A-Z0-9]{16}")),
        Pattern("AWS Secret Key", Regex("[A-Za-z0-9/+=]{40}")),
        Pattern("Private Key", Regex("-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----")),
        Pattern("Generic API Key", Regex("(?i)(?:api[_-]?key|apikey)\\s*[:=]\\s*['\"]?[A-Za-z0-9]{16,}['\"]?")),
        Pattern("Generic Secret", Regex("(?i)(?:secret|password|passwd|pwd)\\s*[:=]\\s*['\"]?[A-Za-z0-9]{8,}['\"]?")),
        Pattern("Bearer Token", Regex("Bearer\\s+[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]")),
        Pattern("JWT Token", Regex("eyJ[A-Za-z0-9_-]+\\.eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))
    )
    
    data class Secret(
        val type: String,
        val value: String,
        val line: Int,
        val file: String
    )
    
    data class Pattern(
        val name: String,
        val regex: Regex
    )
    
    fun scan(content: String, fileName: String = ""): List<Secret> {
        val secrets = mutableListOf<Secret>()
        
        content.lines().forEachIndexed { lineNum, line ->
            patterns.forEach { pattern ->
                pattern.regex.find(line)?.let { match ->
                    secrets.add(Secret(
                        type = pattern.name,
                        value = match.value.take(20) + "...",
                        line = lineNum + 1,
                        file = fileName
                    ))
                }
            }
        }
        
        return secrets
    }
    
    fun scanFile(path: String): List<Secret> {
        return try {
            scan(java.io.File(path).readText(), path)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

object InputSanitizer {
    fun sanitizeCommand(input: String): String {
        return input.replace(Regex("[;&|`$<>]"), "")
    }
    
    fun sanitizePath(input: String): String {
        return input.replace(Regex("[\\x00-\\x1F]"), "")
            .replace("..", "")
    }
    
    fun sanitizeHtml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
    }
    
    fun isValidFilename(input: String): Boolean {
        return !input.contains(Regex("[\\\\/:*?\"<>|]"))
    }
}