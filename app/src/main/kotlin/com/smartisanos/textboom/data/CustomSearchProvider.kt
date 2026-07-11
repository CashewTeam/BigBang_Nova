package com.cashewteam.novatext.android.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class CustomSearchKind(val value: String) {
    WEB("web"),
    DICT("dict"),
    WIKI("wiki"),
    ;

    companion object {
        fun fromValue(value: String): CustomSearchKind? = values().firstOrNull { it.value == value }
    }
}

data class CustomSearchProvider(
    val id: Int,
    val name: String,
    val urlTemplate: String,
    val kind: CustomSearchKind,
    val iconFileName: String,
)

object CustomSearchProviderStore {
    private const val ICON_DIRECTORY = "custom_search_icons"

    fun load(settings: BigBangSettings): List<CustomSearchProvider> {
        val array = runCatching { JSONArray(settings.customSearchProvidersJson) }.getOrNull()
            ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val kind = CustomSearchKind.fromValue(item.optString("kind")) ?: continue
                val iconFileName = item.optString("iconFileName")
                if (item.optInt("id", 0) <= 0 ||
                    item.optString("name").isBlank() ||
                    item.optString("urlTemplate").isBlank() ||
                    iconFileName.isBlank() ||
                    iconFileName.contains('/') ||
                    iconFileName.contains('\\')
                ) {
                    continue
                }
                add(
                    CustomSearchProvider(
                        id = item.optInt("id"),
                        name = item.optString("name"),
                        urlTemplate = item.optString("urlTemplate"),
                        kind = kind,
                        iconFileName = iconFileName,
                    ),
                )
            }
        }
    }

    fun save(settings: BigBangSettings, providers: List<CustomSearchProvider>) {
        val array = JSONArray()
        providers.forEach { provider ->
            array.put(
                JSONObject()
                    .put("id", provider.id)
                    .put("name", provider.name)
                    .put("urlTemplate", provider.urlTemplate)
                    .put("kind", provider.kind.value)
                    .put("iconFileName", provider.iconFileName),
            )
        }
        settings.customSearchProvidersJson = array.toString()
    }

    fun iconFile(context: Context, fileName: String): File {
        return File(File(context.filesDir, ICON_DIRECTORY), fileName)
    }
}
