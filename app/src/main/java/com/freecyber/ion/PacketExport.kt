package com.freecyber.ion

/** Raw frame snapshot for offline diffing (e.g. finding the lock-state byte via before/after exports). */
internal fun exportRecords(context: android.content.Context,records: Map<V12Protocol.Read,PacketRecord>): java.io.File? {
    if(records.isEmpty()) return null
    val dir=context.getExternalFilesDir(null) ?: return null
    val stamp=java.text.SimpleDateFormat("yyyyMMdd-HHmmss",java.util.Locale.US).format(java.util.Date())
    val file=java.io.File(dir,"frames-$stamp.json")
    val sb=StringBuilder()
    sb.append("{\"exported\":").append(System.currentTimeMillis()).append(",\"records\":[")
    var first=true
    for((read,rec) in records) {
        if(!first) sb.append(','); first=false
        sb.append("{\"read\":\"").append(read.name).append("\",\"status\":\"").append(rec.status.replace("\"","'"))
            .append("\",\"at\":").append(rec.at ?: -1)
        val f=rec.frame
        if(f==null) { sb.append(",\"frame\":null}"); continue }
        sb.append(",\"frame\":{\"flags\":\"0x").append("%02X".format(f.flags))
            .append("\",\"commandByte\":\"0x").append("%02X".format(f.commandByte))
            .append("\",\"payloadHex\":\"").append(V12Protocol.hex(f.payload).replace(" ","")).append("\"}}")
    }
    sb.append("]}")
    file.writeText(sb.toString())
    return file
}

