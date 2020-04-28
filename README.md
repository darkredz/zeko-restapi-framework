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
This framework is very easy-to-use. After reading this short documentation, you will have learnt enough.

## Installation
Add this to your maven pom.xml

    <dependency>
      <groupId>io.zeko</groupId>
      <artifactId>zeko-restapi</artifactId>
      <version>1.0.3</version>
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
    
#### Enable annotation processing
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
                        <processorArg>swagger.sampleResultDir=/Users/leng/Documents/zeko-restapi-example/api-doc</processorArg>
                        <processorArg>swagger.outputFile=/Users/leng/Documents/zeko-restapi-example/api-doc/swagger.json</processorArg>
                        <processorArg>swagger.cmpSchemaDir=/Users/leng/Documents/zeko-restapi-example/api-doc/api-schemas</processorArg>
                        <processorArg>default.produces=application/json</processorArg>
                        <processorArg>default.consumes=application/x-www-form-urlencoded</processorArg>
                    </annotationProcessorArgs>
                </configuration>
            </execution>
            
            //.... other execution ...
        </executions>
    </plugin>

#### Compile & run your app
Compile and run your vertx app:
```
mvn clean compile vertx:run -Dvertx.verticle="io.zeko.restapi.examples.BootstrapVerticle"
```
You should see the following output during compilation, after you have created and annotated your endpoints in controller classes
```
[INFO] --- vertx-maven-plugin:1.0.18:initialize (vmp) @ simple-api ---
[INFO] 
[INFO] --- kotlin-maven-plugin:1.3.61:kapt (kapt) @ simple-api ---
[INFO] Note: Writing controller schema /Users/leng/Documentszeko-restapi-example/target/generated-sources/kaptKotlin/compile/UserControllerSchema.kt
[INFO] Note: Writing route class /Users/leng/Documents/zeko-restapi-example/target/generated-sources/kaptKotlin/compile/GeneratedRoutes.kt
[INFO] Note: Writing swagger file to /Users/leng/Documents/zeko-restapi-example/api-doc/swagger.json
[INFO] Note: Writing cron class /Users/leng/Documents/zeko-restapi-example/target/generated-sources/kaptKotlin/compile/GeneratedCrons.kt
[INFO] 
```
Now you can view the swagger.json under the directory you have configured in any Swagger/OpenAPI UI tools or Postman
