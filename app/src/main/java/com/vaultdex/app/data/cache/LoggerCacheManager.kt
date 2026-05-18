package com.vaultdex.app.data.cache

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.security.cert.X509Certificate
import java.util.UUID

/**
 * VULN-CACHE-LOG: Sensitive API response and application logs written to cache.
 *
 * Real-world vulnerability: Developers often log full API responses for debugging
 * and write these logs to files in the cache or internal storage. These logs
 * frequently contain PII, tokens, and passwords that were transmitted securely
 * via HTTPS but are then leaked when dumped to local storage.
 *
 * Additionally, caching cryptographic material like certificates/public keys
 * without integrity checks can lead to MITM attacks if the attacker replaces them.
 */
class LoggerCacheManager(private val context: Context) {

    /**
     * VULN: Writes an HTTP response body directly to a cache file.
     * Contains JWTs, passwords, and PII.
     */
    fun logApiResponse(endpoint: String, requestBody: String, responseBody: String) {
        val logFile = File(context.cacheDir, "api_response_cache_${System.currentTimeMillis()}.log")

        val logContent = """
            --- API Request/Response Log ---
            Endpoint: $endpoint
            Timestamp: ${System.currentTimeMillis()}
            Request:
            $requestBody
            Response:
            $responseBody
        """.trimIndent()

        // VULN: Saving sensitive network traffic locally in plaintext
        OutputStreamWriter(FileOutputStream(logFile)).use { writer ->
            writer.write(logContent)
        }
    }

    /**
     * VULN: Caches an X.509 certificate used for certificate pinning.
     * If an attacker modifies this file, they can bypass the app's SSL pinning
     * and perform a MITM attack. VaultGuard's FileScanner detects .crt/.pem files.
     */
    fun cacheCertificatePinningKey() {
        val certDir = File(context.filesDir, "certs").apply { mkdirs() }
        val certFile = File(certDir, "pinned_server_cert.pem")

        // VULN: A real PEM-formatted certificate
        val dummyCert = """
            -----BEGIN CERTIFICATE-----
            MIIDdTCCAl2gAwIBAgILBAAAAAABFUtaNXUwDQYJKoZIhvcNAQEFBQAwVzELMAkG
            A1UEBhMCQkUxGTAXBgNVBAoTEEdsb2JhbFNpZ24gbnYtc2ExEDAOBgNVBAsTB1Jv
            b3QgQ0ExGzAZBgNVBAMTEkdsb2JhbFNpZ24gUm9vdCBDQTAeFw05ODA5MDExMjAw
            MDBaFw0yODAxMjgxMjAwMDBaMFcxCzAJBgNVBAYTAkJFMRkwFwYDVQQKExBHbG9i
            YWxTaWduIG52LXNhMRAwDgYDVQQLEwdSb290IENBMRswGQYDVQQDExJHbG9iYWxT
            aWduIFJvb3QgQ0EwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDaDuaZ
            jc6j40+Kfvvxi4Mla+pIH/EqsLmVEQS98GPR4mdmzxzdzxtIK+6NiY6arymAZavp
            xy0Sy6scTHAHoT0KMM0VjU/43dSMUBUc71DuxC73/OlS8pN94G3VNTCOXkNz8kHp
            1Wrjsok6Vxc4o8Zh24oQ/0O+B+j1zB7xT4K4N1Z1wYx8qB7rPZ8bLwB2/QZ0T27X
            v8L1E7O14Fm/X1zD8yWzB+xR4oB9W7/6aG2b/D9L1R9z9p1a8s+n+4Z3p5f5e8s7
            g3x4r4s4u9s8q8b7t4g4f2s3f3d3f4g4h5j5k6l7m8n9o0p1q2r3s4t5u6v7w8x9
            y0z1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p7q8r9s0t1u2v3w4x5y6z7a8b9c0d1
            -----END CERTIFICATE-----
        """.trimIndent()

        OutputStreamWriter(FileOutputStream(certFile)).use { writer ->
            writer.write(dummyCert)
        }
    }
}
