package io.zeko.restapi.annotation.codegen

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.zeko.restapi.annotation.Params
import io.zeko.restapi.annotation.http.Routing
import io.zeko.validation.Validator
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.Logger
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.jsonArrayOf
import io.vertx.kotlin.core.json.jsonObjectOf
import io.vertx.kotlin.core.json.obj
import io.zeko.restapi.annotation.cron.Cron
import io.zeko.restapi.annotation.cron.CronSuspend
import io.zeko.restapi.annotation.http.*
import io.zeko.restapi.core.RouteSchema
import io.zeko.restapi.core.cron.CronRunner
import io.zeko.restapi.core.cron.CronSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import kotlin.coroutines.CoroutineContext

@AutoService(Processor::class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions(
    RouteSchemaGenerator.KAPT_KOTLIN_GENERATED_OPTION_NAME,
    RouteSchemaGenerator.SWAGGER_API_VERSION,
    RouteSchemaGenerator.SWAGGER_TITLE,
    RouteSchemaGenerator.SWAGGER_DESCRIPTION,
    RouteSchemaGenerator.SWAGGER_HOST,
    RouteSchemaGenerator.SWAGGER_BASEPATH,
    RouteSchemaGenerator.SWAGGER_SAMPLE_RESULT_DIR,
    RouteSchemaGenerator.SWAGGER_OUTPUT_FILE,
    RouteSchemaGenerator.SWAGGER_CMP_SCHEMA_DIR,
    RouteSchemaGenerator.DEFAULT_PRODUCES,
    RouteSchemaGenerator.DEFAULT_CONSUMES
)
class RouteSchemaGenerator : AbstractProcessor() {
    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
        const val SWAGGER_API_VERSION = "swagger.apiVersion"
        const val SWAGGER_TITLE = "swagger.title"
        const val SWAGGER_DESCRIPTION = "swagger.description"
        const val SWAGGER_HOST = "swagger.host"
        const val SWAGGER_BASEPATH = "swagger.basePath"
        const val SWAGGER_SAMPLE_RESULT_DIR = "swagger.sampleResultDir"
        const val SWAGGER_OUTPUT_FILE = "swagger.outputFile"
        const val SWAGGER_CMP_SCHEMA_DIR = "swagger.cmpSchemaDir"
        const val DEFAULT_PRODUCES = "default.produces"
        const val DEFAULT_CONSUMES = "default.consumes"
    }

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        return mutableSetOf(
            Params::class.java.canonicalName,
            Routing::class.java.canonicalName,
            Get::class.java.canonicalName,
            Post::class.java.canonicalName,
            Delete::class.java.canonicalName,
            Put::class.java.canonicalName,
            Head::class.java.canonicalName,
            Patch::class.java.canonicalName,
            Options::class.java.canonicalName,
            GetSuspend::class.java.canonicalName,
            PostSuspend::class.java.canonicalName,
            DeleteSuspend::class.java.canonicalName,
            PutSuspend::class.java.canonicalName,
            HeadSuspend::class.java.canonicalName,
            PatchSuspend::class.java.canonicalName,
            OptionsSuspend::class.java.canonicalName,
            Cron::class.java.canonicalName,
            CronSuspend::class.java.canonicalName
        )
    }

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        val definitions = mutableListOf<RouteDefinition>()
        val controllerProps = HashMap<String, MutableList<PropertySpec>>()
        val methodsWithRules = HashMap<String, MutableList<String>>()
        val controllerRuleSchema = HashMap<String, HashMap<String, Map<String, List<Any>>>>()

        //To generate controller field rules schema
        processParamAnnotations(roundEnv, controllerProps, controllerRuleSchema, methodsWithRules)
        if (controllerProps.size > 0) {
            generateControllerSchemas(controllerProps)
        }

        //To generate routes
        val rootPathForController = HashMap<String, String>()
        val resourceDescribe = HashMap<String, RouteDefinition>()

        processRoutings(roundEnv, definitions, rootPathForController, resourceDescribe, methodsWithRules)
        if (definitions.size > 0) {
            generateRouteClasses(definitions)
        }

        //To generate swagger spec file
        if (processingEnv.options.containsKey(SWAGGER_OUTPUT_FILE) && definitions.size > 0) {
            generateSwaggerDoc(resourceDescribe, controllerRuleSchema)
        }

        val cronDef = mutableListOf<CronDefinition>()
        processCronAnnotations(roundEnv, cronDef)
        if (cronDef.size > 0) {
            generateCronClasses(cronDef)
        }

        return true
    }

    private fun processCronAnnotations(roundEnv: RoundEnvironment, definitions: MutableList<CronDefinition>) {
        listOf(
            Cron::class, CronSuspend::class
        ).forEach { cronClass ->
            roundEnv.getElementsAnnotatedWith(cronClass.java)
                .forEach {
                    val methodName = it.simpleName.toString()
                    val controllerName = it.enclosingElement.asType().toString()
                    val annoArgs = AnnotationUtils.elementValuesToMap(it.annotationMirrors, cronClass.asTypeName())

                    val schedule = if (annoArgs.containsKey("schedule")) annoArgs["schedule"].toString() else ""
                    val coroutine = cronClass.simpleName?.endsWith("Suspend") == true
                    val pack = processingEnv.elementUtils.getPackageOf(it).toString()

                    val cronDef = CronDefinition(controllerName, methodName, schedule, coroutine, pack)
                    definitions.add(cronDef)
                }
        }
    }

    private fun generateCronClasses(definitions: List<CronDefinition>) {
        val fileName = "GeneratedCrons"
        var codeJobs = ""
        val qt = "\"" + "\"" + "\""

        for (def in definitions) {
            val (className, methodName, schedule, coroutine, pack) = def
            lateinit var job: String
            if (coroutine) {
                job = """
                runner.runSuspend("$schedule") {
                    logger.debug(${qt}Running coroutine cron $className::$methodName${qt})
                    $className(vertx, logger).$methodName() 
                }
            """.trimIndent()
            } else {
                job = """
                runner.run("$schedule") {
                    logger.debug(${qt}Running cron $className::$methodName${qt})
                    $className(vertx, logger).$methodName() 
                }
            """.trimIndent()
            }
            codeJobs += job + "\n"
        }

        //Create handleJobs method
        val pack = definitions.first().pack
        val file = FileSpec.builder(pack, fileName)
            .addType(
                TypeSpec.classBuilder(fileName)
                    .superclass(CronSchema::class)
                    .addSuperclassConstructorParameter(
                        "%N",
                        ParameterSpec.builder("vertx", Vertx::class)
                            .build()
                    )
                    .addSuperclassConstructorParameter(
                        "%N",
                        ParameterSpec.builder("logger", Logger::class)
                            .build()
                    )
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameter("vertx", Vertx::class)
                            .addParameter("logger", Logger::class)
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("handleJobs")
                            .addModifiers(KModifier.OVERRIDE)
                            .addModifiers(KModifier.SUSPEND)
                            .addParameter("runner", CronRunner::class)
                            .addStatement(codeJobs)
                            .build()
                    )
                    .build()
            )
            .addImport(CronRunner::class.asClassName().packageName, CronRunner::class.asClassName().simpleName)
            .build()

        val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]
        logInfo("Writing cron class $kaptKotlinGeneratedDir/$fileName.kt")

        file.writeTo(File(kaptKotlinGeneratedDir, "$fileName.kt"))
    }

    private fun processParamAnnotations(
        roundEnv: RoundEnvironment,
        controllerProps: HashMap<String, MutableList<PropertySpec>>,
        controllerRuleSchema: HashMap<String, HashMap<String, Map<String, List<Any>>>>,
        methodsWithRules: HashMap<String, MutableList<String>>
    ) {
        roundEnv.getElementsAnnotatedWith(Params::class.java)
            .forEach {
                val methodName = it.simpleName.toString()
                val rules = mutableMapOf<String, String>()
                val controllerName = it.enclosingElement.asType().toString()
                val annoArgs = AnnotationUtils.elementValuesToMap(it.annotationMirrors, Params::class.asTypeName())

                if (annoArgs.containsKey("rules")) {
                    val rulesTypeMirror = annoArgs["rules"] as List<AnnotationValue?>

                    for (rt in rulesTypeMirror) {
                        val r = rt!!.value as String
                        val rParts = r.split(" => ")
                        val fieldName = rParts[0]
                        val ruleStr = rParts[1]
                        rules[fieldName] = ruleStr
                    }
                }

                if (rules.isNotEmpty()) {
                    val ruleSchema = HashMap<String, Map<String, List<Any>>>()
                    if (!controllerProps.containsKey(controllerName)) {
                        val properties = mutableListOf<PropertySpec>()
                        properties.add(rulesToProperty(methodName, rules, ruleSchema))
                        controllerProps[controllerName] = properties
                    } else {
                        val properties: MutableList<PropertySpec>? = controllerProps[controllerName]
                        properties!!.add(rulesToProperty(methodName, rules, ruleSchema))
                    }

                    if (!methodsWithRules.containsKey(controllerName)) {
                        methodsWithRules[controllerName] = mutableListOf(methodName)
                    } else {
                        methodsWithRules[controllerName]!!.add(methodName)
                    }
                    controllerRuleSchema["$controllerName.$methodName"] = ruleSchema
                }
            }
    }

    private fun generateControllerSchemas(controllerProps: HashMap<String, MutableList<PropertySpec>>) {
        for ((controller, props) in controllerProps) {
            val parts = controller.split(".")
            val fileName = parts[parts.size - 1] + "Schema"
            val companion = TypeSpec.companionObjectBuilder().addProperties(props).build()

            val pack = parts.take(parts.size - 1).joinToString(".")
            val file = FileSpec.builder(pack, fileName)
                .addType(
                    TypeSpec.classBuilder(fileName)
                        .addType(companion)
                        .build()
                )
                .addImport(
                    RoutingContext::class.asClassName().packageName,
                    RoutingContext::class.asClassName().simpleName
                )
                .addImport(Route::class.asClassName().packageName, Route::class.asClassName().simpleName)
                .build()

            val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]
            logInfo("Writing controller schema $kaptKotlinGeneratedDir/$fileName.kt")
            file.writeTo(File(kaptKotlinGeneratedDir, "$fileName.kt"))
        }
    }

    private fun processRoutings(
        roundEnv: RoundEnvironment,
        definitions: MutableList<RouteDefinition>,
        rootPathForController: HashMap<String, String>,
        resourceDescribe: HashMap<String, RouteDefinition>,
        methodsWithRules: HashMap<String, MutableList<String>>
    ) {
        roundEnv.getElementsAnnotatedWith(Routing::class.java)
            .forEach {
                if (it.kind.isClass) {
                    val annoArgs = AnnotationUtils.elementValuesToMap(it.annotationMirrors, Routing::class.asTypeName())
                    val route = RoutingAnnotation(annoArgs)
                    val cls = it.asType().toString()
                    rootPathForController[cls] = route.path + ""
                } else {
                    parseMethodRoute(
                        it,
                        Routing::class.asTypeName(),
                        definitions,
                        rootPathForController,
                        resourceDescribe,
                        methodsWithRules
                    )
                }
            }

        listOf(
            Get::class, Post::class, Put::class, Head::class,
            Delete::class, Patch::class, Options::class,
            GetSuspend::class, PostSuspend::class, PutSuspend::class, HeadSuspend::class,
            DeleteSuspend::class, PatchSuspend::class, OptionsSuspend::class
        ).forEach { routeClass ->
            roundEnv.getElementsAnnotatedWith(routeClass.java).forEach {
                parseMethodRoute(
                    it,
                    routeClass.asTypeName(),
                    definitions,
                    rootPathForController,
                    resourceDescribe,
                    methodsWithRules
                )
            }
        }
    }

    private fun parseMethodRoute(
        element: Element,
        routeClass: ClassName,
        definitions: MutableList<RouteDefinition>,
        rootPathForController: HashMap<String, String>,
        resourceDescribe: HashMap<String, RouteDefinition>,
        methodsWithRules: HashMap<String, MutableList<String>>
    ) {
        var methodName = element.simpleName.toString()
        var hasRules = false
        val controllerName = element.enclosingElement.asType().toString()

        var rootPath: String? = null
        if (rootPathForController.containsKey(controllerName)) {
            rootPath = rootPathForController[controllerName]
        }

        if (methodsWithRules.containsKey(controllerName) && methodsWithRules[controllerName]!!.contains(methodName)) {
            hasRules = true
        }

        val annoArgs = AnnotationUtils.elementValuesToMap(element.annotationMirrors, routeClass)
        val route = RoutingAnnotation(annoArgs)

        var coroutine = if (route.coroutine != null) route.coroutine!! else false
        if (route.coroutine != true && routeClass.simpleName.endsWith("Suspend")) {
            coroutine = true
        }
        var httpMethod = "route"
        if (route.method != null) {
            httpMethod = route.method!!.toLowerCase()
        } else if (routeClass.simpleName != "Routing") {
            httpMethod = routeClass.simpleName.removeSuffix("Suspend").toLowerCase()
        }

        var fullPath = "" + route.path
        if (rootPath != null && rootPath != "/") {
            fullPath = rootPath + route.path
        }

        val defaultProduces =
            if (processingEnv.options.containsKey(DEFAULT_PRODUCES)) processingEnv.options[DEFAULT_PRODUCES] + "" else "application/json"
        val defaultConsumes =
            if (processingEnv.options.containsKey(DEFAULT_CONSUMES)) processingEnv.options[DEFAULT_CONSUMES] + "" else "application/x-www-form-urlencoded"
        val describe = if (route.describe != null) route.describe!!.trimIndent() else ""
        val schemaRef = if (route.schemaRef != null) route.schemaRef!!.trimIndent() else ""
        val produces = if (route.produces != null) route.produces!!.trimIndent() else defaultProduces
        val consumes = if (route.consumes != null) route.consumes!!.trimIndent() else defaultConsumes

        val pack = processingEnv.elementUtils.getPackageOf(element).toString()
        val routeDef = RouteDefinition(
            controllerName,
            methodName,
            fullPath,
            httpMethod,
            coroutine,
            hasRules,
            pack,
            describe,
            produces,
            consumes,
            schemaRef
        )
        definitions.add(routeDef)
        resourceDescribe[fullPath] = routeDef
    }

    private fun generateSwaggerDoc(
        resourceDescribe: HashMap<String, RouteDefinition>,
        controllerRuleSchema: HashMap<String, HashMap<String, Map<String, List<Any>>>>
    ) {
        val apiVersion =
            if (processingEnv.options.containsKey(SWAGGER_API_VERSION)) processingEnv.options[SWAGGER_API_VERSION] else "1.0"
        val title =
            if (processingEnv.options.containsKey(SWAGGER_TITLE)) processingEnv.options[SWAGGER_TITLE] else "REST API"
        val description =
            if (processingEnv.options.containsKey(SWAGGER_DESCRIPTION)) processingEnv.options[SWAGGER_DESCRIPTION] else ""
        val host =
            if (processingEnv.options.containsKey(SWAGGER_HOST)) processingEnv.options[SWAGGER_HOST] else "localhost"
        val basePath =
            if (processingEnv.options.containsKey(SWAGGER_BASEPATH)) processingEnv.options[SWAGGER_BASEPATH] else "/"
        val swaggerFilePath = processingEnv.options[SWAGGER_OUTPUT_FILE]

        val cmpSchemas = jsonObjectOf()

        if (processingEnv.options.containsKey(SWAGGER_CMP_SCHEMA_DIR)) {
            val schemaFolder = File(processingEnv.options[SWAGGER_CMP_SCHEMA_DIR])
            if (schemaFolder.exists() && schemaFolder.isDirectory) {
                for (file in schemaFolder.listFiles()) {
                    if (file.isFile) {
                        val contentBuilder = StringBuilder()
                        try {
                            val stream = Files.lines(Paths.get(file.toURI()), StandardCharsets.UTF_8)
                            stream.forEach { s -> contentBuilder.append(s).append("\n") }
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                        val cmpSchemaStr = contentBuilder.toString()
                        cmpSchemas.put(SwaggerUtils.stripFileExt(file.name), JsonObject(cmpSchemaStr))
                    }
                }
            }
        }

        val schema = json {
            obj(
                "swagger" to "2.0",
                "info" to obj(
                    "version" to apiVersion,
                    "title" to title,
                    "description" to description
                ),
                "host" to host,
                "basePath" to basePath,
                "schemes" to jsonArrayOf("https", "http"),
                "definitions" to cmpSchemas
            )
        }

        val paths = JsonObject()
        val pattern = SwaggerUtils.vertxUriParamPattern

        for ((path, def) in resourceDescribe) {
            val params = JsonArray()
            //convert vertx :paramName to swagger {paramName}
            val convertedPath = SwaggerUtils.convertToSwaggerUri(path, pattern)
            val uriParam = SwaggerUtils.paramsFromVertxUri(path, pattern)

            if (controllerRuleSchema.containsKey("${def.className}.${def.methodName}")) {
                val props = controllerRuleSchema["${def.className}.${def.methodName}"]!!
                for ((field, rule) in props) {
                    val fieldType = SwaggerUtils.checkFieldType(rule)
                    var required = rule.containsKey("required")
                    var inType = "formData"

                    //if this field is in the URL, change to in path
                    if (!convertedPath.equals(path) && uriParam.contains(field)) {
                        inType = "path"
                        required = true
                    }

                    val fieldSchema = json {
                        obj(
                            "name" to field,
                            "in" to inType,
                            "required" to required,
                            "type" to fieldType
                        )
                    }
                    SwaggerUtils.addFieldFormat(fieldSchema, fieldType, rule)
                    params.add(fieldSchema)
                }
            }

            val method = if (def.httpMethod.isNullOrBlank() || def.httpMethod == "route") "get" else def.httpMethod
            var examples = json {
                obj(
                    def.produces to obj()
                )
            }
            var responses = json {
                obj(
                    "200" to obj(
                        "description" to "OK",
                        "schema" to obj(
                            "type" to "object"
                        ),
                        "examples" to examples
                    )
                )
            }

            if (processingEnv.options.containsKey(SWAGGER_SAMPLE_RESULT_DIR)) {
                val rootFolder = File(processingEnv.options[SWAGGER_SAMPLE_RESULT_DIR])

                if (rootFolder.exists() && rootFolder.isDirectory) {
                    val responseSampleFile = File("${rootFolder.absolutePath}/$path.json")

                    if (responseSampleFile.exists() && responseSampleFile.isFile) {
                        val contentBuilder = StringBuilder()
                        try {
                            val stream = Files.lines(Paths.get(responseSampleFile.toURI()), StandardCharsets.UTF_8)
                            stream.forEach { s -> contentBuilder.append(s).append("\n") }
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                        val exampleSample = contentBuilder.toString()
                        val json = JsonObject(exampleSample)
                        var isWithHttpCode = true

                        for (k in json.map.keys) {
                            if (!k.toString().matches(Regex("^[0-9]+$"))) {
                                isWithHttpCode = false
                                break
                            } else {
                                if (!json.getJsonObject(k).containsKey("schema")) {
                                    json.getJsonObject(k).put("schema", JsonObject().put("type", "object"))
                                }
                            }
                        }
                        if (isWithHttpCode) {
                            responses = json
                        } else {
                            examples.put(def.produces, json)
                        }
                    }
                }
            }

            if (!def.schemaRef.isNullOrEmpty() && cmpSchemas.containsKey(def.schemaRef)) {
                val fieldSchema = json {
                    obj(
                        "name" to "body",
                        "in" to "body",
                        "required" to true,
                        "schema" to obj(
                            "\$ref" to "#/definitions/${def.schemaRef}"
                        )
                    )
                }
                params.add(fieldSchema)
            }

            val route = json {
                obj(
                    method to obj(
                        "summary" to def.describe,
                        "produces" to jsonArrayOf(def.produces),
                        "consumes" to jsonArrayOf(def.consumes),
                        "parameters" to params,
                        "responses" to responses
                    )
                )
            }
            paths.put(convertedPath, route)
        }

        schema.put("paths", paths)

        try {
            logInfo("Writing swagger file to $swaggerFilePath")
            val writer = Files.newBufferedWriter(File(swaggerFilePath).toPath(), Charset.forName("UTF-8"))
            writer.write(schema.encodePrettily())
            writer.close()
        } catch (ex: Exception) {
            ex.printStackTrace();
        }
    }

    private fun rulesToProperty(
        methodName: String,
        rules: Map<String, String>,
        ruleSchema: HashMap<String, Map<String, List<Any>>>
    ): PropertySpec {
        var fieldRuleStr = "mapOf(\n"
        val tabArgs = "                            "
        val tabNextField = "                        "

        /** Need to generate this property in controller schema class
        var ruleName = mapOf(
        "required" to null,
        "length" to listOf(2, 12)
        )
         */
        if (rules.isNotEmpty()) {
            for ((field, ruleStr) in rules) {
                val rule = Validator.parseRules(ruleStr)
                ruleSchema[field] = rule

                var args = ""

                for ((ruleName, ruleArgs) in rule) {
                    args += "$tabArgs\"$ruleName\" to "

                    if (ruleArgs.isNotEmpty()) {
                        if (ruleArgs.first() is String) {
                            val joined = ruleArgs.joinToString { "\"${it}\"" }
                            args += "listOf($joined),\n"
                        } else {
                            args += "listOf(${ruleArgs.joinToString(", ")}),\n"
                        }
                    } else {
                        args += "null,\n"
                    }
                }

                args = "\n" + args.substring(0, args.length - 2) + "\n$tabNextField"

                fieldRuleStr += """
                    "$field" to mapOf($args)
                """.trimIndent()
                fieldRuleStr += ",\n"
            }
            fieldRuleStr = fieldRuleStr.substring(0, fieldRuleStr.length - 2)
        }
        fieldRuleStr += ")"

        //Map<String, Map<String, List<Any>?>>
        val ruleMapValueType = Map::class.asClassName()
            .parameterizedBy(
                String::class.asClassName(),
                List::class.asClassName().parameterizedBy(
                    Any::class.asClassName()
                ).copy(nullable = true)
            )

        val ruleMapType = Map::class.asClassName()
            .parameterizedBy(String::class.asClassName(), ruleMapValueType)

        val fieldRules = PropertySpec.builder("${methodName}Rules", ruleMapType)
            .initializer("%L", fieldRuleStr)
            .build()

        return fieldRules
    }

    private fun generateRouteClasses(definitions: List<RouteDefinition>) {
        val fileName = "GeneratedRoutes"
        var codeRoutes = ""

        for (def in definitions) {
            val (className, methodName, routePath, httpMethod, coroutine, hasRules, pack) = def
            var route = """
                    router.$httpMethod("$routePath").handler({ 
                        $className(vertx, logger, it).$methodName(it) 
                    })
                """.trimIndent()

            if (hasRules) {
                if (coroutine) {
                    route = """
                    koto(router.$httpMethod("$routePath"), { rc, cc ->
                        withContext(cc) {
                            rc.put("inputRules", ${className}Schema.${methodName}Rules)
                            $className(vertx, logger, rc).$methodName(rc) 
                        }
                    })
                """.trimIndent()
                } else {
                    route = """
                    router.$httpMethod("$routePath").handler({ 
                        it.put("inputRules", ${className}Schema.${methodName}Rules)
                        $className(vertx, logger, it).$methodName(it) 
                    })
                """.trimIndent()
                }
            } else if (coroutine) {
                route = """
                    koto(router.$httpMethod("$routePath"), { rc, cc ->
                        withContext(cc) {
                            $className(vertx, logger, rc).$methodName(rc) 
                        }
                    })
                """.trimIndent()
            }
            codeRoutes += route + "\n"
        }

        //Create handleRoutes method
        //(route: Route, fn: suspend (RoutingContext) -> Unit) -> Unit
        val fnInnerParam = LambdaTypeName.get(
            parameters = listOf(
                ParameterSpec.builder("rc", RoutingContext::class.asTypeName()).build(),
                ParameterSpec.builder("cc", CoroutineContext::class.asTypeName()).build()
            ),
            returnType = Unit::class.asTypeName()
        ).copy(suspending = true)

        val fnParam = LambdaTypeName.get(
            parameters = listOf(
                ParameterSpec.builder("route", Route::class).build(),
                ParameterSpec.builder("fn", fnInnerParam).build()
            ),
            returnType = Unit::class.asTypeName()
        )

        val pack = definitions.first().pack
        val file = FileSpec.builder(pack, fileName)
            .addType(
                TypeSpec.classBuilder(fileName)
                    .superclass(RouteSchema::class)
                    .addSuperclassConstructorParameter(
                        "%N",
                        ParameterSpec.builder("vertx", Vertx::class)
                            .build()
                    )
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameter("vertx", Vertx::class)
                            .build()
                    )
//                        .addProperty(PropertySpec.builder("vertx", Vertx::class)
//                                .initializer("vertx")
//                                .build())
                    .addFunction(
                        FunSpec.builder("handleRoutes")
                            .addModifiers(KModifier.OVERRIDE)
                            .addParameter("router", Router::class)
                            .addParameter("logger", Logger::class)
                            .addParameter("koto", fnParam)
                            .addStatement(codeRoutes)
                            .build()
                    )
                    .build()
            )
            .addImport(RoutingContext::class.asClassName().packageName, RoutingContext::class.asClassName().simpleName)
            .addImport(Route::class.asClassName().packageName, Route::class.asClassName().simpleName)
            .addImport(CoroutineScope::class.asClassName().packageName, CoroutineScope::class.asClassName().simpleName)
            .addImport(Dispatchers::class.asClassName().packageName, Dispatchers::class.asClassName().simpleName)
            .addImport("kotlinx.coroutines", "launch")
            .addImport("kotlinx.coroutines", "withContext")
            .build()

        val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]
        logInfo("Writing route class $kaptKotlinGeneratedDir/$fileName.kt")

        file.writeTo(File(kaptKotlinGeneratedDir, "$fileName.kt"))
    }

    private fun logInfo(charSequence: CharSequence) {
        log(Diagnostic.Kind.NOTE, charSequence)
    }

    private fun log(kind: Diagnostic.Kind, charSequence: CharSequence) {
        processingEnv.messager.printMessage(kind, charSequence)
    }

}
