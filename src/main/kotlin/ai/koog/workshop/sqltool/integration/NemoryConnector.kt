package ai.koog.workshop.sqltool.integration

import ai.koog.workshop.sqltool.models.Column
import ai.koog.workshop.sqltool.models.NemoryDatabase
import ai.koog.workshop.sqltool.models.Schema
import ai.koog.workshop.sqltool.models.Table
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import java.io.File

class NemoryConnector(
    private val nemoryYamlPth: String
) {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false  //Ignore unknown properties
        )
    )

    private val database: NemoryDatabase by lazy {
        val yamlContent = File(nemoryYamlPth).readText()
        yaml.decodeFromString(NemoryDatabase.serializer(), yamlContent)
    }

    private val defaultSchema: Schema by lazy {
        database.catalogs.first().schemas.first()
    }

    fun getDatabaseInfo(): NemoryDatabase = database

    fun getAllTables(): List<Table> = defaultSchema.tables

    fun getTableByName(tableName: String?): Table? = defaultSchema.tables.find { it.name == tableName }

    fun getTableRowCount(tableName: String): Int = getTableByName(tableName)?.samples?.size ?: 0

    fun getSampleData(tableName: String, limit: Int): List<Map<String, String?>> =
        getTableByName(tableName)?.samples?.take(limit) ?: emptyList()

    fun getRelatedTables(tableName: String): List<Pair<Table, String>> {
        val table = getTableByName(tableName) ?: return emptyList()
        val relatedTables = mutableListOf<Pair<Table, String>>()

        defaultSchema.tables.forEach { otherTable ->
            otherTable.foreignKeys?.forEach { fk ->
                if (fk.referencedTable.equals(tableName, ignoreCase = true)) {
                    relatedTables.add(otherTable to "referenced by ${otherTable.name}.${fk.columnName}")
                }
            }
        }

        table.foreignKeys?.forEach { fk ->
            getTableByName(fk.referencedTable)?.let { refTable ->
                relatedTables.add(refTable to "references ${refTable.name}.${fk.referencedColumn}")
            }
        }
        return relatedTables
    }

    fun getColumnInfo(tableName: String, columnName: String): Column? {
        return getTableByName(tableName)?.columns?.find {
            it.name.equals(columnName, ignoreCase = true)
        }
    }

    fun hasIndex(tableName: String, columnName: String): Boolean {
        val table = getTableByName(tableName) ?: return false
        return table.indexes?.any { index ->
            index.columns.any { it.equals(columnName, ignoreCase = true) }
        } ?: false
    }

    // Generate human-readable schema summary
    fun generateSchemaContext(tableNames: List<String>): String = buildString {
        appendLine("DATABASE SCHEMA CONTEXT")
        appendLine("Database: ${database.databaseId}")
        appendLine("Schema: ${defaultSchema.name}")
        appendLine()

        tableNames.forEach { tableName ->
            val table = getTableByName(tableName)
            if (table != null) {
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("📋 Table: ${table.name}")
                appendLine()

                appendLine("Columns:")
                table.columns.forEach { col ->
                    val nullText = if (col.nullable) "NULL" else "NOT NULL"
                    val desc = col.description?.let { " - $it" } ?: ""
                    appendLine("  • ${col.name}: ${col.type} ($nullText)$desc")
                }
                appendLine()

                // Show primary key
                table.primaryKey?.let { pk ->
                    appendLine("🔑 Primary Key: ${pk.joinToString()}")
                    appendLine()
                }

                // Show foreign keys
                table.foreignKeys?.let { fks ->
                    if (fks.isNotEmpty()) {
                        appendLine("🔗 Foreign Keys:")
                        fks.forEach { fk ->
                            appendLine("  • ${fk.columnName} → ${fk.referencedTable}.${fk.referencedColumn}")
                        }
                        appendLine()
                    }
                }

                // Show indexes
                table.indexes?.let { indexes ->
                    if (indexes.isNotEmpty()) {
                        appendLine("⚡ Indexes:")
                        indexes.forEach { idx ->
                            val uniqueText = if (idx.unique) " (UNIQUE)" else ""
                            appendLine("  • ${idx.name}: ${idx.columns.joinToString()}$uniqueText")
                        }
                        appendLine()
                    }
                }

                // Show sample data
                table.samples?.let { samples ->
                    if (samples.isNotEmpty()) {
                        appendLine("📝 Sample Data (${samples.size} rows):")
                        samples.take(2).forEach { row ->
                            appendLine("  ${row}")
                        }
                        appendLine()
                    }
                }

                // Show relationships
                val related = getRelatedTables(tableName)
                if (related.isNotEmpty()) {
                    appendLine("🔄 Relationships:")
                    related.forEach { (relTable, relationship) ->
                        appendLine("  • ${relTable.name}: $relationship")
                    }
                    appendLine()
                }
            }
        }
    }

    // Generate compact context for LLM
    fun generateCompactContext(tableNames: List<String>): String = buildString {
        tableNames.forEach { tableName ->
            val table = getTableByName(tableName) ?: return@forEach

            append("$tableName(")
            append(table.columns.joinToString { col ->
                val pk = if (table.primaryKey?.contains(col.name) == true) " PK" else ""
                val fk = table.foreignKeys?.find { it.columnName == col.name }?.let {
                    " FK→${it.referencedTable}"
                } ?: ""
                "${col.name}:${col.type}${pk}${fk}"
            })
            appendLine(")")

            // Add sample for context
            table.samples?.firstOrNull()?.let { sample ->
                appendLine("  Example: $sample")
            }
        }
    }

}