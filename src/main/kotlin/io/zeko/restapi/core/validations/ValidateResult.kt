package io.zeko.restapi.core.validations

data class ValidateResult(
    val success: Boolean,
    val type: Int,
    val errors: Map<String, List<String>>,
    val values: Map<String, Any?>
)
