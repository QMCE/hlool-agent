package rj.cocacode.skills

import rj.cocacode.skills.Skill
import rj.cocacode.skills.SkillSource
import rj.cocacode.skills.SkillLoader
import rj.cocacode.skills.SkillExecutor

/**
 * Bundle of built-in skills that are registered programmatically.
 * Mirrors the TypeScript reference implementation (bundledSkills.ts / bundled/ directory).
 */
object BundledSkills {

    fun registerAll() {
        registerCommit()
        registerVerify()
        registerTest()
        registerBuild()
        registerLint()
        registerFormat()
        registerFeedback()
        registerHelp()
        registerSearch()
        registerReview()
        registerFix()
        registerClean()
        registerDocs()
        registerRefactor()
        registerDeploy()
        registerDebug()
        registerExplain()
        registerPlan()
        registerStatus()
        registerAgent()
        registerSubAgent()
        registerDelegate()
    }

    private fun register(
        name: String,
        description: String,
        command: String,
        prompt: String,
        metadata: Map<String, String> = emptyMap()
    ) {
        SkillLoader.registerSkill(
            Skill(
                name = name,
                description = description,
                command = command,
                prompt = prompt,
                source = SkillSource.BUNDLED,
                metadata = metadata
            )
        )
    }

    // ── Commit ──
    private fun registerCommit() {
        register(
            name = "commit",
            description = "Stage changes and create a conventional commit",
            command = "/commit",
            prompt = """
                Run `git diff --cached --stat` to see staged files.
                If nothing is staged, run `git diff --stat` to see unstaged changes.
                
                Analyze the diff and craft a conventional commit message:
                - Use conventional commit format: `type(scope): description`
                - Types: feat, fix, refactor, test, docs, chore, style, perf
                - Keep the summary line under 72 characters
                - Add a detailed body explaining what and why
                
                Run: git commit -m "..." -m "..."
                If no files are staged, first run: git add <files>
            """.trimIndent(),
            metadata = mapOf("requires_git" to "true")
        )
    }

    // ── Verify ──
    private fun registerVerify() {
        register(
            name = "verify",
            description = "Run project verification (tests, lint, build check)",
            command = "/verify",
            prompt = """
                Run the project's verification pipeline:
                1. If a `check` script exists in package.json or build.gradle: run it
                2. Otherwise run: lint check → test → build in sequence
                3. Report any failures with details
            """.trimIndent(),
            metadata = mapOf("category" to "quality")
        )
    }

    // ── Test ──
    private fun registerTest() {
        register(
            name = "test",
            description = "Run tests (optionally filter by pattern)",
            command = "/test",
            prompt = """
                Run the project's test suite.
                If a specific test path or pattern is provided, run only that subset.
                Default strategy:
                - Kotlin/Gradle: `./gradlew test`
                - Maven: `mvn test`
                - Node: `npm test` or `npx jest <pattern>`
                - Python: `pytest <path>`
                - Generic: look for test scripts in package.json or build config
                
                Report: count of passed/failed/skipped, and any failure details.
            """.trimIndent(),
            metadata = mapOf("category" to "quality")
        )
    }

    // ── Build ──
    private fun registerBuild() {
        register(
            name = "build",
            description = "Build the project",
            command = "/build",
            prompt = """
                Run the project's build command.
                Detect build system:
                - Gradle: `./gradlew build`
                - Maven: `mvn compile`
                - npm/yarn: `npm run build` or `yarn build`
                - cargo: `cargo build`
                - go: `go build ./...`
                
                Report build success or show error details.
            """.trimIndent(),
            metadata = mapOf("category" to "build")
        )
    }

    // ── Lint ──
    private fun registerLint() {
        register(
            name = "lint",
            description = "Run linter on the project",
            command = "/lint",
            prompt = """
                Run the project's linter.
                Detect tooling:
                - ESLint: `npx eslint .`
                - ktlint: `./gradlew ktlintCheck`
                - pylint/flake8: `python -m flake8`
                - RuboCop, golangci-lint, etc.
                - cargo clippy for Rust projects
                
                Report lint violations grouped by severity.
            """.trimIndent(),
            metadata = mapOf("category" to "quality")
        )
    }

    // ── Format ──
    private fun registerFormat() {
        register(
            name = "format",
            description = "Format code files",
            command = "/format",
            prompt = """
                Run code formatters on the project:
                - Prettier for JS/TS/CSS/MD: `npx prettier --write .`
                - ktlint format: `./gradlew ktlintFormat`
                - black for Python: `python -m black .`
                - gofmt for Go: `gofmt -w .`
                - rustfmt: `cargo fmt`
                
                If formatter not configured, suggest adding one.
            """.trimIndent(),
            metadata = mapOf("category" to "quality")
        )
    }

    // ── Feedback ──
    private fun registerFeedback() {
        register(
            name = "feedback",
            description = "Save feedback about agent behavior",
            command = "/feedback",
            prompt = """
                Help the user save feedback as a memory.
                Ask what worked well or what should change about how I approach work.
                Format the feedback clearly and save it as a feedback-type memory.
            """.trimIndent(),
            metadata = mapOf("category" to "memory")
        )
    }

    // ── Help ──
    private fun registerHelp() {
        register(
            name = "help",
            description = "Show available skills and usage info",
            command = "/help",
            prompt = """
                List all available skills with their descriptions.
                Group them by category if metadata includes category.
                Include usage examples for common skills.
            """.trimIndent(),
            metadata = mapOf("category" to "utility")
        )
    }

    // ── Search ──
    private fun registerSearch() {
        register(
            name = "search",
            description = "Search project code and files",
            command = "/search",
            prompt = """
                Search the project for the given query.
                - Use ripgrep (rg) if available: `rg -n <query>`
                - Fall back to grep/grep -r
                - Search both code and markdown files
                - Present results grouped by directory
            """.trimIndent(),
            metadata = mapOf("category" to "utility")
        )
    }

    // ── Review ──
    private fun registerReview() {
        register(
            name = "review",
            description = "Review staged or specified changes",
            command = "/review",
            prompt = """
                Review changes for quality, security, and correctness.
                - If changes are staged: examine `git diff --cached`
                - If files are specified: read and analyze those files
                - Check for: logic errors, security issues, style problems, missing tests
                - Provide constructive feedback with specific line references
            """.trimIndent(),
            metadata = mapOf("category" to "quality")
        )
    }

    // ── Fix ──
    private fun registerFix() {
        register(
            name = "fix",
            description = "Fix compiler errors, lints, or test failures",
            command = "/fix",
            prompt = """
                Analyze the issue and fix it:
                1. Read the error output carefully
                2. Locate the source of the problem
                3. Apply the minimal fix needed
                4. Verify the fix by re-running the failing command
                
                Types of fixes:
                - Compiler errors: missing imports, type mismatches, syntax
                - Lint errors: style issues, unused variables
                - Test failures: logic errors, assertion mismatches
            """.trimIndent(),
            metadata = mapOf("category" to "utility")
        )
    }

    // ── Clean ──
    private fun registerClean() {
        register(
            name = "clean",
            description = "Clean build artifacts and caches",
            command = "/clean",
            prompt = """
                Clean the project's build artifacts:
                - Gradle: `./gradlew clean`
                - Maven: `mvn clean`
                - npm: `rm -rf node_modules/.cache`
                - cargo: `cargo clean`
                - Remove .next/, build/, dist/ directories as appropriate
            """.trimIndent(),
            metadata = mapOf("category" to "build")
        )
    }

    // ── Docs ──
    private fun registerDocs() {
        register(
            name = "docs",
            description = "Generate or update documentation",
            command = "/docs",
            prompt = """
                Read the relevant source files and generate/update documentation.
                - For API docs: document each public function/class with params and returns
                - For README: update with new features or changes
                - Use existing doc style as reference
                - Place docs in the appropriate location (README.md, docs/, or inline)
            """.trimIndent(),
            metadata = mapOf("category" to "utility")
        )
    }

    // ── Refactor ──
    private fun registerRefactor() {
        register(
            name = "refactor",
            description = "Refactor code with specified improvements",
            command = "/refactor",
            prompt = """
                Refactor the specified code:
                - Analyze the current code structure
                - Identify improvement opportunities (extract method, rename, simplify)
                - Apply changes with clear commit-worthy steps
                - Do NOT change behavior — only improve structure
            """.trimIndent(),
            metadata = mapOf("category" to "utility")
        )
    }

    // ── Deploy ──
    private fun registerDeploy() {
        register(
            name = "deploy",
            description = "Deploy the project (dry-run by default)",
            command = "/deploy",
            prompt = """
                Prepare for deployment:
                - Check git status (clean working tree?)
                - Run verification (tests, build)
                - For dry-run: show the deploy plan without executing
                - For actual deploy: follow the project's deploy instructions
                
                Confirm with user before executing actual deployment.
            """.trimIndent(),
            metadata = mapOf("category" to "ops")
        )
    }

    // ── Debug ──
    private fun registerDebug() {
        register(
            name = "debug",
            description = "Diagnose and debug issues",
            command = "/debug",
            prompt = """
                Debug the reported issue:
                1. Gather information (logs, stack traces, state)
                2. Formulate hypotheses about root cause
                3. Test hypotheses with targeted commands
                4. Narrow down to the root cause
                5. Suggest or apply the fix
            """.trimIndent(),
            metadata = mapOf("category" to "utility")
        )
    }

    // ── Explain ──
    private fun registerExplain() {
        register(
            name = "explain",
            description = "Explain code or concepts",
            command = "/explain",
            prompt = """
                Explain the specified code or concept:
                - Read the relevant code/files
                - Explain what it does, why it exists, and how it works
                - Tailor explanation depth to context (user preference, complexity)
                - Include relevant examples or analogies where helpful
            """.trimIndent(),
            metadata = mapOf("category" to "utility")
        )
    }

    // ── Plan ──
    private fun registerPlan() {
        register(
            name = "plan",
            description = "Create an implementation plan",
            command = "/plan",
            prompt = """
                Create a step-by-step implementation plan:
                1. Understand the goal from context
                2. Break down into concrete, ordered steps
                3. For each step: what files to change, what approach to use
                4. Identify risks, dependencies, and edge cases
                5. Present as a numbered plan
            """.trimIndent(),
            metadata = mapOf("category" to "utility")
        )
    }

    // ── Status ──
    private fun registerStatus() {
        register(
            name = "status",
            description = "Show project health summary",
            command = "/status",
            prompt = """
                Show a summary of the project's current state:
                - Git branch, ahead/behind, uncommitted changes
                - Recent commits (last 5)
                - Build status (last build result)
                - Any known issues from recent work
            """.trimIndent(),
            metadata = mapOf("category" to "utility")
        )
    }

    // ── Agent ──
    private fun registerAgent() {
        register(
            name = "agent",
            description = "Create or interact with sub-agents",
            command = "/agent",
            prompt = """
                Manage sub-agents:
                - To create an agent: specify type, name, and task
                - To delegate: assign a task to an existing agent
                - To check status: review agent's work
                - Available agent types: general, code-reviewer, debugger, docs-writer
            """.trimIndent(),
            metadata = mapOf("category" to "agent")
        )
    }

    // ── SubAgent ──
    private fun registerSubAgent() {
        register(
            name = "subagent",
            description = "Execute a complex task in a dedicated sub-agent context window",
            command = "/subagent",
            prompt = """
                Delegate a focused task to a sub-agent with its own context window.
                The sub-agent receives the task description and executes independently.

                When to use:
                - Deep code review across multiple files
                - Complex refactoring or bug investigation
                - Research tasks that need focused attention

                Sub-agents can themselves delegate to further sub-agents,
                enabling recursive multi-level problem solving.
            """.trimIndent(),
            metadata = mapOf("category" to "agent")
        )
    }

    // ── Delegate ──
    private fun registerDelegate() {
        register(
            name = "delegate",
            description = "Split complex tasks and delegate subtasks to specialized sub-agents",
            command = "/delegate",
            prompt = """
                Break complex tasks into independent subtasks and delegate
                each one to the most suitable sub-agent.

                Workflow:
                1. Analyze the overall task and identify independent subtasks
                2. Assign each subtask to a sub-agent with matching specialization
                3. Collect results from all sub-agents
                4. Synthesize into a single coherent response

                Best for: large refactors, multi-file reviews, parallel research,
                and any task with clearly separable components.
            """.trimIndent(),
            metadata = mapOf("category" to "agent")
        )
    }
}
