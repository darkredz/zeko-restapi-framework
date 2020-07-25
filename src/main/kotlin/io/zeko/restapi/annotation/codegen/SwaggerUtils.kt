package io.zeko.restapi.annotation.codegen

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.util.regex.Pattern

class SwaggerUtils {
    companion object {
        @JvmStatic
        val vertxUriParamPattern: Pattern
                get() {
                    val paths = JsonObject()
                    val rgxStr = "\\:([a-zA-Z_0-9]+)"
                    val rgx = Regex(rgxStr)
                    val uriParamPattern = rgx.toPattern()
                    return uriParamPattern
                }

        @JvmStatic
        fun convertToSwaggerUri(path: String, pattern: Pattern) = path.replace(pattern.toRegex(), "\\{\$1\\}")

        @JvmStatic
        fun paramsFromVertxUri(path: String, uriParamPattern: Pattern): MutableList<String> {
            //get vertx uri param names :paramName to a list
            val matcher = uriParamPattern.matcher(path)
            val uriParam = mutableListOf<String>()

            while (matcher.find()) {
                uriParam.add(matcher.group(1))
            }
            return uriParam
        }

        @JvmStatic
        fun stripFileExt(str: String): String {
            val pos = str.lastIndexOf(".")
            // If there wasn't any '.' just return the string as is.
            return if (pos == -1) str else str.substring(0, pos)
        }

        @JvmStatic
        fun checkFieldType(rule: Map<String, List<Any>>): String {
            var fieldType = "string"
            if (rule.containsKey("isInteger") || rule.containsKey("isLong")) {
                fieldType = "integer"
            } else if (rule.containsKey("isDouble") || rule.containsKey("isFloat")) {
                fieldType = "number"
            } else if (rule.containsKey("isBoolean")) {
                fieldType = "boolean"
            }
            return fieldType
        }

        @JvmStatic
        fun addFieldFormat(fieldSchema: JsonObject, fieldType: String, rule: Map<String, List<Any>>) {
            if (fieldType == "integer") {
                fieldSchema.put("format", if (rule.containsKey("isLong")) "int64" else "int32")
            } else if (rule.containsKey("dateFormat")) {
                fieldSchema.put("format", "date")
            } else if (rule.containsKey("dateTimeFormat")) {
                fieldSchema.put("format", "date-time")
            } else if (rule.containsKey("timeFormat")) {
                fieldSchema.put("format", "time")
            } else if (rule.containsKey("time24Hour")) {
                fieldSchema.put("format", "time24Hour")
            } else if (rule.containsKey("email")) {
                fieldSchema.put("format", "email")
            } else if (rule.containsKey("ipv4")) {
                fieldSchema.put("format", "ipv4")
            } else if (rule.containsKey("ipv6")) {
                fieldSchema.put("format", "ipv6")
            } else if (rule.containsKey("hostName")) {
                fieldSchema.put("format", "hostname")
            } else if (rule.containsKey("subdomain")) {
                fieldSchema.put("format", "subdomain")
            } else if (rule.containsKey("url")) {
                fieldSchema.put("format", "url")
            } else if (rule.containsKey("digit")) {
                fieldSchema.put("format", "digit")
            } else if (rule.containsKey("letter")) {
                fieldSchema.put("format", "letter")
            } else if (rule.containsKey("alphaNum")) {
                fieldSchema.put("format", "alphaNum")
            } else if (rule.containsKey("alphaNumSpace")) {
                fieldSchema.put("format", "alphaNumSpace")
            } else if (rule.containsKey("alphaNumDash")) {
                fieldSchema.put("format", "alphaNumDash")
            } else if (rule.containsKey("alphaNumDashSpace")) {
                fieldSchema.put("format", "alphaNumDashSpace")
            } else if (rule.containsKey("alphaNumUnderscore")) {
                fieldSchema.put("format", "alphaNumUnderscore")
            }  else if (rule.containsKey("alphaNumQuoteDashSpace")) {
                fieldSchema.put("format", "alphaNumQuoteDashSpace")
            }  else if (rule.containsKey("alphaNumQuoteSpace")) {
                fieldSchema.put("format", "alphaNumQuoteSpace")
            }  else if (rule.containsKey("alphaQuoteDashSpace")) {
                fieldSchema.put("format", "alphaQuoteDashSpace")
            }  else if (rule.containsKey("alphaQuoteSpace")) {
                fieldSchema.put("format", "alphaQuoteSpace")
            } else if (rule.containsKey("allLowerCase")) {
                fieldSchema.put("format", "allLowerCase")
            } else if (rule.containsKey("allUpperCase")) {
                fieldSchema.put("format", "allUpperCase")
            } else if (rule.containsKey("passwordSimple")) {
                fieldSchema.put("format", "password")
            } else if (rule.containsKey("creditCard")) {
                fieldSchema.put("format", "creditCard")
            }

            if (rule.containsKey("inArray")) {
                fieldSchema.put("format", "inArray")
                fieldSchema.put("enum", JsonArray(rule.get("inArray")))
            } else if (rule.containsKey("notInArray")) {
                fieldSchema.put("format", "notInArray")
                fieldSchema.put("enum", JsonArray(rule.get("notInArray")))
            }

            if (rule.containsKey("regex")) {
                fieldSchema.put("pattern", rule["regex"]!![0])
            }
            if (rule.containsKey("min")) {
                fieldSchema.put("minimum", rule["min"]!![0])
            }
            if (rule.containsKey("max")) {
                fieldSchema.put("maximum", rule["max"]!![0])
            }
            if (rule.containsKey("length")) {
                fieldSchema.put("minLength", rule["length"]!![0])
                fieldSchema.put("maxLength", rule["length"]!![1])
            } else {
                if (rule.containsKey("minLength")) {
                    fieldSchema.put("minLength", rule["minLength"]!![0])
                }
                if (rule.containsKey("maxLength")) {
                    fieldSchema.put("maxLength", rule["maxLength"]!![0])
                }
            }
        }
    }
}
