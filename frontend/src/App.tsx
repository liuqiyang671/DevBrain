const backendModules = [
  { name: 'bootstrap', text: 'Main API entry, chat workflows, knowledge management' },
  { name: 'framework', text: 'Shared responses, exceptions, trace, and infrastructure support' },
  { name: 'infra-ai', text: 'AI model adapters, routing, embeddings, and rerank clients' },
  { name: 'mcp-server', text: 'External tool execution service for MCP capabilities' },
];

function App() {
  return (
    <main className="app-shell">
      <section className="hero" aria-labelledby="page-title">
        <div>
          <p className="eyebrow">DevBrain-CQUPT</p>
          <h1 id="page-title">AI knowledge workspace skeleton</h1>
          <p className="summary">
            Java 17, Spring Boot 3.5.x, Maven multi-module backend, and React 18
            frontend are ready for the next build step.
          </p>
        </div>
        <div className="status-panel" aria-label="Initialization status">
          <span className="status-dot" />
          <span>Project initialized</span>
        </div>
      </section>

      <section className="module-grid" aria-label="Backend modules">
        {backendModules.map((module) => (
          <article className="module-card" key={module.name}>
            <h2>{module.name}</h2>
            <p>{module.text}</p>
          </article>
        ))}
      </section>
    </main>
  );
}

export default App;
