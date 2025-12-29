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
      .description("""Fetch Javadoc/Scaladoc HTML documentation for a Java or Scala library class.

For Java libraries, use ':' separator: groupId:artifactId:version
For Scala libraries, use '::' separator: groupId::artifactId:version

Examples:
  Java:  org.slf4j:slf4j-api:2.0.9
  Scala: org.typelevel::cats-effect:3.5.4""")
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
      .description("""Fetch Java or Scala source code for a library class.

For Java libraries, use ':' separator: groupId:artifactId:version
For Scala libraries, use '::' separator: groupId::artifactId:version

Examples:
  Java:  org.slf4j:slf4j-api:2.0.9
  Scala: org.typelevel::cats-effect:3.5.4""")
      .input[GetSourceInput]

    sourceTool.handle { input =>
      service.getSource(input.coordinates, input.className) match {
        case Right(source) => Right(source.sourceText)
        case Left(error) => Left(error.message)
      }
    }
  }
}
