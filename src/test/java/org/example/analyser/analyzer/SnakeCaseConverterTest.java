package org.example.analyser.analyzer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SnakeCaseConverterTest {

    @Test
    void convertsPlainCamelCase() {
        assertThat(SnakeCaseConverter.toSnakeCase("OrderEntity"))
                .isEqualTo("order_entity");
    }

    @Test
    void handlesLeadingAcronym() {
        assertThat(SnakeCaseConverter.toSnakeCase("HTTPOrderEntity"))
                .isEqualTo("http_order_entity");
    }

    @Test
    void handlesTrailingAcronym() {
        assertThat(SnakeCaseConverter.toSnakeCase("URLMapping"))
                .isEqualTo("url_mapping");
    }

    @Test
    void handlesSingleWord() {
        assertThat(SnakeCaseConverter.toSnakeCase("Order"))
                .isEqualTo("order");
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(SnakeCaseConverter.toSnakeCase(null)).isNull();
        assertThat(SnakeCaseConverter.toSnakeCase("")).isEqualTo("");
    }
}
