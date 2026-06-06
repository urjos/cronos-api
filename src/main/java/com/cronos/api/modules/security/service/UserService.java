package com.cronos.api.modules.security.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.cronos.api.modules.security.model.TokenResponse;
import com.cronos.api.modules.security.model.User;
import com.cronos.api.modules.security.model.UserCreateRequest;
import com.cronos.api.modules.security.model.UserLoginRequest;
import com.cronos.api.modules.security.model.UserResponse;
import com.cronos.api.modules.security.model.UserStatus;
import com.cronos.api.modules.security.repository.UserRepository;
import com.cronos.api.modules.workspace.model.WorkspaceCreateRequest;
import com.cronos.api.modules.workspace.service.WorkspaceService;
import java.util.Date;

public class UserService {

    private final UserRepository userRepository;
    private final WorkspaceService workspaceService;
    private final String jwtSecret;

    // Inyección de dependencia: El servicio necesita el repositorio para consultar/guardar
    public UserService(UserRepository userRepository, WorkspaceService workspaceService, String jwtSecret) {
        this.userRepository = userRepository;
        this.workspaceService = workspaceService;
        this.jwtSecret = jwtSecret;
    }

    /**
     * Registra un nuevo usuario aplicando las reglas de negocio y seguridad.
     * @param request DTO con los datos limpios enviados por el cliente.
     * @return DTO UserResponse sin información sensible.
     */
    public UserResponse registerUser(UserCreateRequest request) {
        
        // 1. Validaciones de Negocio: Evitar duplicidad
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("El nombre de usuario ya está en uso.");
        }
        
        if (request.getEmail() != null && userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("El correo electrónico ya está registrado.");
        }
        
        // Validación básica de campos vacíos
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("La contraseña no puede estar vacía.");
        }

        // 2. Encriptación (Hashing) de la contraseña
        // Usamos un factor de costo de 12 (recomendado actualmente para equilibrar seguridad y rendimiento)
        String bcryptHashString = BCrypt.withDefaults().hashToString(12, request.getPassword().toCharArray());

        // 3. Mapeo del Request al Modelo (Entidad)
        User newUser = new User();
        newUser.setUsername(request.getUsername());
        newUser.setEmail(request.getEmail());
        newUser.setPwdHash(bcryptHashString);
        newUser.setStatus(UserStatus.ACTIVE); // Por defecto un usuario nuevo nace activo

        // 4. Persistencia en MySQL
        User savedUser = userRepository.save(newUser);
        
        WorkspaceCreateRequest defaultWorkspace = new WorkspaceCreateRequest();
        defaultWorkspace.setName("Espacio de " + savedUser.getUsername());
        defaultWorkspace.setDescription("Espacio de trabajo personal autogenerado.");
        
        workspaceService.createWorkspace(defaultWorkspace, savedUser.getId());

        // 5. Retornar la respuesta segura (El DTO omite el pwdHash automáticamente)
        return new UserResponse(savedUser);
    }
    
    /**
     * Valida credenciales y genera un JWT si son correctas.
     */
    public TokenResponse login(UserLoginRequest request) {
        
        // 1. Buscar al usuario
        // Si no existe, lanzamos un error genérico por seguridad (para no revelar si el usuario existe o no)
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Credenciales inválidas."));

        // 2. Verificar la contraseña
        // BCrypt extrae la "sal" del hash guardado y comprueba si la contraseña plana coincide
        BCrypt.Result result = BCrypt.verifyer().verify(request.getPassword().toCharArray(), user.getPwdHash());

        if (!result.verified) {
            throw new IllegalArgumentException("Credenciales inválidas.");
        }

        // 3. Generar el JWT
        Algorithm algorithm = Algorithm.HMAC256(jwtSecret);
        
        // El token expirará en 24 horas por seguridad
        long expirationTimeMs = System.currentTimeMillis() + (24 * 60 * 60 * 1000); 

        String token = JWT.create()
                .withIssuer("cronos-api") // Quién emite el token
                .withSubject(String.valueOf(user.getId())) // El ID del usuario es el "sujeto" del token
                .withClaim("username", user.getUsername()) // Información útil extra
                .withExpiresAt(new Date(expirationTimeMs)) 
                .sign(algorithm);

        return new TokenResponse(token);
    }
}