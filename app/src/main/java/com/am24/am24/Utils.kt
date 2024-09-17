package com.am24.am24

import java.text.SimpleDateFormat
import java.util.*

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("Asia/Kolkata") // Set to IST
    return sdf.format(Date(timestamp))
}
