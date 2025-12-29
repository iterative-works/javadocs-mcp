// PURPOSE: MCP tool definitions using Chimp library
// PURPOSE: Defines MCP tools for documentation and source code fetching

package javadocsmcp.presentation

import chimp.*
import javadocsmcp.application.{DocumentationService, SourceCodeService}
import io.circe.Codec
import sttp.tapir.Schema

case class GetDocInput(
  coordinates: String,
  className: String
) derives Codec, Schema

case class GetSourceInput(
  coordinates: String,
  className: String
) derives Codec, Schema

object ToolDefinitions {
  def getDocumentationTool(service: DocumentationService) = {
    val docTool = tool("get_documentation")
      .description("Fetch Javadoc HTML documentation for a Java library class")
      .input[GetDocInput]

    docTool.handle { input =>
      service.getDocumentation(input.coordinates, input.className) match {
        case Right(doc) => Right(doc.htmlContent)
        case Left(error) => Left(error.message)
      }
    }
  }

  def getSourceTool(service: SourceCodeService) = {
    val sourceTool = tool("get_source")
      .description("Fetch Java source code for a library class")
      .input[GetSourceInput]

    sourceTool.handle { input =>
      service.getSource(input.coordinates, input.className) match {
        case Right(source) => Right(source.sourceText)
        case Left(error) => Left(error.message)
      }
    }
  }
}
