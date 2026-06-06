package org.spruce.service

import java.util.concurrent.CompletableFuture

fun interface ServiceEventHandler {
    fun handle(payloadJson: String): CompletableFuture<Void>
}