package io.zeko.restapi.core.controllers

import io.vertx.core.Vertx
import io.zeko.validation.Notification
import io.zeko.validation.Validator
import io.vertx.core.json.JsonObject
import org.slf4j.Logger
import io.vertx.ext.web.RoutingContext
import java.lang.Double
import java.lang.Float
import java.lang.Long
import io.vertx.kotlin.core.json.*
import io.zeko.db.sql.utilities.toCamelCase
import io.zeko.restapi.core.utilities.endJson
import io.zeko.restapi.core.utilities.errorJson
import io.zeko.restapi.core.utilities.validate
import io.zeko.restapi.core.validations.ValidateResult
import io.zeko.restapi.core.validations.ValidationError

open abstract class ApiController(
    val vertx: Vertx,
    val logger: Logger,
    val context: RoutingContext
) : Controller {
    var params: Map<String, String>? = null
    var useCamelCaseResponse: Boolean = false

    init {
        val entries = context.request().params().entries()
        params = entries.associate { Pair(it.key, it.value) }
        if (context.get<Boolean>("useCamelCaseResponse")) {
            useCamelCaseResponse = true
        }
    }

    override fun inputRules(): Map<String, Map<String, String>> {
        return mapOf()
    }

    override fun inputErrorMessages() = ValidationError.defaultMessages

    protected open fun checkInputErrors(rules: Map<String, Any>): ValidateResult {
        return checkInputErrors(this.params, rules, this.inputErrorMessages())
    }

    protected open fun validateInput(statusCode: Int = 400): ValidateResult {
        val inputRules: Map<String, Any>? = context.get("inputRules")
        return validateInput(inputRules, statusCode, statusCode)
    }

    protected open fun validateInput(statusCode: Int = 400, errorCode: Int = 400): ValidateResult {
        val inputRules: Map<String, Any>? = context.get("inputRules")
        return validateInput(inputRules, statusCode, errorCode)
    }

    protected open fun validateInput(action: String, statusCode: Int = 400): ValidateResult {
        val rules = this.inputRules()
        return validateInput(rules[action]!!, statusCode)
    }

    protected open fun validateInput(
        rules: Map<String, Any>?,
        statusCode: Int = 400,
        errorCode: Int = 400
    ): ValidateResult {
        if (rules.isNullOrEmpty()) {
            return outputNoRulesError(statusCode, errorCode)
        }

        val validateResult = checkInputErrors(rules)
        if (!validateResult.success) {
            outputError(validateResult, statusCode, errorCode)
        }
        return validateResult
    }

    protected fun getJsonKey(key: String): String {
        if (useCamelCaseResponse)
            return key.toCamelCase()
        return key
    }

    protected open fun outputNoRulesError(statusCode: Int, errorCode: Int): ValidateResult {
        val values = HashMap<String, Any?>()
        val errors = mapOf(
            getJsonKey("server_error") to listOf("Undefined input rules for this endpoint")
        )
        val validateResult = ValidateResult(false, -1, errors, values)
        val res = json {
            obj(
                getJsonKey("error_code") to errorCode,
                "errors" to validateResult.errors
            )
        }
        context.response().setStatusCode(405).putHeader("Content-Type", "application/json").end(res.toString())
        return validateResult
    }

    override fun outputError(validateResult: ValidateResult, statusCode: Int, errorCode: Int) {
        val res = JsonObject().put("error_code", errorCode).put("errors", JsonObject(validateResult.errors))
        if (params!!["_debug"] == "true") {
            res.put("values", JsonObject(validateResult.values))
        }
        context.response().setStatusCode(statusCode).putHeader("Content-Type", "application/json").end(res.encode())
    }

    override fun end(value: Any?) {
        if (value != null) {
            context.response().end(value.toString())
        } else {
            context.response().end("null")
        }
    }

    override fun endJson(value: Any?, statusCode: Int) {
        context.endJson(value, statusCode)
    }

    override fun errorJson(vararg errors: Pair<String, List<String>>) {
        context.errorJson(*errors)
    }

    override fun errorJson(vararg errors: Pair<String, String>, code: Int) {
        val errMap = HashMap<String, List<String>>()
        errors.forEach {
            errMap.put(it.first, listOf(it.second))
        }
        context.errorJson(errMap, code, code)
    }

    override fun errorJson(errors: Map<String, List<String>>, code: Int) {
        context.errorJson(errors, code)
    }

    override fun errorJson(errors: Map<String, List<String>>, statusCode: Int, errorCode: Int) {
        context.errorJson(errors, statusCode, errorCode)
    }

    companion object {
        @JvmStatic
        fun checkInputErrors(
            params: Map<String, String>?,
            rules: Map<String, Any>,
            inputErrorMessages: Map<String, String>
        ): ValidateResult {
            val note = Notification("__", inputErrorMessages)
            val values = HashMap<String, Any?>()
            val errors = HashMap<String, List<String>>()

            for ((fieldName, ruleDetail) in rules) {
                var rule = mapOf<String, List<Any>>()

                when (ruleDetail) {
                    is String -> rule = Validator.parseRules(ruleDetail) as Map<String, List<Any>>
                    is Map<*, *> -> rule = ruleDetail as Map<String, List<Any>>
                }

                val checked = params.validate(fieldName, rule, note)
                val errorList = note.getMessages(fieldName);
                // logger.debug("$fieldName ${checked.rules} $errorList")

                if (errorList == null) {
                    val isOptional = !checked.rules.contains("required")
                    if (isOptional && !params!!.containsKey(fieldName)) {
                        //optional field is not set (null)
                    } else {
                        if (checked.rules.contains("isInteger")) {
                            values[fieldName] = Integer.parseInt(params!![fieldName])
                        } else if (checked.rules.contains("isLong")) {
                            values[fieldName] = Long.parseLong(params!![fieldName])
                        } else if (checked.rules.contains("isFloat")) {
                            values[fieldName] = Float.parseFloat(params!![fieldName])
                        } else if (checked.rules.contains("isDouble")) {
                            values[fieldName] = Double.parseDouble(params!![fieldName])
                        } else {
                            values[fieldName] = params!![fieldName]
                        }
                    }
                } else {
                    errors[fieldName] = errorList
                }
            }
            var type = if (errors.size > 0) -1 else 1
            if (errors.size == 0 && values.size == 0) {
                type = 0
            }
            var success = type == 1
            //if all params are optional, and nothing is pass in. should be success
            if (!success && errors.keys.isEmpty()) {
                success = true
            }
            return ValidateResult(success, type, errors, values)
        }
    }
}
