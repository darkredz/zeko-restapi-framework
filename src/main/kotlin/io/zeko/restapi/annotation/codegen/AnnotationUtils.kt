package io.zeko.restapi.annotation.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asTypeName
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.ExecutableElement

class AnnotationUtils {
    companion object {
        fun elementValuesToMap(annotationMirrors: List<AnnotationMirror>, className: ClassName): Map<String, Any> {
            var valueMap = mapOf<String, Any>()
            for (anno in annotationMirrors) {
                if (anno.annotationType.asTypeName() == className) {
                    valueMap = elementValuesToMap(anno.elementValues)
                }
            }
            return valueMap
        }

        fun elementValuesToMap(elementValues: Map<out ExecutableElement?, AnnotationValue?>): Map<String, Any> {
            val valueMap = mutableMapOf<String, Any>()
            for (entry in elementValues.entries) {
                val key = entry.key!!.simpleName.toString()
                val value = entry.value!!.value
                valueMap[key] = value
            }
            return valueMap
        }
    }
}
