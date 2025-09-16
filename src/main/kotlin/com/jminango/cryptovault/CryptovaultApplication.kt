package com.jminango.cryptovault

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CryptovaultApplication

fun main(args: Array<String>) {
	runApplication<CryptovaultApplication>(*args)
}
