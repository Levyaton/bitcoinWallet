package com.wallet.levyaton.levyaton_wallet.configuration

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.Security

@Configuration
class CryptoConfiguration {
    @Bean
    fun registerProvider(): Int =
        Security.addProvider(BouncyCastleProvider())

}