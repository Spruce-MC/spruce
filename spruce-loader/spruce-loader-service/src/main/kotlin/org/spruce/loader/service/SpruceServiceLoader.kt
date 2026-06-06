package org.spruce.loader.service

import java.util.logging.Logger

object SpruceServiceLoader {

    @JvmStatic
    fun run(serviceClass: Class<*>) {
        SpruceServiceBootstrap(
            Logger.getLogger(serviceClass.simpleName)
        ).enable(serviceClass)
    }
}