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

Introduce a stable parameter container that maps:

- user-facing names
- optimizer vector positions
- bound numerical values

This avoids pushing raw dictionaries through every layer and creates a foundation for reproducible optimization runs.

### 2. Ansatz Interface

Define a thin ansatz abstraction that builds a parameterized circuit using `QuMat`.

Example responsibilities:

- declare parameter names
- build circuit topology from config
- support simple reusable templates such as layered hardware-efficient circuits

### 3. Observable / Objective Layer

Support cost evaluation for a small initial set of objective types:

- measurement-probability objectives
- expectation of simple Pauli terms
- user-defined callback objectives

The key design choice is to separate "how the circuit is built" from "how quality is measured."

### 4. Optimization Loop

Provide a small optimizer runner that:

- accepts an objective function
- binds parameters
- executes the circuit
- records cost history
- returns a structured result

The first version can support:

- random search or grid search for debugging
- SciPy-backed classical optimizers when available
- a finite-difference gradient option later, but not as a requirement for the initial draft

### 5. Result Object

Return a consistent artifact for every variational run, including:

- best parameters
- best objective value
- iteration history
- backend configuration used
- execution counts and timing metadata where available

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

Webhook delivery should be optional and should not invalidate a variational run by default if a downstream endpoint is unavailable. Configuration should allow teams to choose which events are emitted and what payload detail is included.

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
    objective="probability_zero",
    optimizer="cobyla",
)

result = runner.minimize(initial_parameters="zeros")
```

Key properties of this direction:

- `QuMat` remains the execution substrate.
- The variational layer owns algorithm flow and reporting.
- The API can later grow toward VQE, QAOA, and QML without breaking the core model.

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

### Phase 1: Minimal Engine

- parameter container
- simple ansatz abstraction
- one objective type
- one optimizer runner
- structured result object
- event schema plus optional webhook sink
- tests against at least one backend

### Phase 2: Practical Algorithms

- VQE-style expectation workflow
- QAOA helper templates
- convergence history and diagnostics
- cross-backend regression coverage

### Phase 3: QML / QDP Integration

- data-reuploading patterns
- minibatch-capable objective wrappers
- optional QDP-accelerated encoding path

## Risks

### 1. Backend Capability Drift

Different backends expose different strengths for parameter binding, expectation evaluation, and measurement semantics. The engine must normalize behavior carefully or explicitly narrow supported features per backend.

### 2. API Surface Growth

Variational frameworks tend to sprawl quickly. If ansatzes, observables, optimizers, and datasets all land at once, the design will become unstable. The first version should stay narrow.

### 3. Misplaced Responsibility

If too much logic is pushed into `QuMat`, the base library becomes harder to maintain. The engine should orchestrate workflows while `QuMat` remains focused on circuit execution primitives.

### 4. Testing Cost

Variational workflows combine stochastic execution, optimizer behavior, and backend-specific result formats. Tests need deterministic fixtures and tolerance-aware assertions.

### 5. Operational Noise And Backpressure

Webhook-based observability can become noisy or operationally brittle if every iteration emits oversized payloads or if downstream systems are slow. The engine should define bounded payloads, configurable event selection, and clear failure-handling rules early.

## Success Criteria

The proposal should be considered successful if the first implementation can support all of the following:

- a user can define a reusable ansatz without manually managing raw parameter dictionaries
- a user can optimize a simple objective over a parameterized circuit in a backend-neutral way
- the engine returns a structured optimization result instead of ad hoc values
- the design remains compatible with future VQE, QAOA, and QML extensions

## Recommendation

Proceed with `variational-engine` as an incubation area and treat the initial deliverable as a minimal orchestration layer, not a full framework. The immediate objective should be to prove the core abstraction:

"Qumat can execute parameterized circuits" evolves into "Mahout can run variational quantum workloads."

That is the right feature boundary for the next step.
