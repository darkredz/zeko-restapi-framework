# Zeko Rest API Framework
![alt Zeko RestAPI Framework](./logo.svg "Zeko lightweight RESTful API framework for Kotlin")

<p align="left">
    <a href="https://search.maven.org/search?q=g:%22io.zeko%22">
        <img src="https://img.shields.io/maven-central/v/io.zeko/zeko-restapi.svg?label=Maven%20Central" alt="Maven Central" />
    </a>
    <a href="LICENSE">
        <img src="https://img.shields.io/badge/license-Apache%202-blue.svg?maxAge=2592000" alt="Apache License 2" />
    </a>
    <a href="https://github.com/KotlinBy/awesome-kotlin">
        <img src="https://kotlin.link/awesome-kotlin.svg" alt="Awesome Kotlin Badge" />
    </a>
</p>

Zeko Rest API Framework is a asynchronous web framework written for Kotlin language. 
Create restful APIs in Kotlin easily with automatic Swagger/OpenAPI documentation generation.
It is built on top of [Vert.x event-driven toolkit](https://vertx.io) and designed to be simple & fun to use. 

This library is open source and available under the Apache 2.0 license. Please leave a star if you've found this library helpful!

## Features
- No configuration files, no XML, no YAML, lightweight, easy to use
- Event driven & non-blocking built on top of [Vert.x](https://vertx.io)
- Fast startup & performance
- Automatic Swagger/OpenAPI documentation generation for your RESTful API
- Supports Kotlin coroutines
- Run cron jobs easily!
 
## Getting Started
This framework is very easy-to-use. After reading this short documentation(Still in progress), you will have learnt enough.

Or look at the [example project](https://github.com/darkredz/zeko-restapi-examples) straight away! It's simple enough

## Installation
Add this to your maven pom.xml

    <dependency>
      <groupId>io.zeko</groupId>
      <artifactId>zeko-restapi</artifactId>
      <version>1.0.5</version>
    </dependency>
    <!-- Jasync Mysql driver if needed -->
    <dependency>
       <groupId>com.github.jasync-sql</groupId>
       <artifactId>jasync-mysql</artifactId>
       <version>1.0.17</version>
    </dependency>
    <!-- Hikari Mysql connection pool if needed -->
    <dependency>
        <groupId>com.zaxxer</groupId>
        <artifactId>HikariCP</artifactId>
        <version>3.4.3</version>
    </dependency>
    <!-- Vertx jdbc client if needed -->
    <dependency>
        <groupId>io.vertx</groupId>
        <artifactId>vertx-jdbc-client</artifactId>
        <version>${vertx.version}</version>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.kotlinx</groupId>
        <artifactId>kotlinx-coroutines-core</artifactId>
        <version>1.3.3</version>
    </dependency>
    
### Enable Annotation Processor
In order to get your zeko app up and running, you would need to add annotation preprocessor to your maven pom.
This will automatically generates routes, cron and Swagger 2.0/OpenAPI documentation from your controllers.
Set your kotlin.version accordingly for the KAPT to work. 

    <plugin>
        <artifactId>kotlin-maven-plugin</artifactId>
        <groupId>org.jetbrains.kotlin</groupId>
        <version>${kotlin.version}</version>
    
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
                            <version>1.0.3</version>
                        </annotationProcessorPath>
                    </annotationProcessorPaths>
    
                    <annotationProcessors>
                        <annotationProcessor>io.zeko.restapi.annotation.codegen.RouteSchemaGenerator</annotationProcessor>
                    </annotationProcessors>
    
                    <annotationProcessorArgs>
                        <processorArg>swagger.apiVersion=1.0</processorArg>
                        <processorArg>swagger.title=Simple Rest API</processorArg>
                        <processorArg>swagger.description=This is a simple RESTful API demo</processorArg>
                        <processorArg>swagger.host=localhost</processorArg>
                        <processorArg>swagger.basePath=/</processorArg>
                        <processorArg>swagger.sampleResultDir=/Users/leng/Documents/zeko-restapi-example/api-results</processorArg>
                        <processorArg>swagger.outputFile=/Users/leng/Documents/zeko-restapi-example/api-doc/swagger.json</processorArg>
                        <processorArg>swagger.cmpSchemaDir=/Users/leng/Documents/zeko-restapi-example/api-schemas</processorArg>
                        <processorArg>default.produces=application/json</processorArg>
                        <processorArg>default.consumes=application/x-www-form-urlencoded</processorArg>
                    </annotationProcessorArgs>
                </configuration>
            </execution>
            
            //.... other execution ...
        </executions>
    </plugin>

### Compile & Run
Compile and run your vertx app:
```
mvn clean compile vertx:run -Dvertx.verticle="io.zeko.restapi.examples.BootstrapVerticle"
```
You should see the following output during compilation, after you have created and annotated your endpoints in controller classes
```
[INFO] --- vertx-maven-plugin:1.0.18:initialize (vmp) @ simple-api ---
[INFO] 
[INFO] --- kotlin-maven-plugin:1.3.61:kapt (kapt) @ simple-api ---
[INFO] Note: Writing controller schema /Users/leng/Documents/zeko-restapi-example/target/generated-sources/kaptKotlin/compile/UserControllerSchema.kt
[INFO] Note: Writing route class /Users/leng/Documents/zeko-restapi-example/target/generated-sources/kaptKotlin/compile/GeneratedRoutes.kt
[INFO] Note: Writing swagger file to /Users/leng/Documents/zeko-restapi-example/api-doc/swagger.json
[INFO] Note: Writing cron class /Users/leng/Documents/zeko-restapi-example/target/generated-sources/kaptKotlin/compile/GeneratedCrons.kt
[INFO] 
```
Now you can view the swagger.json under the directory configured (swagger.outputFile) in any Swagger/OpenAPI UI tools or Postman


## Bootstrapping
Zeko doesn't include a DI container, instead of reinventing the wheel, it is recommended to use something awesome like [Koin](https://insert-koin.io) 
or [Dagger](https://github.com/google/dagger) to manage your project's dependency injection. The following instructions will be using Koin DI framework.

Bootstrapping for Zeko rest framework is simple. If you would like to use the built-in SQL builder & client, you could follow the same structure as the 
(example project)[https://github.com/darkredz/zeko-restapi-examples/tree/master/src/main/kotlin/io/zeko/restapi/examples]

```
BootstrapVerticle.kt
KoinVerticleFactory.kt
DB.kt
AppDBLog.kt	
RestApiVerticle.kt
```
The 5 Kotlin classes above are crucial for the app to run.
#### BootstrapVerticle
[BootstrapVerticle](https://github.com/darkredz/zeko-restapi-examples/blob/master/src/main/kotlin/io/zeko/restapi/examples/BootstrapVerticle.kt) is the main entry file of the app. 
Setup your DI here with Koin for most things that are shared globally such as logger, DB pool, web client pool, JWT auth configs, mail service, etc.

#### DB class
DB class is written to setup database connection pool using Jasync, Hikari-CP or Vert.x JDBC client.
From your repository class, access the DB object via Koin DI container:
```kotlin
class UserRepo(val vertx: Vertx) : KoinComponent {
    val db: DB by inject()

    suspend fun getActiveUser(id: Int): User? {
        var user: User? = null
        db.session().once { sess ->
            val sql = Query().fields("id", "first_name", "last_name", "email", "last_access_at")
                            .from("user")
                            .where(("id" eq id) and ("status" eq 1))
                            .limit(1).toSql()

            val rows = sess.query(sql, { User(it) }) as List<User>
            if (rows.isNotEmpty()) user = rows[0]
        }
        return user
    }
}
```

#### AppDBLog
During development logging the SQL and prepared statement's parameters will be really useful.
In order to do so, call the setQueryLogger() method on DBSession after it is initialized.
[AppDBLog](https://github.com/darkredz/zeko-restapi-examples/blob/master/src/main/kotlin/io/zeko/restapi/examples/AppDBLog.kt) 
is a simple implementation of DBLogger interface which prints out the logs with vert.x Logger

```kotlin
val dbLogger = AppDBLog(logger).setParamsLogLevel(DBLogLevel.ALL)
JasyncDBSession(connPool, connPool.createConnection()).setQueryLogger(dbLogger) 
```

Implement your own DBLogger for more advanced usage.


#### RestApiVerticle
This would be the place where all the route and cronjob executions happen.
You do not have to define all your endpoints route here manually. 
If they're annotated in the controllers, the routes code will be generated by Zeko KAPT.

Your would just need to bind the generated routes by calling:
```kotlin
bindRoutes("your.name.controllers.GeneratedRoutes", router, logger)
// Or less overhead
bindRoutes(your.name.controllers.GeneratedRoutes(vertx), router, logger)
```

If you have controller classes in different packages, then it is required to call bindRoutes multiple times:
```kotlin
bindRoutes("your.name.controllers.GeneratedRoutes", router, logger)
bindRoutes("his.controllers.GeneratedRoutes", router, logger)
```

The same applies to generated cron jobs
```kotlin
startCronJobs("my.example.jobs.GeneratedCrons", logger)
// Or
startCronJobs(my.example.jobs.GeneratedCrons(vertx, logger), logger)
```

Default error handler, which will output message with status code 500 if any exception is thrown.
503 for connection timeout if you use TimeoutHandler for the routes.
```kotlin
handleRuntimeError(router, logger)
```
