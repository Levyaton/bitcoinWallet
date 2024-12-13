package com.wallet.levyaton.levyaton_wallet.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.bitcoinj.base.BitcoinNetwork
import org.bitcoinj.base.ScriptType
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

@Service
class WalletService(val network: BitcoinNetwork) {

    fun register(): String {
        val wallet = Wallet.createDeterministic(network, ScriptType.P2PKH)
        return wallet.keyChainSeed.mnemonicString!!
    }

    fun login(seed: String): Long {
        val wallet = loadWallet(seed)
        return wallet.balance.value
    }

    fun generate(seed: String): Boolean {
        val wallet = loadWallet(seed)
        val address = wallet.currentReceiveAddress()

        startBitcoind()

        waitForBitcoind()

        val blocksGenerated = generateToAddress(101, address.toString())

        stopBitcoind()

        wallet.reset()
        return blocksGenerated != null
    }

    private fun loadWallet(seed: String) =
        Wallet.fromSeed(network, DeterministicSeed.ofMnemonic(seed, ""), ScriptType.P2PKH)

    private fun startBitcoind() {
        val processBuilder = ProcessBuilder(
            "bitcoind",
            "-regtest",
            "-daemon"
        )
        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            println(line)
        }
        process.waitFor()
    }

    private fun waitForBitcoind() {
        val maxRetries = 10
        val delayBetweenRetries = 2000L // milliseconds
        var attempts = 0
        while (attempts < maxRetries) {
            try {
                if (isBitcoindAvailable()) {
                    println("bitcoind is now available.")
                    return
                }
            } catch (e: Exception) {
                // bitcoind is not yet available
            }
            attempts++
            println("Waiting for bitcoind to start... (Attempt $attempts/$maxRetries)")
            Thread.sleep(delayBetweenRetries)
        }
        throw RuntimeException("bitcoind did not start within the expected time.")
    }

    private fun isBitcoindAvailable(): Boolean {
        val rpcUrl = "http://127.0.0.1:18443/"
        val rpcUser = "user"
        val rpcPassword = "pass"

        val jsonRpcRequest = """
            {
                "jsonrpc": "1.0",
                "id": "ping",
                "method": "ping",
                "params": []
            }
        """.trimIndent()

        val url = URL(rpcUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 2000
        connection.readTimeout = 2000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.requestMethod = "POST"

        // Set Basic Auth header
        val authString = "$rpcUser:$rpcPassword"
        val authEncBytes = authString.toByteArray(Charsets.UTF_8)
        val authStringEnc = "Basic " + java.util.Base64.getEncoder().encodeToString(authEncBytes)
        connection.setRequestProperty("Authorization", authStringEnc)

        connection.outputStream.use { outputStream ->
            outputStream.write(jsonRpcRequest.toByteArray())
            outputStream.flush()
        }

        return connection.responseCode == 200
    }

    private fun stopBitcoind() {
        val processBuilder = ProcessBuilder(
            "bitcoin-cli",
            "-regtest",
            "stop"
        )
        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            println(line)
        }
        process.waitFor()
    }

    private fun generateToAddress(blocks: Int, address: String): List<String>? {
        val rpcUrl = "http://127.0.0.1:18443/"
        val rpcUser = "user"
        val rpcPassword = "pass"

        val jsonRpcRequest = """
            {
                "jsonrpc": "1.0",
                "id": "curltest",
                "method": "generatetoaddress",
                "params": [$blocks, "$address"]
            }
        """.trimIndent()

        val url = URL(rpcUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.requestMethod = "POST"

        // Set Basic Auth header
        val authString = "$rpcUser:$rpcPassword"
        val authEncBytes = authString.toByteArray(Charsets.UTF_8)
        val authStringEnc = "Basic " + java.util.Base64.getEncoder().encodeToString(authEncBytes)
        connection.setRequestProperty("Authorization", authStringEnc)

        connection.outputStream.use { outputStream ->
            outputStream.write(jsonRpcRequest.toByteArray())
            outputStream.flush()
        }

        return if (connection.responseCode == 200) {
            val response = InputStreamReader(connection.inputStream).use { it.readText() }
            val objectMapper = ObjectMapper()
            val jsonResponse: JsonNode = objectMapper.readTree(response)
            val resultNode = jsonResponse.get("result")

            if (resultNode.isArray) {
                resultNode.map { it.asText() }
            } else {
                null
            }
        } else {
            null
        }
    }
}
