// PURPOSE: Application entry point for javadocs-mcp server
// PURPOSE: Initializes dependencies and starts MCP HTTP server

package javadocsmcp

import javadocsmcp.application.{DocumentationService, SourceCodeService}
import javadocsmcp.infrastructure.{CoursierArtifactRepository, JarFileReader}
import javadocsmcp.presentation.McpServer

@main def run(args: String*): Unit = {
  val port = args.headOption.flatMap(_.toIntOption).getOrElse(8080)

  println(s"Initializing javadocs-mcp server...")

  val repository = CoursierArtifactRepository()
  val reader = JarFileReader()
  val documentationService = DocumentationService(repository, reader)
  val sourceCodeService = SourceCodeService(repository, reader)

  McpServer.start(documentationService, sourceCodeService, port)
}
