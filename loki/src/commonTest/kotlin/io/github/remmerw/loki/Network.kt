package io.github.remmerw.loki

import java.net.InetAddress

fun internet(): Boolean =
    try {
        InetAddress.getByName("8.8.8.8").isReachable(2000)
    } catch (e: Exception) {
        false
    }