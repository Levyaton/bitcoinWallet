package com.wallet.levyaton.levyaton_wallet.configuration

import org.bitcoinj.base.BitcoinNetwork
import org.bitcoinj.base.Network
import org.bitcoinj.core.NetworkParameters
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class NetworkParametersConfiguration(
    @Value("\${network}")
    private val network: String
) {
    @Bean
    fun initNetwork(): BitcoinNetwork = BitcoinNetwork.valueOf(network)
}