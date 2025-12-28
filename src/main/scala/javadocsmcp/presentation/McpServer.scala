// PURPOSE: MCP HTTP server using Chimp and Netty
// PURPOSE: Exposes MCP tools via HTTP JSON-RPC endpoint

package javadocsmcp.presentation

import chimp.*
import javadocsmcp.application.DocumentationService
import sttp.tapir.server.netty.sync.NettySyncServer

object McpServer {
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
