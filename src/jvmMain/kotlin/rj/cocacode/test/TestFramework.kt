package rj.cocacode.test

abstract class TestSuite {
    private val tests = mutableListOf<TestCase>()
    
    data class TestCase(
        val name: String,
        val test: () -> Any
    )
    
    fun test(name: String, block: () -> Any) {
        tests.add(TestCase(name, block))
    }
    
    fun run(): TestResult {
        var passed = 0
        var failed = 0
        val errors = mutableListOf<String>()
        
        tests.forEach { tc ->
            try {
                tc.test()
                passed++
            } catch (e: AssertionError) {
                failed++
                errors.add("${tc.name}: ${e.message}")
            } catch (e: Exception) {
                failed++
                errors.add("${tc.name}: Unexpected error: ${e.message}")
            }
        }
        
        return TestResult(passed, failed, errors)
    }
}

data class TestResult(
    val passed: Int,
    val failed: Int,
    val errors: List<String>
) {
    fun isSuccess(): Boolean = failed == 0
}

object Assertions {
    fun assertTrue(condition: Boolean, message: String = "Expected true") {
        if (!condition) throw AssertionError(message)
    }
    
    fun assertFalse(condition: Boolean, message: String = "Expected false") {
        if (condition) throw AssertionError(message)
    }
    
    fun assertEquals(expected: Any?, actual: Any?, message: String = "") {
        if (expected != actual) {
            throw AssertionError("$message Expected: $expected, Actual: $actual")
        }
    }
    
    fun assertNotEquals(expected: Any?, actual: Any?, message: String = "") {
        if (expected == actual) {
            throw AssertionError("$message Expected not: $expected")
        }
    }
    
    fun assertNull(value: Any?, message: String = "Expected null") {
        if (value != null) throw AssertionError("$message but was: $value")
    }
    
    fun assertNotNull(value: Any?, message: String = "Expected not null") {
        if (value == null) throw AssertionError(message)
    }
    
    fun <T> assertContains(collection: Collection<T>, element: T, message: String = "") {
        if (element !in collection) {
            throw AssertionError("$message Collection does not contain: $element")
        }
    }
    
    fun assertThrows(block: () -> Any, message: String = "Expected exception") {
        try {
            block()
            throw AssertionError("$message - No exception thrown")
        } catch (e: Exception) {
        }
    }
}

class AssertionError(message: String) : Exception(message)

object TestRunner {
    fun run(vararg suites: TestSuite): TestResult {
        var totalPassed = 0
        var totalFailed = 0
        val allErrors = mutableListOf<String>()
        
        suites.forEach { suite ->
            val result = suite.run()
            totalPassed += result.passed
            totalFailed += result.failed
            allErrors.addAll(result.errors)
        }
        
        return TestResult(totalPassed, totalFailed, allErrors)
    }
}

class ExampleTests : TestSuite() {
    init {
        test("simple addition") {
            Assertions.assertEquals(2, 1 + 1)
        }
        
        test("string contains") {
            Assertions.assertTrue("hello".contains("ell"))
        }
        
        test("list operations") {
            val list = listOf(1, 2, 3)
            Assertions.assertEquals(3, list.size)
            Assertions.assertEquals(2, list[1])
        }
    }
}