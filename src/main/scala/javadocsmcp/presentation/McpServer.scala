// PURPOSE: MCP HTTP server using Chimp and Netty
// PURPOSE: Exposes MCP tools via HTTP JSON-RPC endpoint

package javadocsmcp.presentation

import chimp.mcpEndpoint
import javadocsmcp.domain.{Documentation, SourceCode, DocumentationError}
import sttp.tapir.server.netty.sync.NettySyncServer
import scala.language.reflectiveCalls

object McpServer {

  case class ServerHandle(thread: Thread, port: Int) {
    def stop(): Unit = thread.interrupt()
  }

  /** Start server asynchronously in background thread and return a handle for stopping it */
  def startAsync(
    documentationService: {
      def getDocumentation(coordinatesStr: String, classNameStr: String, scalaVersion: Option[String]): Either[DocumentationError, Documentation]
    },
    sourceCodeService: {
      def getSource(coordinatesStr: String, classNameStr: String, scalaVersion: Option[String]): Either[DocumentationError, SourceCode]
    },
    port: Int
  ): ServerHandle = {
    val getDocTool = ToolDefinitions.getDocumentationTool(documentationService)
    val getSourceTool = ToolDefinitions.getSourceTool(sourceCodeService)
    val endpoint = mcpEndpoint(List(getDocTool, getSourceTool), List("mcp"), showJsonSchemaMetadata = false)

    val thread = new Thread(() => {
      try {
        println(s"Starting MCP server on port $port")
        NettySyncServer()
          .port(port)
          .addEndpoint(endpoint)
          .startAndWait()
      } catch {
        case _: InterruptedException => // Expected during shutdown
        case ex: Exception => println(s"Server error: ${ex.getMessage}")
      }
    })
    thread.setDaemon(true)
    thread.start()
    Thread.sleep(1000) // Give server time to start
    ServerHandle(thread, port)
  }

  /** Start server and block until shutdown (for Main.scala) */
  def start(
    documentationService: {
      def getDocumentation(coordinatesStr: String, classNameStr: String, scalaVersion: Option[String]): Either[DocumentationError, Documentation]
    },
    sourceCodeService: {
      def getSource(coordinatesStr: String, classNameStr: String, scalaVersion: Option[String]): Either[DocumentationError, SourceCode]
    },
    port: Int
  ): Unit = {
    val getDocTool = ToolDefinitions.getDocumentationTool(documentationService)
    val getSourceTool = ToolDefinitions.getSourceTool(sourceCodeService)
    val endpoint = mcpEndpoint(List(getDocTool, getSourceTool), List("mcp"), showJsonSchemaMetadata = false)

    println(s"Starting MCP server on port $port")
    NettySyncServer()
      .port(port)
      .addEndpoint(endpoint)
      .startAndWait()
  }
}
