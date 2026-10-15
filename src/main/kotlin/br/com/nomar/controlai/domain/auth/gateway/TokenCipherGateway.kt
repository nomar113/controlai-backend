package br.com.nomar.controlai.domain.auth.gateway

interface TokenCipherGateway {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String
}
