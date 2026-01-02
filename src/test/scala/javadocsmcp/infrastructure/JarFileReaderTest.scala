// PURPOSE: Integration tests for reading content from JAR archives
// PURPOSE: Tests real JAR file extraction with javadoc and sources artifacts

package javadocsmcp.infrastructure

import javadocsmcp.domain.ArtifactCoordinates

class JarFileReaderTest extends munit.FunSuite {
  val repository = CoursierArtifactRepository()
  val reader = JarFileReader()

  test("extract HTML from real slf4j javadoc JAR") {
    // First fetch the JAR
    val coords = ArtifactCoordinates("org.slf4j", "slf4j-api", "2.0.9")
    val jarFile = repository.fetchJavadocJar(coords).toOption.get

    // Then read the Logger.html file
    val result = reader.readEntry(jarFile, "org/slf4j/Logger.html")

    assert(result.isRight, "Should successfully read Logger.html from JAR")
    val html = result.toOption.get
    assert(html.nonEmpty, "HTML content should not be empty")
    assert(html.contains("Logger"), "HTML should contain Logger class name")
    assert(html.contains("interface"), "Logger is an interface")
  }

  test("return error for non-existent HTML path") {
    val coords = ArtifactCoordinates("org.slf4j", "slf4j-api", "2.0.9")
    val jarFile = repository.fetchJavadocJar(coords).toOption.get

    val result = reader.readEntry(jarFile, "org/slf4j/NonExistent.html")

    assert(result.isLeft, "Should return error for non-existent entry")
  }

  test("extract HTML from guava javadoc JAR") {
    val coords = ArtifactCoordinates("com.google.guava", "guava", "32.1.3-jre")
    val jarFile = repository.fetchJavadocJar(coords).toOption.get

    val result = reader.readEntry(jarFile, "com/google/common/collect/ImmutableList.html")

    assert(result.isRight, "Should successfully read ImmutableList.html from JAR")
    val html = result.toOption.get
    assert(html.contains("ImmutableList"), "HTML should contain ImmutableList class name")
  }

  test("extract Java source from real slf4j sources JAR") {
    // First fetch the sources JAR
    val coords = ArtifactCoordinates("org.slf4j", "slf4j-api", "2.0.9")
    val jarFile = repository.fetchSourcesJar(coords).toOption.get

    // Then read the Logger.java file
    val result = reader.readEntry(jarFile, "org/slf4j/Logger.java")

    assert(result.isRight, "Should successfully read Logger.java from sources JAR")
    val source = result.toOption.get
    assert(source.nonEmpty, "Source content should not be empty")
    assert(source.contains("public interface Logger"), "Source should contain Logger interface")
    assert(source.contains("void info(String msg)"), "Source should contain info method")
  }

  test("return error for non-existent source file") {
    val coords = ArtifactCoordinates("org.slf4j", "slf4j-api", "2.0.9")
    val jarFile = repository.fetchSourcesJar(coords).toOption.get

    val result = reader.readEntry(jarFile, "org/slf4j/NonExistent.java")

    assert(result.isLeft, "Should return error for non-existent source file")
  }
}
