# Variational Engine Proposal

## Summary

This proposal introduces a new `variational-engine` area for a first-class variational workflow on top of Qumat. The goal is to move from "parameterized circuits exist" to "users can define, optimize, and evaluate variational quantum programs" without forcing them to hand-build the orchestration loop themselves.

The existing codebase already provides the core prerequisites:

- backend-agnostic circuit construction in `qumat`
- parameterized single-qubit rotation gates
- parameter binding across Qiskit, Cirq, and Amazon Braket
- QDP for accelerated classical-to-quantum data preparation

What is missing is the layer that turns those primitives into a usable variational system: observables, objective evaluation, optimizer integration, algorithm templates, execution accounting, and result objects.

## Problem

Today, users can manually create a parameterized circuit and execute it with different parameter bindings, but they must assemble the rest of the variational workflow themselves:

- define a repeatable ansatz structure
- evaluate expectation values or other cost functions
- manage parameter vectors and named parameters
- run optimization loops
- collect convergence history and execution metadata
- publish run events to external systems for monitoring, alerting, or experiment tracking
- connect data loading or embedding pipelines from QDP into training workflows

That leaves Qumat with parameterized-circuit support but without a coherent variational programming model.

## Goals

- Provide a backend-neutral variational workflow built on top of existing `qumat` APIs.
- Standardize how parameters, observables, objectives, and optimizers interact.
- Make common algorithms such as VQE and QAOA straightforward to express.
- Allow future integration with QDP-backed feature preparation for quantum ML workloads.
- Bake observability into variational execution from the start, including configurable webhook notifications.
- Make the implementation and documentation accessible to contributors who have little or no prior quantum-computing background.
- Keep the first implementation narrow enough to land incrementally.

## Non-Goals

- Replacing the current `QuMat` circuit API.
- Building a full automatic-differentiation stack in the first version.
- Solving hardware-specific error mitigation in the first version.
- Introducing a large dependency surface before the API shape is proven.

## Why A Separate `variational-engine` Area

Creating a dedicated `variational-engine` folder gives the feature room to mature before it is folded into the main package surface. That is useful for three reasons:

1. The abstraction is larger than a few extra gate methods.
2. The API needs design iteration before it becomes public and stable.
3. The feature spans multiple concerns: ansatz definition, execution, objective evaluation, optimization, and reporting.

For the proposal phase, this folder can hold design documents, prototypes, and API sketches without forcing premature package-level decisions.

## Current Gap In The Codebase

Based on the current repository state:

- `qumat.QuMat` handles circuit construction, parameter registration, parameter binding, and backend execution.
- Backend modules already support symbolic parameters for rotation gates.
- Tests cover parameter binding regressions across supported backends.
- Existing docs discuss parameterized circuits and broader PQC ideas, but there is no implementation for a variational runtime.

The practical gap is not "can we create parameterized gates?" but "can we run a variational algorithm cleanly and repeatedly?"

## Proposed Scope For v1

The first version of the variational engine should focus on a small, defensible surface:

### 1. Parameter Model

Parameters will be represented symbolically in v1. Each parameter is a named symbolic object (analogous to `cirq.Symbol` or a lightweight wrapper around `sympy.Symbol`) that can appear in circuit construction and is later resolved to a numeric value at execution time.

The parameter container maps:

- user-facing names to symbolic objects
- symbolic objects to optimizer vector positions
- symbolic objects to bound numerical values

This representation is chosen deliberately: symbolic parameters allow the engine to inspect circuit structure, support analytic gradient computation in a future phase (parameter-shift rule), and make parameter provenance explicit in result objects. Numeric-only parameters would require a retrofit later and would close off the gradient path.

For v1, the symbolic layer will be kept minimal — no full `sympy` expression trees are required. A lightweight `Parameter` class with a name and an optional bound value is sufficient. The optimizer will always work with numeric vectors; the symbolic names exist for human readability, result serialization, and future gradient support.

This avoids pushing raw dictionaries through every layer and creates a foundation for reproducible optimization runs.

### 2. Ansatz Interface

Define a thin ansatz abstraction that builds a parameterized circuit using `QuMat`.

Example responsibilities:

- declare parameter names
- build circuit topology from config
- support simple reusable templates such as layered hardware-efficient circuits
- support a default QAOA template with overridable cost-layer and mixer-layer builders for domain-specific demos

### 3. Observable / Objective Layer

Phase 1 should standardize on a single objective contract: a user-defined callback that receives normalized measurement data from the engine and returns a scalar cost.

The normalized measurement payload should abstract away backend-specific result formats while remaining simple enough to compute from shot-based execution. At minimum, the callback input should include:

- shot count
- counts by measured bitstring
- probabilities by measured bitstring
- any backend metadata needed for traceability but not for backend-specific branching

This boundary keeps the first implementation flexible without committing Phase 1 to Hamiltonian parsing, Pauli decomposition, or backend-specific expectation primitives. It also creates a stable foundation for later built-in objectives, since measurement-probability objectives can be implemented as library-provided callbacks and expectation workflows can be layered on once the normalization contract is proven.

The key design choice is to separate "how the circuit is built" from "how quality is measured" while also normalizing execution output before user objective code sees it.

### 4. Optimization Loop

Provide a small optimizer runner that:

- accepts an objective function
- binds parameters
- executes the circuit
- records cost history
- returns a structured result

The first version should use a formal optimizer protocol rather than a string-only adapter. The protocol should follow an `ask` / `tell` design so the engine can pause, resume, and hand control cleanly back to an outer orchestrator such as Airflow.

The runner should execute in chunks rather than assuming a full optimization completes in one invocation. A chunk may stop after a maximum iteration count, a maximum wall-clock duration, or both. The engine should always finish the current evaluation before stopping and then return resumable state.

The first version can support:

- random search or grid search for debugging
- protocol-backed optimizers, including SciPy-backed adapters when available
- a finite-difference gradient option later, but not as a requirement for the initial draft

### 5. Result Object

Return a consistent artifact for every variational run, including:

- best parameters
- best objective value
- iteration history
- backend configuration used
- execution counts and timing metadata where available

The engine should also define a resumable checkpoint object for chunked execution. That checkpoint should be backend-portable and should include:

- run identifier and status
- current iteration
- current parameter vector
- best-so-far parameters and objective value
- serialized optimizer state
- compact history summary
- append-only iteration records with backend attribution
- timestamps for creation and last update

### 6. Built-In Observability

Observability should be part of the engine contract, not an optional afterthought. Variational workloads are iterative and frequently long-running, so operators need visibility into progress, regressions, failures, and final outcomes while runs are still active.

The first version should define a small event model for lifecycle hooks such as:

- run started
- iteration completed
- new best result found
- run failed
- run completed

Those events should be routable to configurable webhook endpoints so external systems can receive structured updates in near real time. This supports:

- lightweight experiment tracking
- CI or benchmark dashboards
- alerting on failures or degraded convergence
- integration with project-specific observability infrastructure

The engine should support multiple sinks. Local structured logging should be enabled by default. Webhook delivery should be optional and should not invalidate a variational run by default if a downstream endpoint is unavailable. Webhook delivery should default to bounded retry behavior and local logging should remain enabled even when additional sinks are configured. A failure callback may be invoked on delivery failure, but the first implementation does not need to take recovery action beyond logging and callback invocation.

### 7. Documentation And Education Surface

The variational engine will need unusually verbose documentation because the expected contributor and user may have little or no prior exposure to quantum-computing terminology.

The first implementation should therefore ship with two complementary documentation layers:

- reference and implementation docs for contributors working on the engine itself
- a light educational area explaining foundational terms and ideas such as parameterized circuits, ansatz, Hamiltonian, expectation value, QAOA, measurement counts, and classical-quantum optimization loops

This documentation should assume minimal prior quantum background and should explain not only what the API does, but why the abstractions exist and how they map to familiar classical engineering patterns.

## Proposed Architecture

The variational engine should sit above `qumat` rather than inside backend modules.

```text
variational-engine/
  proposal.md
  README.md                    # future overview
  api-sketches/                # future examples and interface notes
  prototypes/                  # future experimental implementations
```

Target runtime layering:

```text
User algorithm config
  -> Variational engine
     -> Ansatz builder
     -> Objective evaluator
     -> Optimizer runner
     -> Checkpoint / resume manager
     -> Observability hooks / webhook dispatcher
     -> QuMat
        -> backend adapter
```

This preserves current backend boundaries and keeps the new feature independent from low-level gate implementations.

## Proposed API Direction

The exact API should be validated through prototypes, but the design should move toward something like:

```python
from qumat import QuMat
from variational_engine import HardwareEfficientAnsatz, VariationalRunner

qumat = QuMat({
    "backend_name": "qiskit",
    "backend_options": {"simulator_type": "aer_simulator", "shots": 1024},
})

ansatz = HardwareEfficientAnsatz(
    num_qubits=4,
    layers=2,
    rotation_axes=("ry", "rz"),
    entanglement="linear",
)

runner = VariationalRunner(
    qumat=qumat,
    ansatz=ansatz,
    objective=my_objective_callback,
    optimizer=my_optimizer,
)

chunk = runner.run_chunk(
    initial_parameters="zeros",
    max_iterations=25,
    max_wall_time_seconds=30,
)
```

Key properties of this direction:

- `QuMat` remains the execution substrate.
- The variational layer owns algorithm flow and reporting.
- The API can later grow toward VQE, QAOA, and QML without breaking the core model.
- Chunked execution and resumable state allow the variational engine to act as an inner loop under a larger orchestration system.

## Integration With QDP

QDP should be treated as an optional upstream component, not a hard dependency of the first version.

Near-term integration path:

- use QDP to prepare feature tensors or encoded inputs
- feed those inputs into ansatz construction or data-reuploading workflows
- keep the variational engine independent of CUDA-specific logic

This separation matters because the first feature should work on plain parameterized circuits even when QDP is not installed.

## Phased Delivery Plan

### Phase 0: Proposal And API Validation

- write proposal
- sketch public interfaces
- build one or two small examples
- decide whether the feature lives as a subpackage, experimental namespace, or docs-only prototype first
- define the educational documentation outline for non-quantum readers

### Phase 1: Minimal Engine

- parameter container
- simple ansatz abstraction
- default QAOA ansatz template with overridable cost and mixer builders
- normalized measurement result contract
- user-defined callback objective
- formal optimizer protocol using `ask` / `tell`
- chunked optimizer runner with pause / resume support
- structured result object and resumable checkpoint object
- event schema plus optional webhook sink
- tests for all supported backends against the normalized measurement contract
- verbose reference documentation plus a light educational documentation area

### Phase 2: Practical Algorithms

- VQE-style expectation workflow
- convergence history and diagnostics
- cross-backend regression coverage
- richer problem-specific QAOA helpers such as portfolio optimization mappings

### Phase 3: QML / QDP Integration

- data-reuploading patterns
- minibatch-capable objective wrappers
- optional QDP-accelerated encoding path

## Risks

### 1. Backend Capability Drift

Different backends expose different strengths for parameter binding, expectation evaluation, and measurement semantics. The engine must normalize behavior carefully or explicitly narrow supported features per backend.

For the current design, this risk applies directly to normalized measurement payloads and resumability across backends. The checkpoint format must not depend on opaque backend execution state.

### 2. API Surface Growth

Variational frameworks tend to sprawl quickly. If ansatzes, observables, optimizers, and datasets all land at once, the design will become unstable. The first version should stay narrow.

### 3. Misplaced Responsibility

If too much logic is pushed into `QuMat`, the base library becomes harder to maintain. The engine should orchestrate workflows while `QuMat` remains focused on circuit execution primitives.

### 4. Testing Cost

Variational workflows combine stochastic execution, optimizer behavior, and backend-specific result formats. Tests need deterministic fixtures and tolerance-aware assertions.

### 5. Operational Noise And Backpressure

Webhook-based observability can become noisy or operationally brittle if every iteration emits oversized payloads or if downstream systems are slow. The engine should define bounded payloads, configurable event selection, and clear failure-handling rules early.

### 6. Documentation Debt

If the engine ships with only terse API notes, contributors without a quantum background will struggle to modify or extend it safely. The project should treat educational documentation as a deliverable, not as cleanup work after implementation.

## Success Criteria

The proposal should be considered successful if the first implementation can support all of the following:

- a user can define a reusable ansatz without manually managing raw parameter dictionaries
- a user can optimize a callback-defined objective over a parameterized circuit in a backend-neutral way
- the engine returns a structured optimization result and a resumable checkpoint instead of ad hoc values
- a variational run can pause on one backend and resume on another without changing the callback contract or parameter semantics
- the design remains compatible with future VQE, QAOA, and QML extensions
- the documentation is detailed enough that an engineer without prior quantum-computing experience can follow the architecture and implement a basic extension

## Open Questions

### 1. Normalized Measurement Contract For Phase 1

Phase 1 will use a user-defined callback objective that consumes normalized measurement data. The remaining design question is the exact normalized schema: which fields are mandatory, how bitstring ordering is represented, and how much backend metadata is preserved alongside counts and probabilities.

### 2. Optimizer Interface Design

Phase 1 will use a formal optimizer protocol with `ask` / `tell` semantics. The remaining design question is the exact protocol shape: whether `ask()` returns one point or many, what context is supplied to `tell()`, and how optimizer state is serialized for pause / resume.

### 3. Backend Coverage For Normalized Measurement Results

Phase 1 will normalize measurement results across all supported backends. The remaining design question is the exact conformance contract and what metadata is required versus optional for each backend adapter.

### 4. Multi-Sink Event Delivery

The engine will support multiple sinks. Local logging will be enabled by default, webhook delivery will be optional, and webhook delivery will retry by default with bounded behavior. The remaining design question is the exact retry policy and failure-callback signature.

### 5. Folder Structure Versus Package Structure

The first implementation should land the same way QDP currently does: developed in its own area but surfaced through `qumat` with an intentionally narrow integration boundary. The remaining design question is the exact import path and optional-dependency story for the first public-facing draft.

## Recommendation

Proceed with `variational-engine` as an incubation area and treat the initial deliverable as a minimal orchestration layer, not a full framework. The immediate objective should be to prove the core abstraction:

"Qumat can execute parameterized circuits" evolves into "Mahout can run variational quantum workloads."

That is the right feature boundary for the next step.
