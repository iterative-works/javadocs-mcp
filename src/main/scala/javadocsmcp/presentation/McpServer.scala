// PURPOSE: MCP HTTP server using Chimp and Netty
// PURPOSE: Exposes MCP tools via HTTP JSON-RPC endpoint

package javadocsmcp.presentation

import chimp.*
import javadocsmcp.application.DocumentationService
import sttp.tapir.server.netty.sync.NettySyncServer

object McpServer {

  case class ServerHandle(thread: Thread, port: Int) {
    def stop(): Unit = thread.interrupt()
  }

  /** Start server asynchronously in background thread and return a handle for stopping it */
  def startAsync(service: DocumentationService, port: Int): ServerHandle = {
    val getDocTool = ToolDefinitions.getDocumentationTool(service)
    val mcpEndpoint = chimp.mcpEndpoint(List(getDocTool), List("mcp"))

    val thread = new Thread(() => {
      try {
        println(s"Starting MCP server on port $port")
        NettySyncServer()
          .port(port)
          .addEndpoint(mcpEndpoint)
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
  def start(service: DocumentationService, port: Int): Unit = {
    val getDocTool = ToolDefinitions.getDocumentationTool(service)
    val mcpEndpoint = chimp.mcpEndpoint(List(getDocTool), List("mcp"))

    println(s"Starting MCP server on port $port")
    NettySyncServer()
      .port(port)
      .addEndpoint(mcpEndpoint)
      .startAndWait()
  }
}
