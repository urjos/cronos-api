package com.cronos.api.core;

import com.cronos.api.modules.project.controller.ProjectController;
import com.cronos.api.modules.project.repository.ProjectRepository;
import com.cronos.api.modules.project.service.ProjectService;
import com.cronos.api.modules.report.controller.ReportController;
import com.cronos.api.modules.report.repository.ReportRepository;
import com.cronos.api.modules.report.service.ReportService;
import com.cronos.api.modules.security.controller.UserController;
import com.cronos.api.modules.security.middleware.AuthMiddleware;
import com.cronos.api.modules.security.repository.UserRepository;
import com.cronos.api.modules.security.service.UserService;
import com.cronos.api.modules.tag.controller.TagController;
import com.cronos.api.modules.tag.repository.TagRepository;
import com.cronos.api.modules.tag.service.TagService;
import com.cronos.api.modules.task.controller.TaskController;
import com.cronos.api.modules.task.repository.TaskRepository;
import com.cronos.api.modules.task.service.TaskService;
import com.cronos.api.modules.time.controller.TimeEntryController;
import com.cronos.api.modules.time.repository.TimeEntryRepository;
import com.cronos.api.modules.time.service.TimeEntryService;
import com.cronos.api.modules.workspace.controller.WorkspaceController;
import com.cronos.api.modules.workspace.repository.WorkspaceRepository;
import com.cronos.api.modules.workspace.service.WorkspaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.cdimascio.dotenv.Dotenv;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orquestador principal de la aplicación.
 * Configura el contenedor de dependencias, la base de datos y el servidor web.
 */
public class Bootstrap {

    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);
    private final ServiceLocator locator;
    private Javalin app;

    public Bootstrap() {
        this.locator = new ServiceLocator();
    }

    public void start() {
        log.info("Iniciando secuencia de arranque de CRONOS API...");

        // 1. Cargar Variables de Entorno y registrarlas
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        locator.register(Dotenv.class, dotenv);

        // 2. Inicializar y registrar la Base de Datos
        DatabaseManager dbManager = new DatabaseManager(dotenv);
        locator.register(DatabaseManager.class, dbManager);

        // 3. Inicializar y registrar el Módulo de Seguridad
        log.info("Inicializando módulos de negocio...");
        
        // Extraemos el secreto
        String jwtSecret = dotenv.get("JWT_SECRET", "default_secret_local_jeje_la_beba_2019_daaa");
        
        UserRepository userRepository = new UserRepository(dbManager);
        
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(dbManager);
        WorkspaceService workspaceService = new WorkspaceService(workspaceRepository, userRepository);
        WorkspaceController workspaceController = new WorkspaceController(workspaceService);
        
        UserService userService = new UserService(userRepository, workspaceService, jwtSecret);
        UserController userController = new UserController(userService);
        
        ProjectRepository projectRepository = new ProjectRepository(dbManager);
        ProjectService projectService = new ProjectService(projectRepository, workspaceRepository);
        ProjectController projectController = new ProjectController(projectService);
        
        TaskRepository taskRepository = new TaskRepository(dbManager);
        TaskService taskService = new TaskService(taskRepository, projectRepository, workspaceRepository);
        TaskController taskController = new TaskController(taskService);
        
        TagRepository tagRepository = new TagRepository(dbManager);
        TagService tagService = new TagService(tagRepository, workspaceRepository);
        TagController tagController = new TagController(tagService);
        
        TimeEntryRepository timeEntryRepository = new TimeEntryRepository(dbManager);
        TimeEntryService timeEntryService = new TimeEntryService(timeEntryRepository, workspaceRepository);
        TimeEntryController timeEntryController = new TimeEntryController(timeEntryService);
        
        ReportRepository reportRepository = new ReportRepository(dbManager);
        ReportService reportService = new ReportService(reportRepository, workspaceRepository);
        ReportController reportController = new ReportController(reportService);
        
        AuthMiddleware authMiddleware = new AuthMiddleware(jwtSecret);
        
        locator.register(UserRepository.class, userRepository);
        locator.register(UserService.class, userService);
        locator.register(UserController.class, userController);
        
        locator.register(WorkspaceRepository.class, workspaceRepository);
        locator.register(WorkspaceService.class, workspaceService);
        locator.register(WorkspaceController.class, workspaceController);
        
        locator.register(ProjectRepository.class, projectRepository);
        locator.register(ProjectService.class, projectService);
        locator.register(ProjectController.class, projectController);
        
        locator.register(TaskRepository.class, taskRepository);
        locator.register(TaskService.class, taskService);
        locator.register(TaskController.class, taskController);
        
        locator.register(TagRepository.class, tagRepository);
        locator.register(TagService.class, tagService);
        locator.register(TagController.class, tagController);
        
        locator.register(TimeEntryRepository.class, timeEntryRepository);
        locator.register(TimeEntryService.class, timeEntryService);
        locator.register(TimeEntryController.class, timeEntryController);
        
        locator.register(ReportRepository.class, reportRepository);
        locator.register(ReportService.class, reportService);
        locator.register(ReportController.class, reportController);
        
        locator.register(AuthMiddleware.class, authMiddleware);

        // 4. Configurar Servidor Javalin
        int port = Integer.parseInt(dotenv.get("API_PORT", "8080"));
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        String corsOriginsRaw = dotenv.get("CORS_ALLOWED_ORIGINS", "*");
        List<String> corsAllowedOrigins = Arrays.stream(corsOriginsRaw.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toList());
        if (corsAllowedOrigins.isEmpty()) {
            corsAllowedOrigins = List.of("*");
        }
        final List<String> finalCorsAllowedOrigins = corsAllowedOrigins;

        app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(mapper));
            config.http.defaultContentType = "application/json";
            config.plugins.enableCors(cors -> cors.add(it -> {
                if (finalCorsAllowedOrigins.size() == 1 && "*".equals(finalCorsAllowedOrigins.get(0))) {
                    it.anyHost();
                } else {
                    finalCorsAllowedOrigins.forEach(it::allowHost);
                }
            }));
        }).start(port);
        log.info("CORS habilitado para orígenes: {}", finalCorsAllowedOrigins);

        // Registrar la instancia de Javalin por si algún módulo la necesita
        locator.register(Javalin.class, app);

        // 5. Configurar Rutas y Manejo Global de Errores
        setupBaseRoutes();
        setupSecurityRoutes();
        setupWorkspaceRoutes();
        setupGlobalExceptionHandling();

        // 6. Configurar Apagado Seguro (Graceful Shutdown)
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

        log.info("✅ CRONOS API está listo y escuchando en el puerto {}", port);
    }

    /**
     * Define rutas de prueba o de estado del servidor (Health Check).
     */
    private void setupBaseRoutes() {
        app.get("/", ctx -> ctx.json(Map.of(
            "status", "online",
            "service", "CRONOS API",
            "version", "1.0"
        )));
        
        AuthMiddleware authMiddleware = locator.get(AuthMiddleware.class);
        
        app.before("/api/workspaces", authMiddleware);
        app.before("/api/workspaces/*", authMiddleware);
    }

    /**
     * Registra las rutas del Módulo de Seguridad.
     */
    private void setupSecurityRoutes() {
        UserController userController = locator.get(UserController.class);
        
        app.post("/api/users/register", userController::register);
        app.post("/api/users/login", userController::login);
    }
    
    /**
     * Registra las rutas del Módulo de Workspaces.
     */
    private void setupWorkspaceRoutes() {
        WorkspaceController workspaceController = locator.get(WorkspaceController.class);
        ProjectController projectController = locator.get(ProjectController.class);
        TaskController taskController = locator.get(TaskController.class);
        TagController tagController = locator.get(TagController.class);
        TimeEntryController timeEntryController = locator.get(TimeEntryController.class);
        ReportController reportController = locator.get(ReportController.class);
        
        app.post("/api/workspaces", workspaceController::create);
        app.post("/api/workspaces/{workspaceId}/members", workspaceController::inviteMember);
        app.get("/api/workspaces/{workspaceId}/members", workspaceController::getMembers);
        app.get("/api/workspaces", workspaceController::getAll);
        
        app.post("/api/workspaces/{workspaceId}/projects", projectController::create);
        app.get("/api/workspaces/{workspaceId}/projects", projectController::getAllByWorkspace);
        
        app.post("/api/workspaces/{workspaceId}/projects/{projectId}/tasks", taskController::create);
        app.get("/api/workspaces/{workspaceId}/projects/{projectId}/tasks", taskController::getAllByProject);
    
        app.post("/api/workspaces/{workspaceId}/tags", tagController::create);
        app.get("/api/workspaces/{workspaceId}/tags", tagController::getAllByWorkspace);
        
        app.post("/api/workspaces/{workspaceId}/time-entries/start", timeEntryController::start);
        app.put("/api/workspaces/{workspaceId}/time-entries/{id}/stop", timeEntryController::stop);
        app.get("/api/workspaces/{workspaceId}/time-entries", timeEntryController::getAll);
        app.post("/api/workspaces/{workspaceId}/time-entries/manual", timeEntryController::createManual);
        app.put("/api/workspaces/{workspaceId}/time-entries/{id}", timeEntryController::update);
        app.delete("/api/workspaces/{workspaceId}/time-entries/{id}", timeEntryController::delete);
        
        app.get("/api/workspaces/{workspaceId}/reports/summary", reportController::getSummary);
    }

    /**
     * Atrapa cualquier error no controlado para evitar que el API devuelva HTML o revele código.
     */
    private void setupGlobalExceptionHandling() {
        app.exception(Exception.class, (e, ctx) -> {
            log.error("Error crítico no manejado en la petición", e);
            ctx.status(500).json(Map.of(
                "error", "Error interno del servidor",
                "message", e.getMessage() != null ? e.getMessage() : "Fallo desconocido"
            ));
        });
    }

    /**
     * Apaga los recursos de forma ordenada.
     */
    public void stop() {
        log.info("Iniciando apagado del sistema...");
        if (app != null) {
            app.stop();
        }
        try {
            DatabaseManager dbManager = locator.get(DatabaseManager.class);
            dbManager.close();
        } catch (IllegalArgumentException e) {
            log.warn("El DatabaseManager no estaba registrado al apagar.");
        }
        log.info("Sistema apagado exitosamente.");
    }

    // Getter para que el Main u otros componentes puedan acceder al locator
    public ServiceLocator getLocator() {
        return locator;
    }
}
