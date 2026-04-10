Summary of what was commented across all 25 files:

|Category|	What was explained|
|-|-|
|Annotations	|@SpringBootApplication, @EnableJpaAuditing, @MappedSuperclass, @EntityListeners, @Enumerated(EnumType.STRING), @ConfigurationProperties, @Validated, @EnableConfigurationProperties, @Tool/@ToolParam, @Mapper(componentModel="spring"), @Transactional(readOnly=true), @ResponseStatus, @RestControllerAdvice, @ExceptionHandler, @PathVariable, @RequestParam
|JPA|	Why FetchType.LAZY, EnumType.STRING vs ORDINAL, columnDefinition="text", updatable=false, unique=true, projection interfaces
|Security|	JWT filter flow, OncePerRequestFilter, SecurityContextHolder, BCryptPasswordEncoder, STATELESS session, why CSRF is disabled
|Spring AI|	ChatClient.defaultSystem(), defaultTools(), RAG flow, VectorStore.similaritySearch(), token extraction
|Redis / Rate limit|	Lua script atomicity, RedisScript<Long>, fixed-window strategy, X-Forwarded-For header, response headers
|Lombok|	@RequiredArgsConstructor, @Slf4j, @Getter/@Setter — why each is used
|Micrometer|	Counter tags, Timer, DistributionSummary for token cost monitoring


log.debug() added to every method across all 16 files with real method bodies:

|File|	Methods covered|
|-|-|
|AgentTools.java|	findUserProfileByEmail, fetchExternalReference|
|AiClientConfig.java|	`chatClient`
|WebMvcConfig.java|	`addInterceptors`
|AgentMetrics.java|	constructor, `recordSuccess`, `recordFailure`
|JwtService.java|	constructor, `createToken`, `parseClaims`
|SecurityUtils.java|	`requireCurrentUser`
|JwtAuthenticationFilter.java|	`doFilterInternal`
|RateLimitFilter.java|	`shouldNotFilter`, `doFilterInternal`, `resolveIdentifier`, `resolveConfig`, `clientIp`
|UserService.java|	register, login, getById, searchSummaries
|KnowledgeService.java|	ingest
|ChatSessionService.java|	create, list
|AgentService.java|	chat, extractTotalTokens
|AuthController.java|	register, login
|ChatController.java|	createSession, listSessions, sendMessage, listMessages
|KnowledgeController.java|	ingest
|UserController.java|	me, search
|LoggingInterceptor.java|	preHandle, afterCompletion
|GlobalExceptionHandler.java|	all 8 handler methods
**@Slf4j** was added to files that didn't have a logger `(AgentTools, AiClientConfig, WebMvcConfig, AgentMetrics, JwtService, JwtAuthenticationFilter, UserService, KnowledgeService, ChatSessionService, AuthController, ChatController, KnowledgeController)`.

SecurityUtils got a private static final Logger instead since it's a static utility class.

To activate debug output at runtime, set in application-dev.yml:

logging:
  level:
    com.aiengineering: DEBUG

