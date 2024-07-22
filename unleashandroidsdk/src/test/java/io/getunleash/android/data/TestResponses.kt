package io.getunleash.android.data

import com.fasterxml.jackson.module.kotlin.readValue

object TestResponses {
    val threeToggles = """
        {
            	"toggles": [
                    {
                        "name": "variantToggle",
                        "enabled": true,
                        "variant": {
                            "name": "green",
                            "payload": {
                                "type": "string",
                                "value": "some-text"
                            }
                        },
                        "impressionData": true
                    }, {
                        "name": "featureToggle",
                        "enabled": true,
                        "variant": {
                            "name": "disabled"
                        }
                    }, {
                        "name": "simpleToggle",
                        "enabled": true
                    }
                ]
            }
    """.trimIndent()

    val complicatedVariants = """{
            	"toggles": [
                    {
                        "name": "variantToggle",
                        "enabled": true,
                        "variant": {
                            "name": "green",
                            "payload": {
                                "type": "number",
                                "value": 54
                            }
                        }
                    }, {
                        "name": "featureToggle",
                        "enabled": true,
                        "variant": {
                            "name": "disabled"
                        }
                    }, {
                        "name": "simpleToggle",
                        "enabled": true,
                        "variant": {
                            "name": "red",
                            "payload": {
                                "type": "json",
                                "value": { "key": "value" }
                            }
                        }
                    },
                    {
                        "name": "booleanVariant",
                        "enabled": true,
                        "variant": {
                            "name": "boolthis",
                            "payload": {
                                "type": "boolean",
                                "value": true
                            }
                        }
                    },
                    {
                        "name": "doubleVariant",
                        "enabled": true,
                        "variant": {
                            "name": "the-answer",
                            "payload": {
                                "type": "number",
                                "value": 42.0
                            }
                        }
                    }
                ]
            }""".trimIndent()

    fun String.toToggleMap(): Map<String, Toggle> {
        val response: ProxyResponse = Parser.jackson.readValue(this)
        return response.toggles.groupBy { it.name }.mapValues { (_, v) -> v.first() }
    }
}