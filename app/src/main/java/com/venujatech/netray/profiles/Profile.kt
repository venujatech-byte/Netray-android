package com.venujatech.netray.profiles

import java.util.UUID
import org.json.JSONObject

data class Profile(val id: String, val name: String, val server: String, val port: Int, val transport: String, val security: String, val link: String) {
    companion object {
        fun parse(link: String): Profile {
            val parsed = JSONObject(NativeBackend.parseVless(link.trim()))
            require(!parsed.has("error")) { parsed.optString("error", "Invalid profile link") }
            return Profile(UUID.randomUUID().toString(), parsed.getString("name"), parsed.getString("server"),
                parsed.getInt("port"), parsed.getString("transport"), parsed.getString("security"), link.trim())
        }
    }
}
