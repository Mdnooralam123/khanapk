package com.khanproxy

import org.json.JSONObject

data class KeyResponse(
    val key: String,
    val historyEndpoint: String,
    val proxyPath: String,
    val region: String,
    val mode: String,
    val status: String
) {
    companion object {
        fun fromJson(o: JSONObject): KeyResponse = KeyResponse(
            key = o.optString("key", ""),
            historyEndpoint = o.optString("history_endpoint", ""),
            proxyPath = o.optString("proxy_path", ""),
            region = o.optString("region", "IND"),
            mode = o.optString("mode", "jwt"),
            status = o.optString("status", "")
        )
    }
}

data class TokenItem(
    val name: String,
    val jwt: String,
    val region: String,
    val uid: String,
    val receivedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromJson(o: JSONObject): TokenItem {
            return TokenItem(
                name = firstOf(o, "name", "nickname", "player", "player_name", "username", "account").ifEmpty { "Unknown" },
                jwt = firstOf(o, "jwt", "token", "access_token", "id_token"),
                region = firstOf(o, "region", "server", "server_name").ifEmpty { "—" },
                uid = firstOf(o, "uid", "id", "account_id", "user_id")
            )
        }
        private fun firstOf(o: JSONObject, vararg keys: String): String {
            for (k in keys) {
                if (o.has(k) && !o.isNull(k)) {
                    val v = o.optString(k, "")
                    if (v.isNotEmpty()) return v
                }
            }
            return ""
        }
    }
}

data class HistoryResponse(
    val key: String,
    val region: String,
    val mode: String,
    val status: String,
    val totalTokens: Int,
    val tokens: List<TokenItem>
) {
    companion object {
        fun fromJson(o: JSONObject): HistoryResponse {
            val arr = o.optJSONArray("tokens")
            val list = mutableListOf<TokenItem>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val t = arr.optJSONObject(i) ?: continue
                    list.add(TokenItem.fromJson(t))
                }
            }
            return HistoryResponse(
                key = o.optString("key", ""),
                region = o.optString("region", "IND"),
                mode = o.optString("mode", "jwt"),
                status = o.optString("status", ""),
                totalTokens = o.optInt("total_tokens", list.size),
                tokens = list
            )
        }
    }
}
