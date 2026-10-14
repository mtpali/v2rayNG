package com.v2ray.ang.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.ToNumberPolicy
import com.google.gson.reflect.TypeToken
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import java.lang.reflect.Type

object JsonUtil {
    private val profileGson = Gson()
    private val gson = GsonBuilder()
        .registerTypeAdapter(ProfileItem::class.java, JsonDeserializer<ProfileItem> { json, _, context ->
            val source = json.asJsonObject
            val configType = context.deserialize<EConfigType>(source.get("configType"), EConfigType::class.java)
                ?: throw JsonParseException("Missing or unsupported profile type")
            // Gson's Unsafe allocation skips Kotlin defaults for absent legacy fields.
            // Seed only missing values; every field supplied by the stored profile wins.
            val defaults = profileGson.toJsonTree(ProfileItem.create(configType)).asJsonObject
            source.entrySet().forEach { (name, value) -> defaults.add(name, value) }
            profileGson.fromJson(defaults, ProfileItem::class.java)
        })
        .create()

    /**
     * Converts an object to its JSON representation.
     *
     * @param src The object to convert.
     * @return The JSON representation of the object.
     */
    fun toJson(src: Any?): String {
        return gson.toJson(src)
    }

    /**
     * Parses a JSON string into an object of the specified class.
     *
     * @param src The JSON string to parse.
     * @param cls The class of the object to parse into.
     * @return The parsed object.
     */
    fun <T> fromJson(src: String, cls: Class<T>): T? {
        return gson.fromJson(src, cls)
    }

    /**
     * Safely parses a JSON string into an object of the specified class.
     * Returns null if parsing fails instead of throwing an exception.
     *
     * @param src The JSON string to parse.
     * @param cls The class of the object to parse into.
     * @return The parsed object, or null if parsing fails.
     */
    fun <T> fromJsonSafe(src: String, cls: Class<T>): T? {
        return try {
            gson.fromJson(src, cls)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse JSON", e)
            null
        }
    }

    /**
     * Converts an object to its pretty-printed JSON representation.
     *
     * @param src The object to convert.
     * @return The pretty-printed JSON representation of the object, or null if the object is null.
     */
    fun toJsonPretty(src: Any?): String? {
        if (src == null)
            return null
        val gsonPre = GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .registerTypeAdapter( // custom serializer is needed here since JSON by default parse number as Double, core will fail to start
                object : TypeToken<Double>() {}.type,
                JsonSerializer { src: Double?, _: Type?, _: JsonSerializationContext? ->
                    when (src) {
                        null -> null
                        src.toLong().toDouble() -> JsonPrimitive(src.toLong())
                        else -> JsonPrimitive(src)
                    }
                }
            )
            .create()
        return gsonPre.toJson(src)
    }

    /**
     * Parses a JSON string into a JsonObject.
     *
     * @param src The JSON string to parse.
     * @return The parsed JsonObject, or null if parsing fails.
     */
    fun parseString(src: String?): JsonObject? {
        if (src == null)
            return null
        try {
            return JsonParser.parseString(src).getAsJsonObject()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse JSON string", e)
            return null
        }
    }

    fun parseHeadersToMap(headersJson: String?): Map<String, String> {
        val headerMap = mutableMapOf<String, String>()
        val jsonObject = parseString(headersJson) ?: return headerMap
        try {
            for ((key, jsonElement) in jsonObject.entrySet()) {
                if (key.isNullOrBlank() || jsonElement == null || jsonElement.isJsonNull) {
                    continue
                }
                val value = if (jsonElement.isJsonPrimitive) jsonElement.asString else jsonElement.toString()
                if (value.isNotBlank()) {
                    headerMap[key] = value
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse headers JSON", e)
        }
        return headerMap
    }
}
