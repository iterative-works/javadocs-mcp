// PURPOSE: Scala-CLI project configuration for javadocs-mcp
// PURPOSE: Defines dependencies and build settings

//> using scala 3.7.4
//> using jvm 21

//> using dep "io.get-coursier:coursier_2.13:2.1.24"
//> using dep "io.get-coursier::dependency:0.3.2"

// TASTy analysis for Scala source lookup
//> using dep "ch.epfl.scala::tasty-query:1.6.1"

// MCP Server (Chimp)
//> using dep "com.softwaremill.chimp::core:0.1.6"
//> using dep "com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.13.4"

// Testing
//> using test.dep "org.scalameta::munit:1.0.0"
//> using test.dep "com.softwaremill.sttp.client3::core:3.9.0"
//> using test.dep "io.circe::circe-parser:0.14.6"

//> using option -Werror
//> using option -Wunused:all
//> using option -deprecation
//> using option -feature
