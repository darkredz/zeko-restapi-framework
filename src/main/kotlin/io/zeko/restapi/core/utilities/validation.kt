package io.zeko.restapi.core.utilities

import io.zeko.validation.Notification
import io.zeko.validation.ValidationEngineString
import io.zeko.validation.Validator


fun Map<String, String>?.validate(fieldName: String, rule: Map<String, List<Any>?>, note: Notification): ValidationEngineString {
    val field = Validator(rule, note).checkAll(this, fieldName)
    return field;
}

