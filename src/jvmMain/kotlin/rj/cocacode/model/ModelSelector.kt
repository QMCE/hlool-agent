package rj.cocacode.model

data class Model(
    val id: String,
    val name: String,
    val provider: Provider,
    val cost: ModelCost? = null,
    val latest: Boolean = false
)

data class ModelCost(val input: Double, val output: Double = 0.0)

data class Provider(
    val id: String,
    val name: String
)

object PopularProviders {
    val all = listOf("opencode", "anthropic", "openai", "google", "xai")
}

fun isFree(provider: Provider, cost: ModelCost?): Boolean =
    provider.id == "opencode" && (cost == null || cost.input == 0.0)

class ModelSelector(
    private val models: List<Model> = emptyList()
) {
    fun list(): List<Model> = models

    fun visible(modelID: String, providerID: String): Boolean = true

    fun filterByProvider(providerID: String?): List<Model> =
        if (providerID != null) models.filter { it.provider.id == providerID }
        else models

    fun sortedByName(): List<Model> = models.sortedBy { it.name }

    fun groupedByProvider(): Map<String, List<Model>> =
        models.groupBy { it.provider.name }
}