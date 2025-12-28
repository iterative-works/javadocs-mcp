// PURPOSE: MCP tool definitions using Chimp library
// PURPOSE: Defines get_documentation tool with input schema and handler

package javadocsmcp.presentation

import chimp.*
import javadocsmcp.application.DocumentationService
import io.circe.Codec
import sttp.tapir.Schema

case class GetDocInput(
  coordinates: String,
  className: String
) derives Codec, Schema

object ToolDefinitions {
  def getDocumentationTool(service: DocumentationService) = {
    val adderTool = tool("get_documentation")
      .description("Fetch Javadoc HTML documentation for a Java library class")
      .input[GetDocInput]

    adderTool.handle { input =>
      service.getDocumentation(input.coordinates, input.className) match {
        case Right(doc) => Right(doc.htmlContent)
        case Left(error) => Left(error.message)
      }
    }
  }
}
