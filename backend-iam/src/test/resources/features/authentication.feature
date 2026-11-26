# language: es
@iam @authentication
Característica: Autenticación y autorización (US04 - Acceso al sistema)

  Como usuario del sistema SafeLab
  Quiero registrarme y autenticarme
  Para acceder a las funcionalidades del laboratorio

  Escenario: Registro exitoso de nuevo usuario
    Dado que no existe un usuario con el email "operador@safelab.edu.pe"
    Cuando envío una petición POST a "/api/v1/authentication/sign-up" con body:
      | email    | operador@safelab.edu.pe |
      | password | SecurePass123!          |
      | fullName | Operador Laboratorio    |
    Entonces la respuesta debe ser 201
    Y el usuario recibe un código de verificación por email

  Escenario: Inicio de sesión exitoso
    Dado que existe un usuario registrado con email "operador@safelab.edu.pe"
    Cuando envío una petición POST a "/api/v1/authentication/sign-in" con body:
      | email    | operador@safelab.edu.pe |
      | password | SecurePass123!          |
    Entonces la respuesta debe ser 200
    Y se devuelve un JWT token válido

  Escenario: Rechazo por credenciales inválidas
    Dado que existe un usuario registrado con email "operador@safelab.edu.pe"
    Cuando envío una petición POST a "/api/v1/authentication/sign-in" con body:
      | email    | operador@safelab.edu.pe |
      | password | WrongPassword           |
    Entonces la respuesta debe ser 401

  Escenario: Registro duplicado debe fallar
    Dado que existe un usuario registrado con email "operador@safelab.edu.pe"
    Cuando envío una petición POST a "/api/v1/authentication/sign-up" con body:
      | email    | operador@safelab.edu.pe |
      | password | SecurePass123!          |
      | fullName | Operador Laboratorio    |
    Entonces la respuesta debe ser 409

  Escenario: Desafío 2FA cuando el usuario tiene TOTP habilitado
    Dado que existe un usuario registrado con email "admin@safelab.edu.pe" con 2FA habilitado
    Cuando envío una petición POST a "/api/v1/authentication/sign-in" con body:
      | email    | admin@safelab.edu.pe |
      | password | SecurePass123!       |
    Entonces la respuesta debe ser 200
    Y se solicita verificación de segundo factor

  Escenario: Bloqueo temporal tras cinco intentos fallidos
    Dado que existe un usuario registrado con email "bloqueado@safelab.edu.pe"
    Cuando fallo el inicio de sesión 5 veces para "bloqueado@safelab.edu.pe"
    Y envío una petición POST a "/api/v1/authentication/sign-in" con body:
      | email    | bloqueado@safelab.edu.pe |
      | password | SecurePass123!           |
    Entonces la respuesta debe ser 423
