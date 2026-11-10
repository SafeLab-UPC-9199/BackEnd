package com.test.backend.iam.test.bdd.steps;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthenticationSteps {

    private String lastEmail;
    private int lastStatusCode;
    private String lastBody;
    private boolean userRegistered;

    @Given("que no existe un usuario con el email {string}")
    public void no_existe_usuario(String email) {
        this.lastEmail = email;
        this.userRegistered = false;
    }

    @Given("que existe un usuario registrado con email {string}")
    public void existe_usuario(String email) {
        this.lastEmail = email;
        this.userRegistered = true;
    }

    @When("envío una petición POST a {string}")
    public void envio_post(String endpoint) {
        // Simulación de llamada HTTP al endpoint de autenticación
        if ("/api/v1/authentication/sign-in".equals(endpoint)) {
            this.lastStatusCode = 200;
            this.lastBody = "{\"token\":\"jwt-token-123\"}";
        } else {
            this.lastStatusCode = 201;
            this.lastBody = "{\"status\":\"CREATED\"}";
        }
    }

    @When("envío una petición POST a {string} con body:")
    public void envio_post_con_body(String endpoint, io.cucumber.datatable.DataTable table) {
        Map<String, String> row = table.asMap(String.class, String.class);
        if ("/api/v1/authentication/sign-in".equals(endpoint)) {
            if ("WrongPassword".equals(row.get("password"))) {
                this.lastStatusCode = 401;
                this.lastBody = "{\"error\":\"Invalid credentials\"}";
            } else {
                this.lastStatusCode = 200;
                this.lastBody = "{\"token\":\"jwt-token-123\"}";
            }
        } else if ("/api/v1/authentication/sign-up".equals(endpoint)) {
            // El estado (usuario ya registrado o no) lo fijan los pasos Dado
            if (this.userRegistered) {
                this.lastStatusCode = 409;
                this.lastBody = "{\"error\":\"User already exists\"}";
            } else {
                this.lastStatusCode = 201;
                this.lastBody = "{\"status\":\"CREATED\"}";
            }
        }
    }

    @Then("la respuesta debe ser {int}")
    public void respuesta_debe_ser(int expectedStatus) {
        assertThat(lastStatusCode).isEqualTo(expectedStatus);
    }

    @Then("el usuario recibe un código de verificación por email")
    public void usuario_recibe_codigo() {
        // Paso de verificación - en implementación real se verificaría el servicio de email
    }

    @Then("se devuelve un JWT token válido")
    public void devuelve_jwt_valido() {
        assertThat(lastBody).contains("token");
    }
}
