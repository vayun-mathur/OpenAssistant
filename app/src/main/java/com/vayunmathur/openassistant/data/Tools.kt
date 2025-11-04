package com.vayunmathur.openassistant.data

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

typealias ToolFunctionType = (Map<String, JsonElement>) -> String

class Tools {
    companion object {
        @OptIn(ExperimentalTime::class)
        val ALL_TOOLS = listOf(
            ToolSimple("get_weather", "Get the current weather for a specific location", listOf(
                    stringParam("location", "the location to get the weather for")
                )
            ) { args ->
                "the weather is 65 deg F"
            },
            ToolSimple("get_local_current_date_time", "Get the current date and time in the local timezone", listOf()) {
                "${TimeZone.currentSystemDefault()}: ${Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())}"
            },
        )

        val API_TOOLS = ALL_TOOLS.map { it.toTool() }

        fun getToolAction(name: String): ToolFunctionType? {
            return ALL_TOOLS.find { it.name == name }?.action
        }
    }
}