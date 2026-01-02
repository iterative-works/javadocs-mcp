// PURPOSE: Application entry point for javadocs-mcp server
// PURPOSE: Initializes dependencies and starts MCP HTTP server

package javadocsmcp

import javadocsmcp.application.{DocumentationService, SourceCodeService}
import javadocsmcp.infrastructure.{CoursierArtifactRepository, JarFileReader, TastySourceResolver, CachedDocumentationService, CachedSourceCodeService, LRUCache, CacheKey}
import javadocsmcp.domain.DocumentationError
import javadocsmcp.presentation.McpServer

@main def run(args: String*): Unit = {
  val port = args.headOption.flatMap(_.toIntOption).getOrElse(8080)

  // Read cache configuration from environment
  val cacheSizeMB = sys.env.get("CACHE_MAX_SIZE_MB")
    .flatMap(_.toIntOption)
    .getOrElse(100)
  val cacheSizeBytes = cacheSizeMB * 1024L * 1024L

  println(s"Initializing javadocs-mcp server...")
  println(s"Cache configuration: max size = ${cacheSizeMB}MB (${cacheSizeBytes} bytes)")

  // Create infrastructure components
  val repository = CoursierArtifactRepository()
  val reader = JarFileReader()
  val sourcePathResolver = TastySourceResolver(repository)

  // Create base services
  val baseDocumentationService = DocumentationService(repository, reader)
  val baseSourceCodeService = SourceCodeService(repository, reader, sourcePathResolver)

  // Wrap services with caching decorators
  val docCache = LRUCache[CacheKey, Either[DocumentationError, String]](cacheSizeBytes)
  val sourceCache = LRUCache[CacheKey, Either[DocumentationError, String]](cacheSizeBytes)

  val documentationService = CachedDocumentationService(baseDocumentationService, docCache)
  val sourceCodeService = CachedSourceCodeService(baseSourceCodeService, sourceCache)

  McpServer.start(documentationService, sourceCodeService, port)
}
