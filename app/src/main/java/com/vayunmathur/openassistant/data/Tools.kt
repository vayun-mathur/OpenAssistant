package com.vayunmathur.openassistant.data

import android.content.Context
import android.icu.util.LocaleData
import android.icu.util.ULocale
import androidx.core.text.util.LocalePreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

typealias ToolFunctionType = suspend (Map<String, JsonElement>, Context) -> String

class Tools {
    companion object {
        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
        }
        @OptIn(ExperimentalTime::class)
        val ALL_TOOLS = listOf(
            ToolSimple("get_weather", "Get the current weather (full data) for a specific location", listOf(
                    numberParam("latitude", "the latitude of the location"),
                    numberParam("longitude", "the longitude of the location"),
                )
            ) { args, context ->
                println(args)
                val latitude = args["latitude"]
                val longitude = args["longitude"]


                val locale = context.resources.configuration.locales[0]
                val isMetric = locale.country.let { it == "US" || it == "LR" || it == "MM" }.not()


                val temperatureUnit = when(LocalePreferences.getTemperatureUnit()) {
                    LocalePreferences.TemperatureUnit.CELSIUS -> "celsius"
                    LocalePreferences.TemperatureUnit.FAHRENHEIT -> "fahrenheit"
                    else -> if(isMetric) "celsius" else "fahrenheit"
                }

                if (latitude == null || longitude == null) {
                    "{\"error\": \"latitude and longitude parameters are required\"}"
                } else {
                    try {
                        client.get("https://api.open-meteo.com/v1/forecast?current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,rain,showers,snowfall,weather_code,cloud_cover,pressure_msl,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m") {
                            parameter("latitude", latitude.jsonPrimitive.double)
                            parameter("longitude", longitude.jsonPrimitive.double)
                            parameter("temperature_unit", temperatureUnit)
                            if(isMetric) {
                                parameter("wind_speed_unit", "kmh")
                                parameter("precipitation_unit", "mm")
                            } else {
                                parameter("wind_speed_unit", "mph")
                                parameter("precipitation_unit", "inch")
                            }
                        }.bodyAsText() + ": The user would like their responses using the units requested and returned by the response. Other units may be used in parenthesis."
                    } catch (e: Exception) {
                        "{\"error\": \"Unable to fetch weather data: ${e.message}\"}"
                    }
                }
            },
            ToolSimple("get_local_current_date_time", "Get the current date and time in the local timezone", listOf()) { _, _ ->
                "${TimeZone.currentSystemDefault()}: ${Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())}"
            },
        )

        val API_TOOLS = ALL_TOOLS.map { it.toTool() }

        fun getToolAction(name: String): ToolFunctionType? {
            return ALL_TOOLS.find { it.name == name }?.action
        }
    }
}
