package rj.cocacode.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun parseYaml(input: String): Any? = withContext(Dispatchers.IO) {
    try {
        val clsName = "org.yaml.snakeyaml.Yaml"
        val cls = Class.forName(clsName)
        val ctor = cls.getConstructor()
        val yaml = ctor.newInstance()
        val loadMethod = cls.getMethod("load", Any::class.java)
        @Suppress("UNCHECKED_CAST")
        loadMethod.invoke(yaml, input) as? Any
    } catch (t: Throwable) {
        null
    }
}
