package rj.cocacode.template

import java.io.File

object TemplateEngine {
    private val templates = mutableMapOf<String, Template>()
    
    data class Template(
        val name: String,
        val description: String,
        val content: String,
        val variables: List<String> = emptyList()
    )
    
    fun register(name: String, content: String, description: String = "") {
        val variables = extractVariables(content)
        templates[name] = Template(name, description, content, variables)
    }
    
    fun render(name: String, variables: Map<String, String>): String {
        val template = templates[name] ?: throw IllegalArgumentException("Template not found: $name")
        return renderString(template.content, variables)
    }
    
    fun renderString(content: String, variables: Map<String, String>): String {
        var result = content
        variables.forEach { (key, value) ->
            result = result.replace("{{$key}}", value)
        }
        return result
    }
    
    private fun extractVariables(content: String): List<String> {
        val regex = Regex("\\{\\{(\\w+)\\}\\}")
        return regex.findAll(content).map { it.groupValues[1] }.distinct().toList()
    }
    
    fun getTemplate(name: String): Template? = templates[name]
    
    fun getAllTemplates(): List<Template> = templates.values.toList()
    
    fun loadFromDir(dir: String) {
        val templateDir = File(dir)
        if (!templateDir.exists()) return
        
        templateDir.listFiles()?.forEach { file ->
            if (file.extension == "tmpl" || file.extension == "template") {
                val name = file.nameWithoutExtension
                register(name, file.readText())
            }
        }
    }
}

object DefaultTemplates {
    fun init() {
        TemplateEngine.register("kotlin-class", """
package {{package}}

class {{className}} {
    {{#fields}}
    val {{name}}: {{type}}
    {{/fields}}
    
    fun {{functionName}}() {
        {{body}}
    }
}
        """.trimIndent())
        
        TemplateEngine.register("kotlin-data-class", """
package {{package}}

data class {{className}}(
    {{#fields}}
    val {{name}}: {{type}}{{#comma}},{{/comma}}
    {{/fields}}
)
        """.trimIndent())
        
        TemplateEngine.register("react-component", """
import React from 'react'

interface {{name}}Props {
    {{#props}}
    {{name}}: {{type}}
    {{/props}}
}

export function {{name}}({ {{#props}}{{name}}, {{/props}} }: {{name}}Props) {
    return (
        <div className="{{className}}">
            {{content}}
        </div>
    )
}
        """.trimIndent())
        
        TemplateEngine.register("api-endpoint", """
@RestController
@RequestMapping("{{path}}")
class {{className}}Controller {
    
    @GetMapping
    fun get{{entityName}}s(): List<{{entityName}}> {
        return service.getAll()
    }
    
    @GetMapping("/{id}")
    fun get{{entityName}}(@PathVariable id: Long): {{entityName}}? {
        return service.getById(id)
    }
    
    @PostMapping
    fun create{{entityName}}(@RequestBody {{entityNameLower}}): {{entityName}} {
        return service.create({{entityNameLower}})
    }
    
    @PutMapping("/{id}")
    fun update{{entityName}}(@PathVariable id: Long, @RequestBody {{entityNameLower}}): {{entityName}}? {
        return service.update(id, {{entityNameLower}})
    }
    
    @DeleteMapping("/{id}")
    fun delete{{entityName}}(@PathVariable id: Long) {
        service.delete(id)
    }
}
        """.trimIndent())
    }
}