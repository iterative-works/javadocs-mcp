// PURPOSE: Application entry point for javadocs-mcp server
// PURPOSE: Initializes dependencies and starts MCP HTTP server

package javadocsmcp

import javadocsmcp.application.DocumentationService
import javadocsmcp.infrastructure.{CoursierArtifactRepository, JarFileReader}
import javadocsmcp.presentation.McpServer

@main def run(args: String*): Unit = {
  val port = args.headOption.flatMap(_.toIntOption).getOrElse(8080)

  println(s"Initializing javadocs-mcp server...")

  val repository = CoursierArtifactRepository()
  val reader = JarFileReader()
  val service = DocumentationService(repository, reader)

  McpServer.start(service, port)
}
