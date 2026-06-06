package org.spruce.core.service

import java.util.concurrent.CompletableFuture

fun interface ServiceRequestHandler {
    fun handle(payloadJson: String): CompletableFuture<String>
}