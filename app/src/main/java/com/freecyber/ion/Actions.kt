package com.freecyber.ion

internal data class Actions(
    val scan: () -> Unit = {}, val connect: (WheelDevice) -> Unit = {}, val disconnect: () -> Unit = {},
    val stopScan: () -> Unit = {}, val forget: () -> Unit = {}, val readAll: () -> Unit = {},
    val read: (V12Protocol.Read) -> Unit = {}, val write: (V12Protocol.Write) -> Unit = {},
    val theme: () -> Unit = {}, val permissions: () -> Unit = {}
)

