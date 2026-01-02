// PURPOSE: Test suite for DocumentationError message formatting
// PURPOSE: Ensures error messages are clear, actionable, and user-friendly

package javadocsmcp.domain

class ErrorsTest extends munit.FunSuite {

  test("ArtifactNotFound message includes coordinates") {
    val error = DocumentationError.ArtifactNotFound("org.example:fake:1.0.0")
    val msg = error.message

    assert(msg.contains("org.example:fake:1.0.0"), "Message should include artifact coordinates")
  }

  test("ArtifactNotFound message includes Maven Central suggestion") {
    val error = DocumentationError.ArtifactNotFound("org.example:fake:1.0.0")
    val msg = error.message

    assert(msg.contains("Maven Central"), "Message should suggest checking Maven Central")
    assert(msg.contains("https://search.maven.org/"), "Message should include Maven Central URL")
  }

  test("ArtifactNotFound message includes spelling check suggestion") {
    val error = DocumentationError.ArtifactNotFound("org.example:fake:1.0.0")
    val msg = error.message

    assert(msg.contains("spelling") || msg.contains("Spelling"), "Message should suggest checking spelling")
  }

  test("JavadocNotAvailable message includes coordinates") {
    val error = DocumentationError.JavadocNotAvailable("org.example:lib:2.0.0")
    val msg = error.message

    assert(msg.contains("org.example:lib:2.0.0"), "Message should include artifact coordinates")
  }

  test("JavadocNotAvailable message suggests using get_source") {
    val error = DocumentationError.JavadocNotAvailable("org.example:lib:2.0.0")
    val msg = error.message

    assert(msg.contains("get_source"), "Message should suggest using get_source instead")
  }

  test("JavadocNotAvailable message explains libraries don't publish javadoc") {
    val error = DocumentationError.JavadocNotAvailable("org.example:lib:2.0.0")
    val msg = error.message

    assert(msg.contains("don't publish javadoc") || msg.contains("not available"),
      "Message should explain that some libraries don't publish javadoc")
  }

  test("SourcesNotAvailable message includes coordinates") {
    val error = DocumentationError.SourcesNotAvailable("org.example:another:3.0.0")
    val msg = error.message

    assert(msg.contains("org.example:another:3.0.0"), "Message should include artifact coordinates")
  }

  test("SourcesNotAvailable message suggests using get_documentation") {
    val error = DocumentationError.SourcesNotAvailable("org.example:another:3.0.0")
    val msg = error.message

    assert(msg.contains("get_documentation"), "Message should suggest using get_documentation instead")
  }

  test("SourcesNotAvailable message explains libraries don't publish sources") {
    val error = DocumentationError.SourcesNotAvailable("org.example:another:3.0.0")
    val msg = error.message

    assert(msg.contains("don't publish sources") || msg.contains("not available"),
      "Message should explain that some libraries don't publish sources")
  }

  test("ClassNotFound message includes class name") {
    val error = DocumentationError.ClassNotFound("org.example.MyClass")
    val msg = error.message

    assert(msg.contains("org.example.MyClass"), "Message should include class name")
  }

  test("ClassNotFound message suggests checking spelling and capitalization") {
    val error = DocumentationError.ClassNotFound("org.example.MyClass")
    val msg = error.message

    assert(msg.contains("spelling") || msg.contains("Spelling"), "Message should suggest checking spelling")
    assert(msg.contains("capitalization") || msg.contains("case-sensitive"),
      "Message should mention capitalization/case-sensitivity")
  }

  test("InvalidCoordinates message includes invalid input") {
    val error = DocumentationError.InvalidCoordinates("invalid-format")
    val msg = error.message

    assert(msg.contains("invalid-format"), "Message should include the invalid input")
  }

  test("InvalidCoordinates message shows expected format examples") {
    val error = DocumentationError.InvalidCoordinates("invalid")
    val msg = error.message

    assert(msg.contains("groupId:artifactId:version") || msg.contains("org.slf4j:slf4j-api:2.0.9"),
      "Message should show expected format")
  }

  test("InvalidCoordinates message distinguishes Java and Scala formats") {
    val error = DocumentationError.InvalidCoordinates("invalid")
    val msg = error.message

    assert(msg.contains("::") || msg.contains("Scala"), "Message should mention Scala format with ::")
    assert(msg.contains("Java") || msg.contains(":"), "Message should mention Java format")
  }

  test("InvalidClassName message includes invalid input") {
    val error = DocumentationError.InvalidClassName("bad-class-name")
    val msg = error.message

    assert(msg.contains("bad-class-name"), "Message should include the invalid input")
  }

  test("InvalidClassName message shows expected format") {
    val error = DocumentationError.InvalidClassName("bad")
    val msg = error.message

    assert(msg.contains("fully qualified") || msg.contains("org.slf4j.Logger"),
      "Message should show expected format")
  }
}
