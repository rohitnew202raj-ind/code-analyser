package org.example.analyser.model;

/**
 * What triggers a single {@link EntryPointInfo} - previously a
 * raw {@code String} field, which meant "entry point kind" was
 * whatever string literal each analyzer happened to write, with
 * no compiler check that a new analyzer's trigger label actually
 * matched an existing one. A real enum makes every possible
 * execution model explicit in one place instead of scattered
 * string literals across {@code ApiAnalyzer}/{@code BatchAnalyzer}.
 */
public enum TriggerType {

    // ==========================================
    // REST / MVC
    // ==========================================

    GET,
    POST,
    PUT,
    PATCH,
    DELETE,

    /**
     * A bare {@code @RequestMapping} with no HTTP method
     * restriction specified - reachable via any HTTP verb.
     */
    ANY,

    // ==========================================
    // GRAPHQL
    // ==========================================

    GRAPHQL_QUERY,
    GRAPHQL_MUTATION,
    GRAPHQL_SUBSCRIPTION,
    GRAPHQL_SCHEMA_MAPPING,

    // ==========================================
    // BATCH / EVENT-DRIVEN / STARTUP
    //
    // Each of these is a genuinely different execution model
    // (a cron-style schedule, a message-queue consumer, a
    // startup hook, ...) even though earlier versions of this
    // tool lumped several of them under one generic "batch"
    // label - see LIMITATIONS.md.
    // ==========================================

    SCHEDULED,
    ASYNC,
    EVENT_LISTENER,
    KAFKA_CONSUMER,
    JMS_CONSUMER,
    RABBIT_CONSUMER,
    SPRING_BATCH_STEP_COMPONENT,
    STARTUP_RUNNER,
    MAIN_ENTRY_POINT
}
