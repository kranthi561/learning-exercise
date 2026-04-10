Here's what was commented line by line in AgentService.java:

**Annotations**

- `@Service` — why it's a specialisation of @Component and what it enables
- `@RequiredArgsConstructor` — what Lombok generates and why it's preferred over @Autowired
- `@Slf4j` — what field it injects and how to use it
- `@Transactional` — why all three DB operations (user save, history fetch, assistant save) must be atomic

**Fields**

- `MAX_HISTORY` / `RAG_TOP_K` — trade-off reasoning (context window vs token cost)
- `chatClient` — where it's configured and what it carries (system prompt + tools)
- `vectorStore` — what backs it (PgVector) and what it's used for
- `agentMetrics` — what it records and where it surfaces

**Method body — key lines**

- Ownership check with `findByIdAndUserId` — why userId is included
- Why user message is saved before the AI call
- `subList` trim — context window protection
- `vectorStore.similaritySearch` — what embedding search means, why `orElse(List.of())`
- RAG block construction — why `\n---\n` separator
- `systemWithRag` — why it's a per-call override and not the default system prompt
- The `switch` on `MessageRole` — what each case maps to in Spring AI
- `chatClient.prompt().system().messages().call().chatResponse()` — each step explained
- `Optional` chain on `assistantText` — why each `.map()` exists and what it guards
- `agentMetrics.recordSuccess` / `recordFailure` — what each metric records
- `elapsed / 1_000_000` — nanos → millis conversion
- `extractTotalTokens` — why it returns `null` vs `0`
