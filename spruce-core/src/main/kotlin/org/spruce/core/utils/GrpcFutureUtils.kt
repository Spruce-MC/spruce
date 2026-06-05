package org.spruce.core.utils

import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.CompletableFuture

object GrpcFutureUtils {

    fun <T> toCompletableFuture(future: ListenableFuture<T>): CompletableFuture<T> {
        val completable = CompletableFuture<T>()

        Futures.addCallback(
            future,
            object : FutureCallback<T> {
                override fun onSuccess(result: T?) {
                    completable.complete(result)
                }

                override fun onFailure(t: Throwable) {
                    completable.completeExceptionally(t)
                }
            },
            MoreExecutors.directExecutor()
        )

        return completable
    }
}