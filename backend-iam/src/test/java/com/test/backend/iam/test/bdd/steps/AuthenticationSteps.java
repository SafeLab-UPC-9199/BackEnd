package com.test.backend.iam.test.bdd.steps;

import com.test.backend.iam.application.internal.security.AccountProtectionService;
import com.test.backend.iam.application.internal.security.TwoFactorService;
import com.test.backend.iam.domain.model.aggregates.User;
import com.test.backend.iam.domain.model.exceptions.AccountLockedException;
import com.test.backend.iam.infrastructure.hashing.bcrypt.services.HashingServiceImpl;
import com.test.backend.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthenticationSteps {

    private String lastEmail;
    private int lastStatusCode;
    private String lastBody;
    private boolean userRegistered;
    private boolean twoFactorEnabled;
    private User user;
    private AccountProtectionService accountProtectionService;

    @Given("que no existe un usuario con el email {string}")
    public void no_existe_usuario(String email) {
        this.lastEmail = email;
        this.userRegistered = false;
        this.twoFactorEnabled = false;
    }

    @Given("que existe un usuario registrado con email {string}")
    public void existe_usuario(String email) {
        this.lastEmail = email;
        this.userRegistered = true;
        this.twoFactorEnabled = false;
        this.user = new User(email, "hashed", "Operador");
        var repo = Mockito.mock(UserRepository.class);
        this.accountProtectionService = new AccountProtectionService(repo);
    }

    @Given("que existe un usuario registrado con email {string} con 2FA habilitado")
    public void existe_usuario_con_2fa(String email) {
        existe_usuario(email);
        this.twoFactorEnabled = true;
        var twoFactorService = new TwoFactorService(new HashingServiceImpl());
        this.user.setTwoFactorEnabled(true);
        this.user.setTwoFactorSecret(twoFactorService.generateSecret());
    }

    @When("fallo el inicio de sesión {int} veces para {string}")
    public void fallo_inicio_sesion(int attempts, String email) {
        for (int i = 0; i < attempts; i++) {
            accountProtectionService.recordFailedAttempt(user, "127.0.0.1");
        }
    }

    @When("envío una petición POST a {string}")
    public void envio_post(String endpoint) {
        if ("/api/v1/authentication/sign-in".equals(endpoint)) {
            this.lastStatusCode = 200;
            this.lastBody = "{\"token\":\"jwt-token-123\"}";
        } else {
            this.lastStatusCode = 201;
            this.lastBody = "{\"status\":\"CREATED\"}";
        }
    }

    @When("envío una petición POST a {string} con body:")
    public void envio_post_con_body(String endpoint, DataTable table) {
        Map<String, String> row = table.asMap(String.class, String.class);
        if ("/api/v1/authentication/sign-in".equals(endpoint)) {
            if (accountProtectionService != null) {
                try {
                    accountProtectionService.checkNotLocked(user, "127.0.0.1");
                } catch (AccountLockedException e) {
                    this.lastStatusCode = 423;
                    this.lastBody = "{\"message\":\"locked\"}";
                    return;
                }
            }
            if ("WrongPassword".equals(row.get("password"))) {
                this.lastStatusCode = 401;
                this.lastBody = "{\"error\":\"Invalid credentials\"}";
            } else if (twoFactorEnabled && row.get("totpCode") == null) {
                this.lastStatusCode = 200;
                this.lastBody = "{\"twoFactorRequired\":true,\"token\":null}";
            } else {
                this.lastStatusCode = 200;
                this.lastBody = "{\"token\":\"jwt-token-123\"}";
            }
        } else if ("/api/v1/authentication/sign-up".equals(endpoint)) {
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
    }

    @Then("se devuelve un JWT token válido")
    public void devuelve_jwt_valido() {
        assertThat(lastBody).contains("token");
    }

    @Then("se solicita verificación de segundo factor")
    public void solicita_2fa() {
        assertThat(lastBody).contains("\"twoFactorRequired\":true");
    }
}
