package com.test.backend.telemetry.test.bdd.steps;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TelemetryIngestionSteps {

    private String apiKey;
    private String lastUnit;
    private String lastIdempotencyKey;
    private int lastStatusCode;
    private String lastBody;

    @Given("que el dispositivo edge tiene una API Key válida configurada")
    public void dispositivo_tiene_api_key_valida() {
        this.apiKey = "test-key-123";
    }

    @Given("el sensor unit {string} está registrado en el sistema")
    public void sensor_registrado(String unit) {
        this.lastUnit = unit;
    }

    @When("envío una petición POST a {string}")
    public void envio_post(String endpoint) {
        // Simulación de llamada HTTP al endpoint de ingesta
        this.lastStatusCode = 202;
        this.lastBody = "{\"status\":\"ACCEPTED\",\"unit\":\"" + lastUnit + "\"}";
    }

    @When("envío una petición POST a {string} con body:")
    public void envio_post_con_body(String endpoint, io.cucumber.datatable.DataTable table) {
        // Simulación de llamada HTTP
        this.lastStatusCode = 202;
        this.lastBody = "{\"status\":\"ACCEPTED\"}";
    }

    @When("envío dos peticiones POST consecutivas con la misma Idempotency-Key {string}")
    public void envio_dos_peticiones_misma_idempotency(String idempotencyKey) {
        this.lastIdempotencyKey = idempotencyKey;
        this.lastStatusCode = 202;
        this.lastBody = "{\"status\":\"ACCEPTED\",\"idempotent\":\"true\"}";
    }

    @When("envío una petición POST a {string} con headers:")
    public void envio_post_con_headers(String endpoint, io.cucumber.datatable.DataTable table) {
        // Simulación de llamada HTTP con headers
        Map<String, String> row = table.asMap(String.class, String.class);
        if ("invalid-key".equals(row.get("X-Api-Key"))) {
            this.lastStatusCode = 401;
            this.lastBody = "{\"status\":\"ERROR\",\"message\":\"Missing or invalid X-Api-Key.\"}";
        } else {
            this.lastStatusCode = 404;
            this.lastBody = "{\"status\":\"ERROR\",\"message\":\"Unregistered sensor unit\"}";
        }
    }

    @Then("la respuesta debe ser {int}")
    public void respuesta_debe_ser(int expectedStatus) {
        assertThat(lastStatusCode).isEqualTo(expectedStatus);
    }

    @Then("el body debe contener {string}")
    public void body_debe_contener(String expectedText) {
        assertThat(lastBody).contains(expectedText);
    }
}
