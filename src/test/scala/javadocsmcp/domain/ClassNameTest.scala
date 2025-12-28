// PURPOSE: Test suite for ClassName value object and HTML path mapping
// PURPOSE: Ensures class names are correctly converted to javadoc HTML file paths

package javadocsmcp.domain

class ClassNameTest extends munit.FunSuite {
  test("convert class name to HTML path") {
    val result = ClassName.parse("org.slf4j.Logger")

    assert(result.isRight, "Should successfully parse valid class name")
    val className = result.toOption.get
    assertEquals(className.toHtmlPath, "org/slf4j/Logger.html")
  }

  test("strip inner class suffix") {
    val result = ClassName.parse("org.slf4j.Logger$Factory")

    assert(result.isRight, "Should successfully parse class with inner class suffix")
    val className = result.toOption.get
    assertEquals(className.toHtmlPath, "org/slf4j/Logger.html",
      "Should strip $Factory and use outer class")
  }

  test("handle nested inner classes") {
    val result = ClassName.parse("com.example.Outer$Middle$Inner")

    assert(result.isRight, "Should handle nested inner classes")
    val className = result.toOption.get
    assertEquals(className.toHtmlPath, "com/example/Outer.html",
      "Should use only the outermost class")
  }

  test("reject empty class name") {
    val result = ClassName.parse("")

    assert(result.isLeft, "Should reject empty string")
  }

  test("reject class name with only whitespace") {
    val result = ClassName.parse("   ")

    assert(result.isLeft, "Should reject whitespace-only string")
  }
}
