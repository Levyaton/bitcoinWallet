package com.wallet.levyaton.levyaton_wallet.controller

import com.wallet.levyaton.levyaton_wallet.service.WalletService
import org.bitcoinj.wallet.Wallet
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*

@RestController()
class WalletController(val walletService: WalletService) {
    @GetMapping("/user")
    fun getBalance(@RequestParam seed: String): ResponseEntity<String>{
        return ResponseEntity.ok(walletService.login(seed).toString())
    }

    @PostMapping("/user")
    fun register(): ResponseEntity<String> {
        return ResponseEntity.ok(walletService.register())
    }

    @PostMapping("/buy")
    fun generateIncome(@RequestParam seed: String){
        walletService.generate(seed)
    }
}