package ai.koog.workshop.sqltool

import ai.jetbrains.code.prompt.llm.JetBrainsAIModels
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.prompt.dsl.prompt
import ai.koog.workshop.sqltool.integration.NemoryConnector
import ai.koog.workshop.sqltool.tools.QueryTutorTools
import ai.koog.workshop.intro.utils.installDefaultEventHandler
import ai.koog.workshop.intro.utils.simpleGraziePromptExecutor
import kotlinx.coroutines.runBlocking
/*
this is just an example, playground how to set up an agent with a more complex strategy (using subgraphs)
 */
data class QueryRequest(
    val query: String,
    val action: String = "explain" // explain, optimize, analyze, challenge
)

fun main() = runBlocking {
    println("🚀 Starting SQL Query Tutor with Nemory + Grazie\n")

    // 1. Get Grazie token
    val token = System.getenv("GRAZIE_TOKEN")
        ?: error("GRAZIE_TOKEN is required. Set it with: export GRAZIE_TOKEN='your-token'")

    // 2. Get Nemory schema path
    val nemoryPath = System.getenv("NEMORY_SCHEMA_PATH")
        ?: "nemory-schema.yaml"

    println("✅ Grazie token found")
    println("✅ Using Nemory schema: $nemoryPath\n")

    // 3. Initialize Nemory connector
    val nemoryConnector = NemoryConnector(nemoryPath)
    println("✅ Nemory connector initialized")
    println("   Database: ${nemoryConnector.getDatabaseInfo().databaseId}")
    println("   Tables: ${nemoryConnector.getAllTables().joinToString { it.name }}\n")

    // 4. Create tools
    val tutorTools = QueryTutorTools(nemoryConnector)
    println("✅ Tools initialized\n")

    // 5. Create the AI Agent
    val agent = AIAgent(
        promptExecutor = simpleGraziePromptExecutor(token),

        toolRegistry = ToolRegistry {
            tools(tutorTools.asTools())
        },

        agentConfig = AIAgentConfig(
            prompt = prompt("sql-tutor-prompt") {
                system("""
                    You are a friendly SQL tutor helping developers learn and improve their queries.
                    
                    Database: ${nemoryConnector.getDatabaseInfo().databaseId}
                    Available tables: ${nemoryConnector.getAllTables().joinToString { it.name }}
                    
                    Your teaching approach:
                    1. Use analyze_query to understand query structure and detect issues
                    2. Use get_table_schema to reference actual schema details
                    3. Use suggest_optimizations to find performance improvements
                    4. Explain in plain English with concrete examples from sample data
                    5. Be encouraging and patient! 🎓
                    
                    Use emojis for readability:
                    🎯 for goals
                    💡 for tips
                    ⚡ for performance
                    🔗 for relationships
                    📊 for data insights
                """.trimIndent())
            },

            model = JetBrainsAIModels.OpenAI_GPT4_1_via_JBAI,
            maxAgentIterations = 20,
        ),

        strategy = strategy<QueryRequest, String>("sql-tutor-strategy") {

            // Step 1: Analyze the query
            val analyzeSubgraph by subgraphWithTask<QueryRequest, QueryRequest>(
                toolSelectionStrategy = ToolSelectionStrategy.ALL
            ) { request ->
                "First, analyze this SQL query for complexity, concepts used, and potential issues: ${request.query}"
            }

            // Step 2: Get schema information
            val schemaSubgraph by subgraphWithTask<QueryRequest, QueryRequest>(
                toolSelectionStrategy = ToolSelectionStrategy.ALL
            ) { request ->
                "Get the detailed schema information for all tables mentioned in the query analysis"
            }

            // Step 3: Generate final response based on action
            val responseNode by node<QueryRequest, String> { request ->
                when (request.action) {
                    "explain" ->
                        "Now explain this query in plain English step-by-step: ${request.query}. " +
                                "Use the analysis results and schema details. Give concrete examples using sample data."

                    "optimize" ->
                        "Suggest specific optimizations for this query: ${request.query}. " +
                                "Use the analysis to identify issues and suggest concrete improvements."

                    "analyze" ->
                        "Provide a detailed analysis of: ${request.query}. " +
                                "Include complexity, concepts, and potential issues with explanations."

                    else ->
                        "Help with this SQL query: ${request.query}"
                }
            }

            // Define the flow
            edge(nodeStart forwardTo analyzeSubgraph)
            edge(analyzeSubgraph forwardTo schemaSubgraph)
            edge(schemaSubgraph forwardTo responseNode)
            edge(responseNode forwardTo nodeFinish onAssistantMessage { true })
        }
    ) {
        // Install default event handler for debugging
        installDefaultEventHandler()
    }

    println("✅ Agent created successfully\n")
    println("=".repeat(70))

    // 6. Test with aircraft query
    val testQuery = """
        SELECT a.registration, a.status, a.manufacture_date
        FROM aircraft a
        WHERE a.status = 'Active'
        ORDER BY a.manufacture_date DESC
    """.trimIndent()

    println("\n📝 Testing with query:")
    println(testQuery)
    println("\n💭 Agent is thinking...\n")

    val result = agent.run(QueryRequest(
        query = testQuery,
        action = "explain"
    ))

    println("\n🤖 Tutor's Response:")
    println(result)
    println("\n" + "=".repeat(70) + "\n" )
}