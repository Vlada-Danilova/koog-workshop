package ai.koog.workshop.sqltool.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.workshop.sqltool.integration.NemoryConnector
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.select.Select

class QueryTutorTools(
    private val nemoryConnector: NemoryConnector
): ToolSet {

    // In-memory store for generated challenge solutions. Keys are challenge IDs.
    private val challengeSolutions = mutableMapOf<String, String>()

    @Serializable
    data class ChallengeQuestion(
        val id: String,
        val title: String,
        val task: String,
        val hints: List<String> = emptyList(),
        val samples: Map<String, String?>? = null
    )

    @Serializable
    data class ChallengeAnswer(
        val id: String,
        val solution: String
    )

    @Tool("analyze_query")
    @LLMDescription("Analyzes SQL query complexity, detects concepts used (JOIN, GROUP BY, etc.), and identifies potential issues like missing indexes or performance problems.")
    fun analyzeQuery(
        @LLMDescription("The SQL SELECT query to analyze")
        query: String?
    ): String {
        return try {
            val statement = CCJSqlParserUtil.parse(query)

            when (statement) {
                is Select -> {
                    val tables = extractTableNames(statement)
                    val concepts = mutableListOf<String>()
                    val issues = mutableListOf<String>()

                    // Detect concepts
                    if (hasJoins(statement)) concepts.add("JOIN")
                    if (hasGroupBy(statement)) concepts.add("GROUP BY")
                    if (hasSubqueries(statement)) concepts.add("Subquery")
                    if (hasWindowFunctions(statement)) concepts.add("Window Functions")

                    // Detect issues using Nemory schema
                    tables.forEach { tableName ->
                        val table = nemoryConnector.getTableByName(tableName)
                        if (table == null) {
                            issues.add("⚠️ Table '$tableName' not found in schema!")
                            return@forEach
                        }

                        // Check for SELECT * on wide tables
                        if (hasSelectAll(statement) && table.columns.size > 10) {
                            issues.add("⚡ SELECT * fetches ${table.columns.size} columns from '$tableName' - specify columns instead")
                        }

                        // Check for missing WHERE clause
                        if (!hasWhereClause(statement)) {
                            table.samples?.let { samples ->
                                if (samples.size > 5) {
                                    issues.add("💡 No WHERE clause - might return many rows from '$tableName'")
                                }
                            }
                        }
                    }

                    val complexity = when {
                        concepts.size > 3 -> "Advanced"
                        concepts.size > 1 -> "Intermediate"
                        else -> "Beginner"
                    }

                    buildString {
                        appendLine("📊 QUERY ANALYSIS")
                        appendLine()
                        appendLine("Complexity Level: $complexity")
                        appendLine("Concepts Used: ${concepts.joinToString() ?: "Basic SELECT"}")
                        appendLine("Tables Involved: ${tables.joinToString()}")
                        appendLine()

                        if (issues.isNotEmpty()) {
                            appendLine("⚠️ ISSUES DETECTED:")
                            issues.forEach { appendLine("  • $it") }
                        } else {
                            appendLine("✅ No issues detected - query looks good!")
                        }
                    }
                }
                else -> "❌ Only SELECT queries are supported currently"
            }
        } catch (e: Exception) {
            "❌ Error parsing query: ${e.message}\nMake sure it's valid SQL syntax."
        }
    }

    @Tool("get_table_schema")
    @LLMDescription("Gets detailed schema information for a specific database table including columns, data types, foreign keys, indexes, and sample data.")
    fun getTableSchema(
        @LLMDescription("The name of the table to look up")
        tableName: String?
    ): String {
        val table = nemoryConnector.getTableByName(tableName)

        return if (table != null) {
            buildString {
                appendLine("📋 TABLE: ${table.name}")
                appendLine()

                appendLine("COLUMNS:")
                table.columns.forEach { col ->
                    val nullable = if (col.nullable) "NULL" else "NOT NULL"
                    val desc = col.description?.let { " - $it" } ?: ""
                    appendLine("  • ${col.name}: ${col.type} ($nullable)$desc")
                }
                appendLine()

                // Primary key
                table.primaryKey?.let { pk ->
                    appendLine("🔑 PRIMARY KEY: ${pk.joinToString()}")
                    appendLine()
                }

                // Foreign keys
                table.foreignKeys?.let { fks ->
                    if (fks.isNotEmpty()) {
                        appendLine("🔗 FOREIGN KEYS:")
                        fks.forEach { fk ->
                            appendLine("  • ${fk.columnName} → ${fk.referencedTable}.${fk.referencedColumn}")
                        }
                        appendLine()
                    }
                }

                // Indexes
                table.indexes?.let { indexes ->
                    if (indexes.isNotEmpty()) {
                        appendLine("⚡ INDEXES:")
                        indexes.forEach { idx ->
                            val unique = if (idx.unique) " (UNIQUE)" else ""
                            appendLine("  • ${idx.name}: ${idx.columns.joinToString()}$unique")
                        }
                        appendLine()
                    }
                }

                // Sample data
                table.samples?.let { samples ->
                    if (samples.isNotEmpty()) {
                        appendLine("📝 SAMPLE DATA (${samples.size} rows):")
                        samples.take(3).forEach { row ->
                            appendLine("  $row")
                        }
                    }
                }
            }
        } else {
            "❌ Table '$tableName' not found in schema. Available tables: ${nemoryConnector.getAllTables().joinToString { it.name }}"
        }
    }

    @Tool("suggest_optimizations")
    @LLMDescription("Analyzes a SQL query and suggests specific performance optimizations like adding indexes, replacing IN subqueries with JOINs, or avoiding SELECT *.")
    fun suggestOptimizations(
        @LLMDescription("The SQL query to optimize")
        query: String?
    ): String {
        val suggestions = mutableListOf<String>()

        try {
            val statement = CCJSqlParserUtil.parse(query) as? Select
                ?: return "❌ Only SELECT queries can be optimized"

            val tables = extractTableNames(statement)

            // Check 1: SELECT *
            if (hasSelectAll(statement)) {
                suggestions.add("""
                    💡 Replace SELECT * with specific columns
                    Reason: Fetches unnecessary data, increases memory and network usage
                    Performance gain: 20-50% faster
                """.trimIndent())
            }

            // Check 2: Missing indexes on WHERE columns
            tables.forEach { tableName ->
                val table = nemoryConnector.getTableByName(tableName)
                if (table != null) {
                    // Simplified: check if WHERE clause exists
                    if (hasWhereClause(statement)) {
                        table.columns.forEach { col ->
                            if (!nemoryConnector.hasIndex(tableName, col.name)) {
                                suggestions.add("""
                                    ⚡ Consider adding index on ${tableName}.${col.name}
                                    SQL: CREATE INDEX idx_${tableName}_${col.name} ON ${tableName}(${col.name});
                                    Performance gain: 50-90% faster for filtered queries
                                """.trimIndent())
                            }
                        }
                    }
                }
            }

            // Check 3: IN subquery
            if (hasInSubquery(statement)) {
                suggestions.add("""
                    🔄 Replace IN subquery with JOIN
                    Reason: JOINs are better optimized by query planners
                    Performance gain: 3-10x faster on large datasets
                """.trimIndent())
            }

            // Check 4: Multiple JOINs without indexes
            if (hasJoins(statement)) {
                suggestions.add("""
                    🔗 Ensure JOIN columns have indexes
                    Reason: Indexes dramatically speed up JOIN operations
                    Check: Use get_table_schema tool to verify indexes exist
                """.trimIndent())
            }

            return if (suggestions.isNotEmpty()) {
                buildString {
                    appendLine("🎯 OPTIMIZATION SUGGESTIONS")
                    appendLine()
                    suggestions.forEachIndexed { index, suggestion ->
                        appendLine("${index + 1}. $suggestion")
                        appendLine()
                    }
                }
            } else {
                "✅ Query looks well-optimized! No obvious improvements needed."
            }
        } catch (e: Exception) {
            return "❌ Error analyzing query: ${e.message}"
        }
    }

    @Tool("generate_challenge")
    @LLMDescription("Creates a SQL practice challenge (beginner, intermediate, or advanced) using the actual database schema with hints. Does NOT reveal the solution.")
    fun generateChallenge(
        @LLMDescription("Difficulty level: 'beginner', 'intermediate', or 'advanced'")
        difficulty: String?
    ): String {
        val tables = nemoryConnector.getAllTables()

        return when (difficulty?.lowercase()) {
            "beginner" -> {
                val table = tables.filter { it.samples?.isNotEmpty() == true }.random()
                val column = table.columns.random()
                val sampleValue = table.samples?.firstOrNull()?.get(column.name)

                """
                🎓 BEGINNER CHALLENGE
                
                📋 Table: ${table.name}
                Columns: ${table.columns.joinToString { it.name }}
                
                🎯 TASK:
                Write a query to find all records from '${table.name}' where 
                ${column.name} equals a specific value.
                
                💡 HINTS:
                1. Use SELECT ... FROM ... WHERE ...
                2. The column '${column.name}' has type: ${column.type}
                3. Example value from sample data: $sampleValue
                
                📝 Sample data to help you:
                ${table.samples?.firstOrNull()}
                
                📚 Concepts tested: SELECT, WHERE, Filtering
                """.trimIndent()
            }

            "intermediate" -> {
                val tablesWithFk = tables.filter { it.foreignKeys?.isNotEmpty() == true }

                if (tablesWithFk.isEmpty()) {
                    return "❌ No foreign key relationships found. Try beginner challenges instead."
                }

                val table = tablesWithFk.random()
                val fk = table.foreignKeys!!.random()
                val refTable = nemoryConnector.getTableByName(fk.referencedTable)!!

                """
                🎓 INTERMEDIATE CHALLENGE
                
                📋 Tables:
                • ${table.name}: ${table.columns.joinToString { it.name }}
                • ${refTable.name}: ${refTable.columns.joinToString { it.name }}
                
                🔗 Relationship:
                ${table.name}.${fk.columnName} → ${refTable.name}.${fk.referencedColumn}
                
                🎯 TASK:
                Write a query that combines data from both tables to show 
                ${table.name} records with their related ${refTable.name} information.
                
                💡 HINTS:
                1. Use a JOIN to combine tables
                2. JOIN ON ${table.name}.${fk.columnName} = ${refTable.name}.${fk.referencedColumn}
                3. Decide: INNER JOIN (only matching) or LEFT JOIN (include all from left)?
                
                📝 Sample data:
                ${table.name}: ${table.samples?.firstOrNull()}
                ${refTable.name}: ${refTable.samples?.firstOrNull()}
                
                📚 Concepts tested: JOIN, Foreign Keys, Table Relationships
                """.trimIndent()
            }

            "advanced" -> {
                val table = tables.filter { it.samples?.isNotEmpty() == true }.random()
                val groupCol = table.columns.firstOrNull {
                    it.type.contains("varchar", ignoreCase = true)
                } ?: table.columns.first()

                """
                🎓 ADVANCED CHALLENGE
                
                📋 Table: ${table.name}
                Columns: ${table.columns.joinToString { "${it.name} (${it.type})" }}
                
                🎯 TASK:
                Write a query that:
                1. Groups ${table.name} by ${groupCol.name}
                2. Counts records in each group
                3. Shows only groups with more than 1 record
                4. Orders results by count (highest first)
                
                💡 HINTS:
                1. Use GROUP BY ${groupCol.name}
                2. Use COUNT(*) to count records
                3. Use HAVING (not WHERE) to filter groups
                4. Use ORDER BY COUNT(*) DESC
                
                📝 Sample data:
                ${table.samples?.take(3)}
                
                📚 Concepts tested: GROUP BY, Aggregate Functions, HAVING, ORDER BY
                """.trimIndent()
            }

            else -> "❌ Invalid difficulty. Use: 'beginner', 'intermediate', or 'advanced'"
        }
    }

    @Tool("generate_challenge_structured")
    @LLMDescription("Creates a SQL practice challenge returning ONLY the question details as JSON. Use 'reveal_challenge_answer' with the returned id to get the solution later.")
    fun generateChallengeStructured(
        @LLMDescription("Difficulty level: 'beginner', 'intermediate', or 'advanced'")
        difficulty: String?
    ): String {
        val id = UUID.randomUUID().toString()
        val tables = nemoryConnector.getAllTables()

        return when (difficulty?.lowercase()) {
            "beginner" -> {
                val table = tables.filter { it.samples?.isNotEmpty() == true }.random()
                val column = table.columns.random()
                val sampleValue = table.samples?.firstOrNull()?.get(column.name)

                val task = "Write a query to find all records from '${table.name}' where ${column.name} equals a specific value."
                val hints = listOf(
                    "Use SELECT ... FROM ... WHERE ...",
                    "The column '${column.name}' has type: ${column.type}",
                    "Example value from sample data: $sampleValue"
                )
                // store solution (handle NULLs and numeric quoting roughly)
                fun isNumericType(t: String): Boolean =
                    t.contains("int", true) || t.contains("decimal", true) || t.contains("number", true) ||
                            t.contains("float", true) || t.contains("double", true) || t.contains("real", true)

                val solution = if (sampleValue == null) {
                    "SELECT * FROM ${table.name} WHERE ${column.name} IS NULL;"
                } else {
                    val valueText = if (isNumericType(column.type)) {
                        sampleValue
                    } else {
                        val escaped = sampleValue.replace("'", "''")
                        "'$escaped'"
                    }
                    "SELECT * FROM ${table.name} WHERE ${column.name} = $valueText;"
                }
                challengeSolutions[id] = solution

                val samples = buildMap<String, String?> {
                    put(table.name, table.samples?.firstOrNull()?.toString())
                }

                Json.encodeToString(
                    ChallengeQuestion(
                        id = id,
                        title = "BEGINNER CHALLENGE",
                        task = task,
                        hints = hints,
                        samples = samples
                    )
                )
            }

            "intermediate" -> {
                val tablesWithFk = tables.filter { it.foreignKeys?.isNotEmpty() == true }
                if (tablesWithFk.isEmpty()) {
                    return "{\"error\":\"No foreign key relationships found. Try beginner challenges instead.\"}"
                }
                val table = tablesWithFk.random()
                val fk = table.foreignKeys!!.random()
                val refTable = nemoryConnector.getTableByName(fk.referencedTable)!!

                val task = "Write a query that combines data from ${table.name} and ${refTable.name} to show related records."
                val hints = listOf(
                    "Use a JOIN to combine tables",
                    "JOIN ON ${table.name}.${fk.columnName} = ${refTable.name}.${fk.referencedColumn}",
                    "Decide: INNER JOIN (only matching) or LEFT JOIN (include all from left)?"
                )
                challengeSolutions[id] = """
                    SELECT 
                        ${table.name}.*,
                        ${refTable.name}.*
                    FROM ${table.name}
                    INNER JOIN ${refTable.name} 
                        ON ${table.name}.${fk.columnName} = ${refTable.name}.${fk.referencedColumn};
                """.trimIndent()

                val samples = buildMap<String, String?> {
                    put(table.name, table.samples?.firstOrNull()?.toString())
                    put(refTable.name, refTable.samples?.firstOrNull()?.toString())
                }

                Json.encodeToString(
                    ChallengeQuestion(
                        id = id,
                        title = "INTERMEDIATE CHALLENGE",
                        task = task,
                        hints = hints,
                        samples = samples
                    )
                )
            }

            "advanced" -> {
                val table = tables.filter { it.samples?.isNotEmpty() == true }.random()
                val groupCol = table.columns.firstOrNull { it.type.contains("varchar", ignoreCase = true) } ?: table.columns.first()
                val task = "Group ${table.name} by ${groupCol.name}, count records, filter count > 1, and order by count desc."
                val hints = listOf(
                    "Use GROUP BY ${groupCol.name}",
                    "Use COUNT(*) and HAVING COUNT(*) > 1",
                    "ORDER BY COUNT(*) DESC"
                )
                challengeSolutions[id] = """
                    SELECT 
                        ${groupCol.name},
                        COUNT(*) as record_count
                    FROM ${table.name}
                    GROUP BY ${groupCol.name}
                    HAVING COUNT(*) > 1
                    ORDER BY record_count DESC;
                """.trimIndent()

                val samples = buildMap<String, String?> {
                    put(table.name, table.samples?.take(3)?.toString())
                }

                Json.encodeToString(
                    ChallengeQuestion(
                        id = id,
                        title = "ADVANCED CHALLENGE",
                        task = task,
                        hints = hints,
                        samples = samples
                    )
                )
            }

            else -> "{\"error\":\"Invalid difficulty. Use: beginner, intermediate, or advanced.\"}"
        }
    }

    @Tool("reveal_challenge_answer")
    @LLMDescription("Reveals the stored solution for a previously generated challenge id. Returns JSON with {id, solution}.")
    fun revealChallengeAnswer(
        @LLMDescription("The challenge id previously returned by generate_challenge_structured") id: String?
    ): String {
        val key = id?.trim().orEmpty()
        if (key.isEmpty()) return "{\"error\":\"Missing id\"}"
        val sol = challengeSolutions[key] ?: return "{\"error\":\"Unknown id or answer not found\"}"
        return Json.encodeToString(ChallengeAnswer(id = key, solution = sol))
    }

    @Tool("validate_challenge_answer")
    @LLMDescription("Validates user's SQL against the stored solution for a challenge id. Returns a short message indicating success or needs improvement. Never reveals the solution.")
    fun validateChallengeAnswer(
        @LLMDescription("The challenge id previously returned by generate_challenge_structured") id: String?,
        @LLMDescription("User's SQL answer to validate") sql: String?
    ): String {
        val key = id?.trim().orEmpty()
        if (key.isEmpty()) return "❌ Missing challenge id. Please generate a challenge first."
        val expected = challengeSolutions[key]
            ?: return "❌ Unknown challenge id. Please generate a new challenge."
        val userSql = sql?.trim().orEmpty()
        if (userSql.isEmpty()) return "❌ Please provide a SQL query to validate."

        fun normalize(s: String): String = s
            .lowercase()
            .replace("\n", " ")
            .replace("\t", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        fun success(): String = "✅ Correct! You solved the challenge."
        fun failure(extra: String? = null): String =
            if (extra.isNullOrBlank()) "❌ Not quite. Keep trying! Hint: compare your tables and clauses against the task requirements."
            else "❌ Not quite. Keep trying! ($extra)"

        // First, try strict normalized match using parser
        try {
            val userParsed = (CCJSqlParserUtil.parse(userSql) as? Select)?.toString() ?: userSql
            val expectedParsed = (CCJSqlParserUtil.parse(expected) as? Select)?.toString() ?: expected
            val ok = normalize(userParsed) == normalize(expectedParsed)
            if (ok) return success()
        } catch (_: Exception) {
            // ignore and proceed to heuristic checks
        }

        // Heuristic acceptance for common variants based on the stored expected template
        val u = normalize(userSql)
        val e = normalize(expected)

        // Beginner pattern: SELECT * FROM <table> WHERE <col> = <value> or IS NULL
        run {
            val regexEq = Regex("from\\s+(\\w+)\\s+where\\s+(\\w+)\\s*=\\s*([^;]+)")
            val regexNull = Regex("from\\s+(\\w+)\\s+where\\s+(\\w+)\\s+is\\s+null")
            val mEq = regexEq.find(e)
            val mNull = regexNull.find(e)
            if (mEq != null || mNull != null) {
                val (table, column) = if (mEq != null) mEq.groupValues.let { it[1] to it[2] } else mNull!!.groupValues.let { it[1] to it[2] }
                val okTable = u.contains(" from $table ") || u.contains(" from $table")
                val okWhereCol = u.contains(" where ") && u.contains(" ${column} ")
                if (okTable && okWhereCol) {
                    // Accept equality or IS NULL, with any select list
                    if (u.contains(" is null") || u.contains(" = ")) return success()
                }
            }
        }

        // Intermediate pattern: JOIN between two specific tables with ON left.right = right.left
        run {
            val joinRegex = Regex("from\\s+(\\w+)\\s+inner\\s+join\\s+(\\w+)\\s+on\\s+(\\w+)\\.(\\w+)\\s*=\\s*(\\w+)\\.(\\w+)")
            val jm = joinRegex.find(e)
            if (jm != null) {
                val leftTable = jm.groupValues[1]
                val rightTable = jm.groupValues[2]
                val leftColTable = jm.groupValues[3]
                val leftCol = jm.groupValues[4]
                val rightColTable = jm.groupValues[5]
                val rightCol = jm.groupValues[6]
                val okTables = u.contains(" from $leftTable ") && u.contains(" join $rightTable ")
                val okJoin = u.contains(" on ${leftColTable}.${leftCol} = ${rightColTable}.${rightCol}") ||
                        u.contains(" on ${rightColTable}.${rightCol} = ${leftColTable}.${leftCol}")
                val hasJoinKeyword = u.contains(" join ")
                if (okTables && okJoin && hasJoinKeyword) return success()
            }
        }

        // Advanced pattern: GROUP BY <col> HAVING COUNT(*) > 1 ORDER BY ... DESC against a table
        run {
            val fromRegex = Regex("from\\s+(\\w+)")
            val groupRegex = Regex("group\\s+by\\s+(\\w+)")
            if (fromRegex.containsMatchIn(e) && groupRegex.containsMatchIn(e)) {
                val table = fromRegex.find(e)!!.groupValues[1]
                val groupCol = groupRegex.find(e)!!.groupValues[1]
                val okFrom = u.contains(" from $table ") || u.contains(" from $table")
                val okGroup = u.contains(" group by $groupCol ")
                val okHaving = u.contains(" having ") && (u.contains(" count(*) ") || u.contains(" count ( * ) "))
                val okOrder = u.contains(" order by ") && u.contains(" desc")
                if (okFrom && okGroup && okHaving && okOrder) return success()
            }
        }

        // As last resort, whitespace-insensitive comparison without parsing
        if (normalize(userSql) == normalize(expected)) return success()

        return failure()
    }

    // Helper functions (simplified implementations)
    private fun hasJoins(statement: Select): Boolean =
        statement.toString().contains("JOIN", ignoreCase = true)

    private fun hasGroupBy(statement: Select): Boolean =
        statement.toString().contains("GROUP BY", ignoreCase = true)

    private fun hasSubqueries(statement: Select): Boolean =
        statement.toString().count { it == '(' } > 1

    private fun hasWindowFunctions(statement: Select): Boolean =
        statement.toString().contains("OVER(", ignoreCase = true)

    private fun hasSelectAll(statement: Select): Boolean =
        statement.toString().contains("SELECT *")

    private fun hasWhereClause(statement: Select): Boolean =
        statement.toString().contains(" WHERE ", ignoreCase = true)

    private fun hasInSubquery(statement: Select): Boolean =
        statement.toString().contains(" IN (", ignoreCase = true)

    private fun extractTableNames(statement: Select): List<String> {
        // Simplified extraction
        val sql = statement.toString()
        val fromPart = sql.substringAfter("FROM ", "").substringBefore(" WHERE")
            .substringBefore(" GROUP").substringBefore(" ORDER")
        return fromPart.split(",", "JOIN")
            .map { it.trim().split(" ").first().trim() }
            .filter { it.isNotEmpty() }
    }
}