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

Zeko Rest API Framework is an asynchronous web framework written for Kotlin language. 
Create restful APIs in Kotlin easily with automatic Swagger/OpenAPI documentation generation.
It is built on top of [Vert.x event-driven toolkit](https://vertx.io) and designed to be simple & fun to use. 

This library is open source and available under the Apache 2.0 license. Please leave a star if you've found this library helpful!

## Features
- No configuration files, no XML or YAML, lightweight, easy to use
- Event driven & non-blocking built on top of [Vert.x 3.9.0](https://vertx.io) 
- Fast startup & performance
- Supports Kotlin coroutines
- Automatic Swagger/OpenAPI doc generation for your RESTful API
- Code generation via Kotlin kapt
- Largely reflection-free, consumes little memory
- [Project creator](https://github.com/darkredz/zeko-restapi-examples#project-creator) included
- Add endpoint validations easily
- Run cron jobs easily!
- Mail service with Sendgrid & Mandrill
- Simple SQL builder & data mapper
- Built with JVM 8, works fine with JVM 9/10 and above
 
## Getting Started
This framework is very easy-to-use. After reading this short documentation, you will have learnt enough.

Or look at the [example project](https://github.com/darkredz/zeko-restapi-examples) straight away! It's simple enough!

The example project includes a project creator tool which is the quickest way to create a new project (accessible at /project/create endpoint)

## Installation
Add this to your maven pom.xml

    <dependency>
      <groupId>io.zeko</groupId>
      <artifactId>zeko-restapi</artifactId>
      <version>1.1.0</version>
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
        <version>3.9.0</version>
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
                            <version>1.0.7</version>
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
[example project](https://github.com/darkredz/zeko-restapi-examples/tree/master/src/main/kotlin/io/zeko/restapi/examples)

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

## Controllers
For any endpoint to work, you would need to create a class and extends ApiController.
```kotlin
import io.zeko.restapi.annotation.http.*
import io.zeko.restapi.annotation.Params
import io.zeko.restapi.core.controllers.ApiController
import io.zeko.restapi.core.validations.ValidateResult

@Routing("/user")     // <----- (1)
class UserController : ApiController {

    constructor(vertx: Vertx, logger: Logger, context: RoutingContext) : super(vertx, logger, context)

    @GetSuspend("/show-user/:user_id", "Show User profile data")    // <----- (2)
    @Params([    // <----- (3)
        "user_id => required, isInteger, min;1, max;99999999",
        "country => inArray;MY;SG;CN;US;JP;UK"
    ])
    suspend fun getUser(ctx: RoutingContext) {
        val res = validateInput()   // <----- (4)
        if (!res.success) {   // <----- (5)
            return
        }

        val uid = res.values["user_id"].toString().toInt()
        // val user = <call your business logic bla...>
        endJson(user)      // <----- (6)
    }
}
```

1. This Routing annotation will add a prefix to the endpoint URL for the entire class. Thus, the final URI for getUser() will be */user/show-user/123*

2. GetSuspend defines an endpoint route, you should use *@Get* if it isn't a suspend function call. First parameter is the URI, second is the description which will be used to generate the Swagger documentation.
List of routing annotations (add Suspend suffix if it is calling a suspend method):
    
        Get
        Post
        Delete
        Put
        Head
        Patch
        Options
        Routing // define your own

3. Params indicates that the parameters this endpoint requires. It accepts an array of strings which is the rule definitions for the fields needed.

    The format can be explained as:
    ```
    "field_name => required, rule1, rule2;rule2_param, rule3_param;rule3_param"
    ```
    If required is not defined then the parameter would be optional. 
    Each rule is separated by a comma (,) while the rule's parameters are separated by semi-colon (;)
    
    ```
    user_id => required, isInteger, min;1, max;99999999
    ```
    The rule definition above means that user_id field is required, should be an integer, minimum value of 1 and max of 99999999

4. Calls the built in input validation which returns [ValidateResult](https://github.com/darkredz/zeko-restapi-framework/blob/master/src/main/kotlin/io/zeko/restapi/core/validations/ValidateResult.kt)
res.values contains of the parameter values in a hash map.

5. Check if the the validation is successful. If it failed, by default, ApiController will output the errors in JSON format with status code 400.
    ```
    {
        "error_code": 400,
        "errors": {
            "user_id": [
                "User Id is not a valid integer value",
                "User Id minimum value is 1",
                "User Id maximum value is 99"
            ]
        }
    }
    ```

    You could override the status code and error messages by 
    defining a different status code & error messages in the form of Map<String, String>. 
    
    Refer to [ValidationError.defaultMessages](https://github.com/darkredz/zeko-restapi-framework/blob/master/src/main/kotlin/io/zeko/restapi/core/validations/ValidationError.kt) on how to define your custom Rule's error messages
    
   ```kotlin
    override fun inputErrorMessages() = ValidationError.defaultMessages
    override fun validateInput(statusCode: Int): ValidateResult = super.validateInput(422)
    ```
   
6. endJson() will convert the entity or any other object to JSON with Content-Type as application/json and status code 200.
Do remember to define your Jackson [naming strategy](https://github.com/darkredz/zeko-restapi-examples/blob/master/src/main/kotlin/io/zeko/restapi/examples/BootstrapVerticle.kt#L33) in the bootstrap class


## Validations
For the list of predefined rules, refer to keys of [ValidationError.defaultMessages](https://github.com/darkredz/zeko-restapi-framework/blob/master/src/main/kotlin/io/zeko/restapi/core/validations/ValidationError.kt)
or [RuleSet](https://github.com/darkredz/Zeko-Validator/blob/master/src/main/java/io/zeko/validation/RuleSet.java) for all the rules method and its parameters.


## Cron Jobs
Cron job would be similar to the controller routes. You would need to create a class and extends CronJob

```kotlin
package my.example.jobs

import io.vertx.core.json.Json
import io.zeko.restapi.annotation.cron.Cron
import io.zeko.restapi.annotation.cron.CronSuspend
import io.zeko.restapi.core.cron.CronJob

class UserCronJob(vertx: Vertx, logger: Logger) : CronJob(vertx, logger), KoinComponent {

    val userService: UserService by inject()

    @CronSuspend("*/2 * * * *")
    suspend fun showUser() {
        val user = userService.getProfileStatus(1)
        logger.info("Cron showUser " + Json.encode(user))
    }

    @Cron("*/1 * * * *")
    fun showUserNormal() {
        val uid = 1
        val user = User().apply {
            id = uid
            firstName = "I Am"
            lastName = "Mango"
        }
        logger.info("Cron showUserNormal " + Json.encode(user))
    }

}
```
@CronSuspend should be used on any suspend calls while @Cron should be used on method calls without Kotlin coroutine.
The annotation accepts a string value which should be your good old [UNIX cron expression](https://crontab.guru/)

In the sample cron job above, showUser will be executed in every 2 minute, and showUserNormal in every 1 minute.

All cron jobs in the same package will be aggregated into a GeneratedCrons class during kapt phase.

Start the cron job from RestApiVerticle:
```kotlin
startCronJobs(my.example.jobs.GeneratedCrons(vertx, logger), logger)
```

## Mail Service
The framework provides two mail service: [Sendgird](https://sendgrid.com/) and [Mandrill](https://mandrill.com/)
The mail service classes are using [Vert.x web client](https://vertx.io/docs/vertx-web-client/kotlin/) to call the service APIs.

Example sending via SendGrid. First, create an instance of SendGridMail.
```kotlin
val webClient = SendGridMail.createSharedClient(vertx)
val sendGridConfig = MailConfig(
        "Your Api Key",
        "noreply@zeko.io", "Zeko",
        true, "dev-app@gmail.com"  // this confines the service to send all mails to this Dev email address, useful in dev mode
)
val mailService = SendGridMail(webClient, sendGridConfig, get())
```

Call sendEmail() method to send out emails.
```kotlin
val tags = listOf("super-duber-app-with-zeko.com", "register")
val res: MailResponse = mailService.send(
        email, fullName,
        "Register Success",
        "<h2>Success!</h2><p>You are now a new member!</p>", 
        "Success! You are now a new member!",
        tags
)
```

#### Retries
It would be better if the email will be resent if the API call failed.
The following code will retry to send email 3 more times if the first call failed with a 2 second delay interval.
```kotlin
mailService.retry(3, 2000) {
    it.send(email, fullName,
            "Register Success",
            "<h2>Success!</h2><p>You are now a new member!</p>", 
            "Success! You are now a new member!")
}
```

#### Circuit Breaker
API calls & email sending might fail, these faults can range in severity from a partial loss of connectivity to the complete failure of a service.
For some mission critical tasks, you might want to send emails with a circuit breaker pattern. 

To do so with the mail service in Zeko:
```kotlin
val mailCircuitBreaker = SendGridMail.createCircuitBreaker(vertx)

mailService.sendInCircuit(circuitBreaker, 
            email, fullName,
            "User Registration Success",
            "<h2>Success!</h2><p>You are now a new user!</p>",
            "Success! You are now a new user!")
```
Circuit breaker instance should be better shared and not created on every email send, put it into your DI container instead.

By default, the createCircuitBreaker() method creates a circuit breaker with name of "zeko.mail.sendgrid" (or "zeko.mail.mandrill" for MandrillMailService),
 along with max failures of 5, and 8 maximum retries. Change the behaviour by providing your own [CircuitBreakerOptions](https://vertx.io/docs/apidocs/io/vertx/circuitbreaker/CircuitBreakerOptions.html)

```kotlin
// Unlimited retries
val opt = CircuitBreakerOptions().apply { 
    maxFailures = 15
    maxRetries = 0
}
SendGridMail.createCircuitBreaker(vertx, "important.mailtask1", opt)
```

## SQL Queries
Just use any sql builder libraries or refer to [Zeko's SQL Builder](https://github.com/darkredz/Zeko-SQL-Builder)

## Data Mapper
DIY or refer to [Zeko Data Mapper](https://github.com/darkredz/Zeko-Data-Mapper)
