package io.zeko.restapi.core.controllers

import io.vertx.core.Vertx
import io.zeko.restapi.annotation.http.*
import io.zeko.restapi.annotation.Params
import org.slf4j.Logger
import io.vertx.ext.web.RoutingContext
import io.zeko.db.sql.utilities.toCamelCase
import io.zeko.db.sql.utilities.toSnakeCase
import io.zeko.restapi.core.utilities.downloadZip
import io.zeko.restapi.core.utilities.zip.TempFile

open class ProjectInitController(vertx: Vertx, logger: Logger, context: RoutingContext) :
    ApiController(vertx, logger, context) {

    /**
     * Visit this url to generate and download a new project setup automatically by the framework. JDBC url should be uri encoded
     * http://localhost:9999/project/create?artifact_id=trade-game&group_id=com.mycorp.superapp&version=1.0.0
     * &package_name=com.mycorp.superapp.trade&http_port=8888
     * &jwt_key=YourKey&jwt_refresh_key=YourKey&jwt_expiry=604800&jwt_refresh_expiry=1209600&jwt_refresh_when_expire=false
     * &controllers=user,stock,game_admin,game_manager
     * &db_driver=hikari&jdbc_url=jdbc%3Amysql%3A%2F%2Flocalhost%3A3306%2Fzeko_test%3Fuser%3Droot%26password%3D123456
     */
    @Routing("/create", "route", true, "Create New Zeko Project")
    @Params(
        [
            "artifact_id => required, minLength;3, maxLength;60, alphaNumDash",
            "group_id => required, minLength;3, maxLength;60, [a-zA-Z0-9\\\\_\\\\.]",
            "version => required, minLength;3, maxLength;10, regex;[0-9\\\\.]+",
            "package_name => required, minLength;3, maxLength;60, regex;[a-zA-Z0-9\\\\_\\\\.]+",
            "jwt_key => required",
            "jwt_refresh_key => required",
            "jwt_expiry => required, min;60;",
            "jwt_refresh_expiry => required, min;60;",
            "jwt_refresh_when_expire => required, isBoolean",
            "db_driver => required, inArray;jasync;hikari;vertx",
            "jdbc_url => required, minLength;16, maxLength;160",
            "controllers => required, separateBy",
            "http_port => required, isInteger, min;80, max:20000"
        ]
    )
    open suspend fun createNew(ctx: RoutingContext) {
        val res = validateInput()
        if (!res.success) return

        val packageName = res.values["package_name"].toString()
        val artifactId = res.values["artifact_id"].toString()
        val groupId = res.values["group_id"].toString()
        val version = res.values["version"].toString()

        var dbDriver = res.values["db_driver"].toString()
        val drivers = mapOf("jasync" to "Jasync", "hikari" to "Hikari", "vertx" to "Vertx")
        dbDriver = drivers[dbDriver] + ""

        val dbUrl = res.values["jdbc_url"].toString()
        val jwtKey = res.values["jwt_key"].toString()
        val jwtRefreshKey = res.values["jwt_refresh_key"].toString()
        val jwtExpiry = res.values["jwt_expiry"].toString()
        val jwtRefreshExpiry = res.values["jwt_refresh_expiry"].toString()
        val jwtRefreshWhenExpire = res.values["jwt_refresh_when_expire"].toString()
        val httpPort = res.values["http_port"].toString()

        val controllers = res.values["controllers"].toString().split(",")

        val clsDb = """
package $packageName

import io.vertx.core.Vertx
import org.slf4j.Logger
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.zeko.db.sql.connections.*

class DB {
    private var vertx: Vertx
    private var connPool: ${dbDriver}DBPool
    private var dbLogger: DBLogger

    constructor(vertx: Vertx, logger: Logger) {
        this.vertx = vertx

        val dbConfig = json {
            obj(
                    "url" to "$dbUrl",
                    "max_pool_size" to 30
            )
        }

        dbLogger = AppDBLog(logger)
        connPool = ${dbDriver}DBPool(dbConfig)
    }

    suspend fun session(): DBSession = ${dbDriver}DBSession(connPool, connPool.createConnection()).setQueryLogger(dbLogger)
}
        """.trimIndent()

        val clsAppDbLog = """
package $packageName

import org.slf4j.Logger
import io.zeko.db.sql.connections.DBLogLevel
import io.zeko.db.sql.connections.DBLogger

class AppDBLog(val logger: Logger): DBLogger {
    var paramsLevel: DBLogLevel = DBLogLevel.DEBUG
    var sqlLevel: DBLogLevel = DBLogLevel.DEBUG

    override fun logQuery(sql: String, params: List<Any?>?) {
        if (sqlLevel.level >= DBLogLevel.DEBUG.level) {
            logger.debug("[SQL] ${'$'}sql")
        }
        if (paramsLevel.level >= DBLogLevel.DEBUG.level && params != null) {
            logger.debug("[SQL_PARAM] ${'$'}params")
        }
    }

    override fun logRetry(numRetriesLeft: Int, err: Exception) {
        logger.warn("[SQL_RETRY:${'$'}numRetriesLeft] ${'$'}err")
    }

    override fun logUnsupportedSql(err: Exception) {
        logger.warn("[SQL_UNSUPPORTED] ${'$'}err")
    }

    override fun logError(err: Exception) {
        logger.error("[SQL_ERROR] ${'$'}err")
    }

    override fun getParamsLogLevel(): DBLogLevel {
        return paramsLevel
    }

    override fun getSqlLogLevel(): DBLogLevel {
        return sqlLevel
    }

    override fun setParamsLogLevel(level: DBLogLevel): DBLogger {
        this.paramsLevel = level
        return this
    }

    override fun setSqlLogLevel(level: DBLogLevel): DBLogger {
        this.sqlLevel = level
        return this
    }

    override fun setLogLevels(sqlLevel: DBLogLevel, paramsLevel: DBLogLevel): DBLogger {
        this.sqlLevel = sqlLevel
        this.paramsLevel = paramsLevel
        return this
    }
}
        """.trimIndent()

        val clsKoinFactory = """
package $packageName

import io.vertx.core.Verticle
import io.vertx.core.spi.VerticleFactory
import org.koin.standalone.KoinComponent
import org.koin.standalone.get

object KoinVerticleFactory : VerticleFactory, KoinComponent {
    override fun prefix(): String = "koin"

    override fun createVerticle(verticleName: String, classLoader: ClassLoader): Verticle {
        return get(clazz = Class.forName(verticleName.substringAfter("koin:")).kotlin)
    }
}
        """.trimIndent()

        val clsBootstrap = """
package $packageName

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.json.Json
import org.slf4j.LoggerFactory
import io.vertx.ext.auth.PubSecKeyOptions
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions
import org.koin.core.Koin
import org.koin.dsl.module.module
import org.koin.log.EmptyLogger
import org.koin.standalone.StandAloneContext

class BootstrapVerticle : AbstractVerticle() {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val vertx = Vertx.vertx(VertxOptions().setHAEnabled(false))
            vertx.deployVerticle(BootstrapVerticle())
        }
    }

    override fun start() {
        val logFactory = System.getProperty("org.vertx.logger-delegate-factory-class-name")
        if (logFactory == null) {
            System.setProperty("org.vertx.logger-delegate-factory-class-name", SLF4JLogDelegateFactory::class.java.name)
        }
        val logger = LoggerFactory.getLogger(BootstrapVerticle.javaClass")
        logger.info("STARTING APP...")

        DatabindCodec.mapper().registerModule(JavaTimeModule())
        DatabindCodec.mapper().configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        DatabindCodec.mapper().propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE

        //set JWT keys for auth
        val jwtAuthKeys = listOf(
                PubSecKeyOptions().setAlgorithm("HS256").setBuffer("$jwtKey")
        )
        val jwtOpt = JWTAuthOptions().setPubSecKeys(jwtAuthKeys)
        var jwtAuth = JWTAuth.create(vertx, jwtOpt)

        val jwtRefreshOpt = JWTAuthOptions().setPubSecKeys(listOf(
                PubSecKeyOptions().setAlgorithm("HS256").setBuffer("$jwtRefreshKey")
        ))
        var jwtAuthRefresh = JWTAuth.create(vertx, jwtRefreshOpt)

        val appModules = listOf(module {
            single { vertx }
            single { logger }
            single { DB(vertx, get()) }
            single("jwtAuth") { jwtAuth }
            single("jwtAuthRefresh") { jwtAuthRefresh }
            factory { RestApiVerticle() }
        })

        StandAloneContext.stopKoin()
        StandAloneContext.startKoin(appModules)
        Koin.logger = EmptyLogger()

        vertx.registerVerticleFactory(KoinVerticleFactory)
        vertx.deployVerticle(RestApiVerticle::class.java.canonicalName, DeploymentOptions().setInstances(1))
    }
}
        """.trimIndent()

        val clsRestApi = """
package $packageName

import org.slf4j.Logger
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.TimeoutHandler
import io.zeko.restapi.core.security.JWTAuthHandler
import io.zeko.restapi.core.security.JWTAuthRefreshHandler
import io.zeko.restapi.core.verticles.ZekoVerticle
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject

class RestApiVerticle : ZekoVerticle(), KoinComponent {
    val jwtAuth: JWTAuth by inject("jwtAuth")
    val jwtAuthRefresh: JWTAuth by inject("jwtAuthRefresh")
    val logger: Logger by inject()

    val skipAuth = listOf("/user/login", "/user/register", "/user/refresh-token", "/ping-health")

    override suspend fun start() {
        val router = Router.router(vertx)
        router.route().handler(BodyHandler.create());
        router.route().handler(TimeoutHandler.create(10000L, 503))

        router.route("/*").handler(JWTAuthHandler(jwtAuth, skipAuth))

        //auth access token 60s, refresh token 300s, only allow refresh after token expired
        router.post("/user/refresh-token").handler(JWTAuthRefreshHandler(jwtAuth, jwtAuthRefresh, $jwtExpiry, $jwtRefreshExpiry, $jwtRefreshWhenExpire))

        bindRoutes("$packageName.controller.GeneratedRoutes", router, logger)
        handleRuntimeError(router, logger)

        //start running cron jobs
        //startCronJobs("$packageName.job.GeneratedCrons", logger)

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(config.getInteger("http.port", $httpPort))
    }
}
        """.trimIndent()

        val clsControllers = hashMapOf<String, String>()

        for (route in controllers) {
            val uri = route.toSnakeCase().replace("_", "-").toLowerCase()
            var name = route.replace("-", "_")
            if (name.indexOf("_") > -1) {
                name = name.toCamelCase().capitalize()
            } else {
                name = name.capitalize()
            }
            val clsController = """
package $packageName.controller

import io.vertx.core.Vertx
import io.zeko.restapi.annotation.http.*
import io.zeko.restapi.annotation.Params
import io.zeko.restapi.core.controllers.ApiController
import io.zeko.restapi.core.validations.ValidateResult
import org.slf4j.Logger
import io.vertx.ext.web.RoutingContext
import org.koin.standalone.KoinComponent
import org.koin.standalone.inject

@Routing("/$uri")
class ${name}Controller : KoinComponent, ApiController {
    constructor(vertx: Vertx, logger: Logger, context: RoutingContext) : super(vertx, logger, context)

    override fun validateInput(statusCode: Int): ValidateResult {
        return super.validateInput(422)
    }

//    @GetSuspend("/check/:user_id", "Check this out")
//    @Params(["user_id => required, isInteger, min;1, max;99999999"])
//    suspend fun checThisOut(ctx: RoutingContext) {
//        val res = validateInput()
//        if (!res.success) return
//
//        val uid = res.values["user_id"].toString().toInt()
//        val user = userService.getById(uid)
//        endJson(user)
//    }
}            
            """.trimIndent()

            clsControllers["${name}Controller"] = clsController
        }

        val vertxLogConf = """
handlers=java.util.logging.ConsoleHandler,java.util.logging.FileHandler
java.util.logging.SimpleFormatter.format=[%1${'$'}tF %1${'$'}tT] [%4${'$'}s] %5${'$'}s %6${'$'}s\n
#java.util.logging.SimpleFormatter.format=%5${'$'}s %6${'$'}s\n
java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter
java.util.logging.ConsoleHandler.level=FINE
java.util.logging.FileHandler.level=FINE
java.util.logging.FileHandler.formatter=io.vertx.core.logging.impl.VertxLoggerFormatter

# Put the log in the system temporary directory
java.util.logging.FileHandler.pattern=%t/vertx.log

.level=FINE
io.vertx.level=FINE
com.hazelcast.level=SEVERE
io.netty.util.internal.PlatformDependent.level=SEVERE
        """.trimIndent()

        val runSh = """
mvn clean compile vertx:run -Dvertx.verticle="$packageName.BootstrapVerticle" \
    -Dvertx.jvmArguments="-Djava.util.logging.config.file=vertx_conf/logging.properties" \
    -Dvertx.runArgs="--redeploy-grace-period=5 --redeploy=src/main/kotlin/**/*"
#    -Dvertx.disableDnsResolver=true
#    -Dvertx.runArgs="-cluster"
        """.trimIndent()

        val buildDockerSh = """
        mvn compile jib:dockerBuild
        """.trimIndent()

        val pom = """
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>$groupId</groupId>
    <artifactId>$artifactId</artifactId>
    <version>$version</version>

    <properties>
        <vertx.verticle>$packageName.BootstrapVerticle</vertx.verticle>
        <kotlin.version>1.4.10</kotlin.version>
        <zeko-restapi.version>1.3.1</zeko-restapi.version>
        <vertx.version>4.0.0</vertx.version>
        <micrometer.version>1.1.0</micrometer.version>
        <java.version>1.8</java.version>
        <jib.version>2.2.0</jib.version>

        <kotlin.compiler.incremental>true</kotlin.compiler.incremental>
        <kotlin.compiler.jvmTarget>1.8</kotlin.compiler.jvmTarget>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>

        <junit-jupiter.version>5.2.0</junit-jupiter.version>
        <junit-platform-surefire-provider.version>1.2.0</junit-platform-surefire-provider.version>
        <junit-platform-launcher.version>1.2.0</junit-platform-launcher.version>
        <spek.version>2.0.9</spek.version>
        <jupiter.version>5.2.0</jupiter.version>
    </properties>

    <repositories>
        <repository>
            <id>jcenter</id>
            <url>https://jcenter.bintray.com/</url>
        </repository>
        <repository>
            <id>spek-dev</id>
            <url>https://dl.bintray.com/spekframework/spek-dev</url>
        </repository>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.vertx</groupId>
                <artifactId>vertx-stack-depchain</artifactId>
                <version>${'$'}{vertx.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>io.zeko</groupId>
            <artifactId>zeko-restapi</artifactId>
            <version>${'$'}{zeko-restapi.version}</version>
        </dependency>
        <dependency>
            <groupId>org.jetbrains.kotlinx</groupId>
            <artifactId>kotlinx-coroutines-core</artifactId>
            <version>1.3.3</version>
        </dependency>

        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-core</artifactId>
            <version>2.10.3</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.10.3</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.module</groupId>
            <artifactId>jackson-module-kotlin</artifactId>
            <version>2.10.3</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.module</groupId>
            <artifactId>jackson-module-parameter-names</artifactId>
            <version>2.10.0</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jdk8</artifactId>
            <version>2.10.0</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
            <version>2.10.0</version>
        </dependency>

        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>1.7.30</version>
        </dependency>

        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.2.3</version>
        </dependency>
        
        <dependency>
            <groupId>org.koin</groupId>
            <artifactId>koin-core</artifactId>
            <version>1.0.2</version>
        </dependency>

        <!-- Test dependencies-->
        <dependency>
            <groupId>io.vertx</groupId>
            <artifactId>vertx-junit5</artifactId>
            <version>${'$'}{vertx.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.platform</groupId>
            <artifactId>junit-platform-launcher</artifactId>
            <version>${'$'}{junit-platform-launcher.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <version>${'$'}{jupiter.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.spekframework.spek2</groupId>
            <artifactId>spek-dsl-jvm</artifactId>
            <version>${'$'}{spek.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.spekframework.spek2</groupId>
            <artifactId>spek-runner-junit5</artifactId>
            <version>${'$'}{spek.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <sourceDirectory>${'$'}{project.basedir}/src/main/kotlin</sourceDirectory>
        <testSourceDirectory>src/test/kotlin</testSourceDirectory>

        <plugins>
            <plugin>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>2.22.0</version>
                <dependencies>
                    <dependency>
                        <groupId>org.junit.platform</groupId>
                        <artifactId>junit-platform-surefire-provider</artifactId>
                        <version>1.2.0</version>
                    </dependency>
                    <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter-engine</artifactId>
                        <version>${'$'}{jupiter.version}</version>
                    </dependency>
                </dependencies>
                <configuration>
                    <includes>
                        <include>**/*Spec.*</include>
                    </includes>
                </configuration>
            </plugin>

            <plugin>
                <groupId>io.reactiverse</groupId>
                <artifactId>vertx-maven-plugin</artifactId>
                <version>1.0.18</version>
                <executions>
                    <execution>
                        <id>vmp</id>
                        <goals>
                            <goal>initialize</goal>
                            <goal>package</goal>
                        </goals>
                    </execution>
                </executions>

                <configuration>
                    <redeploy>true</redeploy>
                    <jvmArgs>
                        <jvmArg>-Djava.util.logging.config.file=vertx_conf/logging.properties</jvmArg>
                    </jvmArgs>
                </configuration>
            </plugin>

            <plugin>
                <artifactId>kotlin-maven-plugin</artifactId>
                <groupId>org.jetbrains.kotlin</groupId>
                <version>${'$'}{kotlin.version}</version>

                <executions>
                    <execution>
                        <id>kapt</id>
                        <goals>
                            <goal>kapt</goal>
                        </goals>
                        <configuration>
                            <sourceDirs>
                                <sourceDir>src/main/kotlin</sourceDir>
                            </sourceDirs>

                            <annotationProcessorPaths>
                                <annotationProcessorPath>
                                    <groupId>io.zeko</groupId>
                                    <artifactId>zeko-restapi</artifactId>
                                    <version>${'$'}{zeko-restapi.version}</version>
                                </annotationProcessorPath>
                            </annotationProcessorPaths>

                            <annotationProcessors>
                                <annotationProcessor>io.zeko.restapi.annotation.codegen.RouteSchemaGenerator</annotationProcessor>
                            </annotationProcessors>

                            <annotationProcessorArgs>
                                <processorArg>swagger.apiVersion=$version</processorArg>
                                <processorArg>swagger.title=Your Rest API</processorArg>
                                <processorArg>swagger.description=This is a RESTful API</processorArg>
                                <processorArg>swagger.host=localhost</processorArg>
                                <processorArg>swagger.basePath=/</processorArg>
                                <processorArg>swagger.sampleResultDir=/Users/you/Documents/$artifactId/api-results</processorArg>
                                <processorArg>swagger.outputFile=/Users/you/Documents/$artifactId/api-doc/swagger.json</processorArg>
                                <processorArg>swagger.cmpSchemaDir=/Users/you/Documents/$artifactId/api-schemas</processorArg>
                                <processorArg>default.produces=application/json</processorArg>
                                <processorArg>default.consumes=application/x-www-form-urlencoded</processorArg>
                            </annotationProcessorArgs>
                        </configuration>
                    </execution>

                    <execution>
                        <id>compile</id>
                        <goals>
                            <goal>compile</goal>
                        </goals>
                        <configuration>
                            <sourceDirs>
                                <sourceDir>${'$'}{project.basedir}/src/main/kotlin</sourceDir>
                                <!-- If you need to mix it with Java -->
                                <sourceDir>${'$'}{project.basedir}/src/main/java</sourceDir>
                            </sourceDirs>
                            <experimentalCoroutines>enable</experimentalCoroutines>
                        </configuration>
                    </execution>
                </executions>
            </plugin>

            <plugin>
                <groupId>com.google.cloud.tools</groupId>
                <artifactId>jib-maven-plugin</artifactId>
                <version>${'$'}{jib.version}</version>
                <configuration>
                    <extraDirectories>
                        <paths>vertx_conf</paths>
                    </extraDirectories>
                    <allowInsecureRegistries>true</allowInsecureRegistries>
                    <container>
                        <mainClass>${'$'}{vertx.verticle}</mainClass>
                    </container>
                    <to>
                        <image>$groupId/$artifactId</image>
                        <tags>
                            <tag>latest</tag>
                            <tag>${'$'}{version}</tag>
                        </tags>
                    </to>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
        """.trimIndent()

        val classes = arrayListOf(clsDb, clsAppDbLog, clsKoinFactory, clsRestApi, clsBootstrap)
        val classNames = arrayListOf("DB", "AppDbLog", "KoinVerticleFactory", "RestApiVerticle", "BootstrapVerticle")

        for ((name, content) in clsControllers) {
            classes.add(content)
            classNames.add("controller/$name")
        }

        val files = arrayListOf<TempFile>()
        classNames.forEachIndexed { index, cls ->
            val fileName = "$artifactId/src/main/kotlin/${packageName.split(".").joinToString("/")}/${cls}.kt"
            files.add(TempFile(fileName, classes[index]))
        }

        files.add(TempFile("$artifactId/vertx_conf/logging.properties", vertxLogConf))
        files.add(TempFile("$artifactId/run.sh", runSh))
        files.add(TempFile("$artifactId/build-docker-image.sh", buildDockerSh))
        files.add(TempFile("$artifactId/pom.xml", pom))

        context.downloadZip(vertx, artifactId, files)
    }
}
