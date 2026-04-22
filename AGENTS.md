# Cocacode Agents

## Overview

Cocacode supports a multi-agent system with customizable agents for different coding tasks.

## Available Agents

### Default Agent

The default agent handles general coding tasks:
- Code generation and editing
- File operations
- Search and navigation
- Problem solving

### Code Review Agent

Specialized agent for code review tasks:
- Pattern detection
- Best practice verification
- Style consistency checks

### Debug Agent

Focused on debugging and troubleshooting:
- Error analysis
- Root cause identification
- Fix suggestions

## Agent Configuration

Agents can be configured in `config.json`:

```json
{
  "agents": {
    "default": {
      "model": "coca-default",
      "temperature": 0.7
    },
    "review": {
      "model": "coca-review",
      "temperature": 0.3
    }
  }
}
```

## Custom Agents

Create custom agents by defining:

```json
{
  "name": "my-agent",
  "description": "Custom agent description",
  "systemPrompt": "You are a specialized...",
  "tools": ["bash", "read", "edit"],
  "model": "custom-model"
}
```

## Agent Communication

Agents communicate via the internal message bus:
- Tool requests
- State updates
- Results

## Implementation

See `src/main/kotlin/rj/cocacode/agents/` for implementation details.
