# Project_Architecture_Blueprint.md for JARM
# Generated: 2026-11-19
# Scope: post-rebrand audit of JARM v2.3.0

# JARM — Architecture Blueprint

## Executive Summary

JARM is a **BYOK (bring-your-own-key) Android LLM client** with agentic workflows,
remote device control, local inference, and an embedded web UI. It is a single Android
application with **six production Gradle modules**, a native C/C++ layer (llama.cpp +
PRoot), and a sophisticated conversation state-machine runtime.

**Primary architectural pattern:** Layered + MVVM + Event-Driven (mailbox/reducer)

---

## 1. Technology Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.3.21, C/C++ (NDK/CMake) |
| UI | Jetpack Compose Material 3, Coil, Lottie, media3, CameraX |
| Async | Kotlin Coroutines / Flow |
| Persistence | Room 2.8.4 (schema v22, 46 schema files), DataStore 1.3.0 |
| Networking | OkHttp 5.3.2, Ktor 3.4.2 (embedded WebUI), SSE |
| Serialization | kotlinx.serialization 1.11.0 |
| Crypto | BouncyCastle 1.86 |
| Native | llama.cpp (chat + embeddings), PRoot (Alpine sandbox), talloc |
| SSH | jsch 0.2.23 |
| Build | Gradle (AGP 9.2.1), KSP 2.3.9, JDK 21 |
| Testing | JUnit 4, Robolectric 4.16, MockK, Ktor Test |

---

## 2. Gradle Module Topology

```
:app (composition, native, runtime owners)
  ├── :core:model         shared values: messages, citations, Run/effect state
  ├── :core:contracts     provider/tool contracts, errors, generation context
  ├── :core:presentation  deterministic projections (timeline, citation, context)
  ├── :data:database      Room entities/DAOs/migrations, read snapshots
  └── :provider:cloud     OpenAI/Anthropic/Gemini/Ollama HTTP providers

Dependency direction (enforced at build time):
  app → cloud → contracts → model
  presentation → model + contracts
  database → model

A cycle-check plugin (build-logic) verifies all six modules on every preBuild.
```

---

## 3. App Package Structure (com.jarm.app)

| Package | Files | Responsibility |
|---|---:|---|
| `ui` | 272 | Compose screens: chat, settings, tasks, media, remote, onboarding |
| `data` | 138 | Room DAOs, DataStore, import/export, attachments, backup |
| `viewmodel` | 129 | Conversation state, generation, branching, runtime |
| `api` | 79 | Protocol adapters: openai/, anthropic/, gemini/, ollama/, local/ + tokenizers |
| `util` | 32 | Shared helpers |
| `webui` | 31 | Embedded Ktor Web UI client |
| `tool` | 28 | Agent tools: memory, RAG, web, shell, image-gen, subagents, todo, skills, MCP |
| `model` | 27 | Domain data objects |
| `phone` | 25 | Phone automation (accessibility, screen read, input) |
| `remote` | 23 | Remote session / device control |
| `automation` | 15 | Task/Loop scheduling, cron, execution engine |
| `service` | 15 | Foreground WorkManager, AIDL |
| `sandbox` | 10 | PRoot/Alpine sandbox |
| `mcp` | 7 | MCP transports, protocol client, registry |
| `di` | 1 | AppContainer (manual DI composition root) |
| `presentation` | 1 | Shared ConversationControls projection |

---

## 4. Conversation Runtime (12-Layer Agent Stack Audit)

The conversation runtime is the most architecturally complex part of JARM.
It uses a **pure reducer + command mailbox** pattern to keep generation state
deterministic and race-free.

### 4.1 State Machine

```
Idle → Preparing → Active → Finalizing → Idle
                ↘ Stopping ↗ (both normal and Stop paths)
```

- `ConversationCommandMailbox`: bounded 64-entry FIFO per conversation
- `ConversationRuntimeReducer`: pure function, no Android/Room/Provider dependency
- `GenerationManager`: executes mailbox-authorized effect bodies
- `MessageGenerationController`: adapts external Send/Stop/Queue/Edit/Regenerate/Compact
- `ChatViewModel`: Compose facade, no Run-transition authority
- `ConversationStateRegistry`: process-lifetime owner, deterministic disposal

### 4.2 12-Layer Audit

| Layer | Status | Notes |
|---|---|---|
| 1. System prompt | Clean | `GenerationRequestBuilder` assembles; no hidden prompt layers |
| 2. Session history | Clean | Room is durable truth; overlay is UI-only |
| 3. Long-term memory | Clean | `MemoryManager` + RAG, admission is explicit |
| 4. Distillation | Clean | Compact capsules are explicit user-visible operations |
| 5. Active recall | Low risk | RAG tool is opt-in per conversation |
| 6. Tool selection | Clean | Tools are code-gated via `ToolProvider` implementations |
| 7. Tool execution | Clean | `GenerationManager` executes; no hallucinated calls |
| 8. Tool interpretation | Low risk | Provider-specific validation is per-provider |
| 9. Answer shaping | Clean | Markdown projection is deterministic |
| 10. Platform rendering | Clean | Compose + Ktor WebUI both consume shared projections |
| 11. Hidden repair loops | **Clean** | No silent retry/fallback LLM passes; Stop is explicit |
| 12. Persistence | Clean | Room transaction boundaries; terminal Runs never reopened |

**Verdict: high confidence.** The architecture explicitly rejects stale results
by identity and enforces atomic transactions. No hidden repair loops detected.

---

## 5. Cross-Cutting Concerns

| Concern | Implementation |
|---|---|
| **Auth** | BYOK: user provides API keys stored in DataStore; secrets use Android Keystore AES-256-GCM envelope (plaintext fallback on failure) |
| **Error handling** | Typed generation errors in `core:contracts`; fail-closed on stale identity, graph drift, partial transactions |
| **Logging** | `DiagnosticRequestContext` in contracts; `RequestTrace` binds to app diagnostics; crash reports sent only on explicit user confirmation |
| **Validation** | Room schema migrations; Room active-slot constraint; UI re-reads at transactional boundary |
| **Config** | DataStore for settings; `AppContainer` for process-scoped DI; per-provider config in `ProviderConfig` |
| **Concurrency** | Mailbox/reducer is single transition authority; Room transactions are durable fence; locks never held across network/Provider/tool/UI |

---

## 6. Native Layer

| Component | Files | Purpose |
|---|---|---|
| llama.cpp | `llama_chat_jni.cpp`, `llama_chat_generation.cpp` | GGUF chat + embedding inference |
| PRoot | `proot_jni.cpp`, `thirdparty/proot/` | Alpine Linux sandbox for F-Droid flavor |
| talloc | `thirdparty/talloc/` | Memory allocator for PRoot |
| Atomic file publish | `atomic_file_jni.cpp` | Crash-safe file writes |
| Managed directory | `managed_directory_jni.cpp` | Sandbox file access |

CMake targets: `jarm_llama`, `jarm_proot` (renamed from `agora_*`).

---

## 7. Store Flavors

| Flavor | Difference |
|---|---|
| `fdroid` | Bundles PRoot + talloc arm64 binaries; includes Alpine sandbox |
| `play` | No bundled PRoot; sandbox uses user-installed Termux |

---

## 8. Data Architecture

- **Room v22**: 46 schema files, 58 Kotlin entity/DAO files in `:data:database`
- **Message tree**: `parentId` links; selected path per conversation; tool request/result rows are synthetic
- **Branches**: forks copy attachments; deletion deferred until Room confirms no references
- **Secrets**: Android Keystore AES-GCM; exported `.jarm` archives contain unencrypted secrets (documented)
- **Backups**: versioned `.jarm` ZIP archives; ChatGPT/Claude import supported

---

## 9. Testing Architecture

- **4,615 unique test identities** (4,599 original + 16 new from module extractions)
- **JVM tests**: JUnit 4, Robolectric, MockK, Ktor Test
- **Native tests**: `scripts/native-tests/` (C++ UTF-8 probe, Java probe)
- **Build gate**: `build.ps1` — aggregate tests + R8 + lint + F-Droid release assembly, 600-second hard deadline
- **Local last accepted gate**: 450.629 s (427 tasks, 23 executed, 404 up-to-date)

---

## 10. Extension Points

| Extension Point | Pattern |
|---|---|
| New provider | Implement `LlmProvider` in `:provider:cloud`; register in `AppContainer` |
| New tool | Implement `ToolProvider`; add to `AppContainer` tool list |
| New MCP server | Configure in Settings → MCP; auto-discovered via `McpRegistry` |
| New automation task | Define in `TaskManager`; schedule via `CronExpression` or `TaskSchedule` |
| New UI screen | Add Composable to `ui/` package; register in navigation graph |
| New sandbox backend | Implement `SandboxManager` in `app/src/fdroid/` or `app/src/play/` |

---

## 11. Known Boundaries & Constraints

1. **`core:model` still depends on Compose/Markdown** (`ChatMessage.preparedMarkdown` exposes `State.Success`). A JVM conversion requires a separate rendering-state redesign.
2. **No separate `:runtime` module yet.** `ChatRuntime`, `TaskExecutionEngine`, and `AppContainer` share registry/coordinator owners. Extracting a runtime module requires closing the dependency graph first.
3. **WebUI and Remote still consume concrete app objects** from `AppContainer`. The client boundary is prepared but not yet a separate module.
4. **157 declaration-free relocation placeholders** remain in `app` as maintenance residue from the three module extractions.
5. **Live device UI, remote CI, and clean-build timing** remain unverified (per `modularization-plan.md` §10).

---

## 12. Branding & Identity (JARM v2.3.0)

| Item | Value |
|---|---|
| App name | JARM |
| Package / Application ID | `com.jarm.app` |
| GitHub org (placeholder) | `jarm-dev` |
| GitHub repo (placeholder) | `jarmridha/JARM` |
| Website (placeholder) | `https://jarm.example.com` |
| User manual (placeholder) | `https://jarmridha.github.io/jarm/` |
| License | GPL-3.0 |
| Version | 2.3.0 (versionCode 33) |
| minSdk / targetSdk | 26 / 36 |
| ABI | arm64-v8a |
| NDK | 28.2.13676358 |
| JDK | 21 |

> **Note:** All placeholder URLs (`jarm.example.com`, `jarmridha.github.io/jarm`)
> must be replaced with real endpoints before public release.

---

## 13. Architecture Decision Records

| Decision | Rationale |
|---|---|
| Manual DI via `AppContainer` | No DI framework; explicit composition; easy to test |
| Pure reducer + mailbox | Single transition authority; prevents race conditions; terminal Runs never reopened |
| Room as durable truth | UI overlay is not a second state owner; all async results validated by identity |
| Six Gradle modules | Enforces dependency direction at build time; prevents circular imports |
| PRoot for F-Droid | Bundled Alpine sandbox without root; no Play dependency |
| GPL-3.0 (v2.2.0+) | Prevents unauthorized redistribution under different brand |

---

*Blueprint generated: 2026-11-19 | JARM v2.3.0*
*Based on: 1,888 Kotlin files, 6 Gradle modules, 12 native C/C++ files*
