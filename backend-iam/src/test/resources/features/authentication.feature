@iam @authentication
Funcionalidad: Autenticación y autorización (US04 - Acceso al sistema)

  Como usuario del sistema SafeLab
  Quiero registrarme y autenticarme
  Para acceder a las funcionalidades del laboratorio

  Escenario: Registro exitoso de nuevo usuario
    Dado que no existe un usuario con el email "operador@safelab.edu.pe"
    Cuando envío una petición POST a "/api/v1/authentication/sign-up"
      Con body:
        | email       | operador@safelab.edu.pe |
        | password    | SecurePass123! |
        | fullName    | Operador Laboratorio |
    Entonces la respuesta debe ser 201
    Y el usuario recibe un código de verificación por email

  Escenario: Inicio de sesión exitoso
    Dado que existe un usuario registrado con email "operador@safelab.edu.pe"
    Cuando envío una petición POST a "/api/v1/authentication/sign-in"
      Con body:
        | email    | operador@safelab.edu.pe |
        | password | SecurePass123! |
    Entonces la respuesta debe ser 200
    Y se devuelve un JWT token válido

  Escenario: Rechazo por credenciales inválidas
    Dado que envío una petición POST a "/api/v1/authentication/sign-in"
      Con body:
        | email    | operador@safelab.edu.pe |
        | password | WrongPassword |
    Entonces la respuesta debe ser 401

  Escenario: Registro duplicado debe fallar
    Dado que ya existe un usuario con email "operador@safelab.edu.pe"
    Cuando envío una petición POST a "/api/v1/authentication/sign-up"
      Con body:
        | email       | operador@safelab.edu.pe |
        | password    | SecurePass123! |
        | fullName    | Operador Laboratorio |
    Entonces la respuesta debe ser 409
