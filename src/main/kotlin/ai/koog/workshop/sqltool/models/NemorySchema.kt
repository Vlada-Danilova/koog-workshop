package ai.koog.workshop.sqltool.models

import kotlinx.serialization.Serializable

@Serializable
data class NemoryDatabase(
    val databaseId: String,
    val catalogs: List<Catalog>
)

@Serializable
data class Catalog(
    val name: String,
    val schemas: List<Schema>,
    val description: String? = null
)

@Serializable
data class Schema(
    val name: String,
    val tables: List<Table>,
    val description: String? = null
)

@Serializable
data class Table(
    val name: String,
    val columns: List<Column>,
    val samples: List<Map<String, String?>>? = null,
    val primaryKey: List<String>? = null,
    val foreignKeys: List<ForeignKey>? = null,
    val indexes: List<Index>? = null,
    val description: String? = null
)

@Serializable
data class Column(
    val name: String,
    val type: String,
    val nullable: Boolean,
    val description: String? = null
)

@Serializable
data class ForeignKey(
    val columnName: String,
    val referencedTable: String,
    val referencedColumn: String
)

@Serializable
data class Index(
    val name: String,
    val columns: List<String>,
    val unique: Boolean = false
)