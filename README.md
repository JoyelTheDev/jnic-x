# Jnic

Jnic is a build tool that "nativizes" Java bytecode methods: at build time it scans methods in `input.jar` matching configured rules, rewrites them as `native`, generates corresponding C code, cross-compiles multi-platform dynamic libraries via Zig, and finally packs the libraries back into the output JAR as encrypted resources — at runtime, an injected `JNICLoader` automatically unpacks and loads them.

> Use cases: code protection/obfuscation experiments, JNI/bytecode engineering study, build-time native bridge layer generation, etc.
> Note: This is a hard "semantic preservation" problem — complex bytecode, special instructions, reflection, etc. may not be fully equivalent.

---

## Features

- Build-time processing: input JAR → generate C → Zig compile → output JAR
- Runtime auto-loading: injects `JNICLoader.load("jnic", clazz)` into the `<clinit>` of processed classes
- Multi-target cross-compilation: Windows / Linux / macOS / Android (driven by the `target` config)
- Resource packing: compiles output packed as `cn/sky/jnic/<uuid>.dat` with XOR encryption
- Optional string encryption and basic control flow handling (see `obfuscation` in `config.yml` — only a simple XOR string cipher is currently implemented)

---

## Quick Start

### 1) Requirements

- JDK 17 (`build.gradle` targets Java 17)
- On Windows, the bundled Zig in `zig-x86_64-windows/` can be used directly; on other systems, install `zig` yourself and ensure it's on `PATH`

### 2) Prepare Input

Place the JAR to process in the project root (or adjust paths as needed):

- `input.jar`: the input JAR to be processed
- `libs/`: optional dependency libraries (used to complete the classpath for analysis/generation)

### 3) Configure `config.yml`

Example `config.yml` in the project root (modify as needed):

```yml
input: ./input.jar
output: ./output.jar
libs:
  - ./libs
target:
  - WINDOWS_X86_64
  - LINUX_X86_64
  # - ANDROID_ARM64
includes:
  - "*"
excludes:
  - ""
obfuscation:
  stringEncryption: true
  flowObfuscation: false
  antiDebug: true
```

Notes:

- `includes/excludes` use class internal names (e.g. `cn/sky/**`, separator is `/`), supporting `*`, `**`, `?`
- Avoid leaving `includes/excludes` as empty array entries (e.g. bare `-`), to prevent empty-string matching logic issues

### 4) Build and Run

Build:

```bash
./gradlew.bat build
```

Run (to execute the tool itself):

```bash
java -jar jnic.jar
```

Output after completion:

- `output.jar`: contains classes rewritten as `native`, plus the injected loader and encrypted native library resources

---

## How It Works (Flowchart)

```mermaid
flowchart LR
  A[input.jar] --> B[SkyJarLoader reads classes/resources]
  B --> C[NativeProcessor scans/filters methods]
  C --> D[CGenerator generates native-lib.c]
  D --> E[ZigCompiler cross-compiles multi-target dynamic libs]
  E --> F[Pack as cn/sky/jnic/uuid.dat]
  F --> G[SkyJarLoader writes output.jar]
  G --> H[Runtime JNICLoader unpacks + System.load]
  H --> I[registerNatives(class)]
```

---

## Target Platforms and Naming

### Config target → Zig target

Internal mapping in [ZigCompiler.java](src/main/java/cn/sky/jnic/process/ZigCompiler.java):

- `WINDOWS_X86_64` → `x86_64-windows`
- `LINUX_X86_64` → `x86_64-linux`
- `MACOS_X86_64` → `x86_64-macos`
- `MACOS_ARM64` → `aarch64-macos`
- `ANDROID_ARM64` → `aarch64-linux-android`
- `ANDROID_ARM32` → `arm-linux-androideabi`
- `ANDROID_X86` → `x86-linux-android`
- `ANDROID_X86_64` → `x86_64-linux-android`

### Dynamic library filename (unified between compile-time and runtime)

The runtime loader `JNICLoader` assembles the target library name from system info:

```
lib<libName>_<arch>-<platform>.<ext>
```

Examples:

- Windows x86_64: `libjnic_x86_64-windows.dll`
- Linux x86_64: `libjnic_x86_64-linux.so`
- Android arm64: `libjnic_aarch64-android.so`

---

## FAQ

### 1) Zig not found

- Windows: use the bundled `zig-x86_64-windows/` in the jnic.jar directory (or add `zig` to `PATH` manually)
- Non-Windows: install Zig and ensure `zig` is directly invokable from the command line

---

## Project Structure (Summary)

- `src/main/java/cn/sky/jnic/`
  - `Jnic`: main flow, temp directory and resource key generation
  - `SkyJarLoader`: read/write JAR (classes + resources)
  - `process/NativeProcessor`: method filtering, loader injection, native lib packing
  - `generator/CGenerator`: C code generation and `RegisterNatives` generation
  - `process/ZigCompiler`: Zig compilation and target mapping
  - `JNICLoader`: runtime unpacking and dynamic library loading
- `src/main/resources/jni.h`: bundled JNI header (used during Zig compilation)

---

## Roadmap

### 1) Native layer performance optimization

### 2) Add control flow obfuscation and stronger string encryption

### 3) Rename obfuscation

---

## Disclaimer

This project involves bytecode rewriting and native code generation, which may introduce compatibility and security risks. Use only in controlled environments and evaluate the stability and compliance of output artifacts yourself.
