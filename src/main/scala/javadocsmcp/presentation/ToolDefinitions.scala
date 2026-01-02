// PURPOSE: MCP tool definitions using Chimp library
// PURPOSE: Defines MCP tools for documentation and source code fetching

package javadocsmcp.presentation

import chimp.*
import javadocsmcp.domain.{Documentation, SourceCode, DocumentationError}
import io.circe.Codec
import sttp.tapir.Schema
import scala.language.reflectiveCalls

case class GetDocInput(
  coordinates: String,
  className: String,
  scalaVersion: Option[String] = None
) derives Codec, Schema

case class GetSourceInput(
  coordinates: String,
  className: String,
  scalaVersion: Option[String] = None
) derives Codec, Schema

object ToolDefinitions {
  def getDocumentationTool(service: {
    def getDocumentation(coordinatesStr: String, classNameStr: String, scalaVersion: Option[String]): Either[DocumentationError, Documentation]
  }) = {
    val docTool = tool("get_documentation")
      .description("""Fetch Javadoc/Scaladoc HTML documentation for a Java or Scala library class.

For Java libraries, use ':' separator: groupId:artifactId:version
For Scala libraries, use '::' separator: groupId::artifactId:version

Optional scalaVersion parameter (defaults to "3"):
  - For Scala 3 artifacts: scalaVersion="3"
  - For Scala 2.13 artifacts: scalaVersion="2.13"
  - For Scala 2.12 artifacts: scalaVersion="2.12"
  - Java artifacts ignore this parameter

Examples:
  Java:  coordinates="org.slf4j:slf4j-api:2.0.9"
  Scala 3 (default): coordinates="org.typelevel::cats-effect:3.5.4"
  Scala 2.13: coordinates="org.typelevel::cats-effect:3.5.4", scalaVersion="2.13"
  Explicit suffix (bypass): coordinates="org.typelevel:cats-effect_2.13:3.5.4"  """)
      .input[GetDocInput]

    docTool.handle { input =>
      service.getDocumentation(input.coordinates, input.className, input.scalaVersion) match {
        case Right(doc) => Right(doc.htmlContent)
        case Left(error) => Left(error.message)
      }
    }
  }

  def getSourceTool(service: {
    def getSource(coordinatesStr: String, classNameStr: String, scalaVersion: Option[String]): Either[DocumentationError, SourceCode]
  }) = {
    val sourceTool = tool("get_source")
      .description("""Fetch Java or Scala source code for a library class.

For Java libraries, use ':' separator: groupId:artifactId:version
For Scala libraries, use '::' separator: groupId::artifactId:version

Optional scalaVersion parameter (defaults to "3"):
  - For Scala 3 artifacts: scalaVersion="3"
  - For Scala 2.13 artifacts: scalaVersion="2.13"
  - For Scala 2.12 artifacts: scalaVersion="2.12"
  - Java artifacts ignore this parameter

Examples:
  Java:  coordinates="org.slf4j:slf4j-api:2.0.9"
  Scala 3 (default): coordinates="org.typelevel::cats-effect:3.5.4"
  Scala 2.13: coordinates="org.typelevel::cats-effect:3.5.4", scalaVersion="2.13"
  Explicit suffix (bypass): coordinates="org.typelevel:cats-effect_2.13:3.5.4"  """)
      .input[GetSourceInput]

    sourceTool.handle { input =>
      service.getSource(input.coordinates, input.className, input.scalaVersion) match {
        case Right(source) => Right(source.sourceText)
        case Left(error) => Left(error.message)
      }
    }
  }
}
