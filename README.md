# DevBrain-CQUPT

DevBrain-CQUPT is initialized as a Java 17 + Spring Boot 3.5.x multi-module backend with a React 18 + Vite + TypeScript frontend.

## Modules

| Path | Responsibility |
| --- | --- |
| `bootstrap/` | Main Spring Boot application entry and future business APIs. |
| `framework/` | Shared framework utilities, common responses, exceptions, trace, and cross-cutting support. |
| `infra-ai/` | Future AI provider adapters for chat, embedding, rerank, routing, and fallback. |
| `mcp-server/` | Standalone MCP tool service entry. |
| `frontend/` | Vite React frontend application. |
| `resources/database/` | Database schema and seed scripts from later build steps. |
| `resources/docker/` | Local Docker Compose and container configuration. |
| `resources/docs/` | Runtime or exported project documents. |
| `docs/` | Development documents and initialization reports. |

## Common Commands

```powershell
mvn -q -DskipTests compile
mvn -pl bootstrap spring-boot:run
cd frontend
npm install
npm run dev
```
