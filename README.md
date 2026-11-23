# Deadlock Simulator

An interactive Java Swing desktop application that demonstrates two important operating-systems concepts:

- **Deadlock Prevention** — using the Dining Philosophers problem.
- **Deadlock Avoidance** — using the Banker's Algorithm.

The application allows users to run simulations, explore how deadlocks occur, and understand how operating systems can prevent or avoid them.

---

## Features

### Dining Philosophers Tab

- Animated live simulation with 5 philosophers and 5 forks.
- **Show the Problem** — demonstrates how all philosophers pick up their left fork first, creating a circular wait that leads to deadlock.
- **Show the Solution** — applies resource ordering by requiring philosophers to pick up the lower-numbered fork first, preventing circular wait.
- Pause and Resume controls to manage the simulation.
- Speed slider to adjust the simulation.
- Live event log displaying philosopher actions, fork pick-ups, and waiting events.

### Banker's Algorithm Tab

**Watch it Live sub-ta**

- Visual diagrams showing how deadlock occurs and how the solution works.
- Interactive controls to follow the simulation and understand each step.

**Tables & Safety Check sub-tab**

- Set the number of processes (1–20) and resources (1–15).
- Enter the Maximum, Allocated, and Available resource values directly into the tables.
- **Find Need** — calculates the Need matrix by subtracting Allocated from Maximum.
- **Run Algorithm** — performs the Banker's safety check and displays whether the system is in a SAFE or UNSAFE state.
- Displays the safe sequence when one exists.
- **Next Step** — walks through the safety sequence one process at a time, highlighting the current process and explaining what happens.
- **Advanced Panel** — allows users to submit resource requests for individual processes. The request is granted only if the system remains in a safe state; otherwise, it is denied with an explanation.
- Resource Allocation Graph that updates to reflect the current system state.

---

## Project Structure

```text
deadlock-simulator/
├── lib/
│   ├── flatlaf-3.8.jar          # UI look-and-feel
│   ├── gs-core-2.0.jar          # GraphStream core
│   ├── gs-ui-swing-2.0.jar      # GraphStream Swing renderer
│   ├── mbox2-1.0.jar
│   └── pherd-1.0.jar
└── src/edu/keiser/deadlock/
    ├── Main.java                 # Application entry point
    ├── core/
    │   └── Mode.java             # SAFE / UNSAFE states
    ├── banker/
    │   ├── BankersAlgorithm.java # Banker's Algorithm logic
    │   └── BankersGUI.java       # Banker's Algorithm interface
    ├── philosophers/
    │   ├── PhilosophersSimulation.java
    │   ├── PhilosophersConsole.java
    │   └── PhilosopherState.java
    └── ui/
        ├── PhilosophersTab.java
        ├── PhilosophersScreen.java
        ├── WalkthroughScreen.java
        ├── BankersLiveScreen.java
        ├── BankersGraphPanel.java
        ├── BankersParkingPanel.java
        ├── GraphStreamLiveGraph.java
        ├── SidePanel.java
        ├── TablePanel.java
        ├── TableRenderer.java
        └── Theme.java
```

---

## Requirements

- Java 17 or later.
- IntelliJ IDEA (recommended) or any Java IDE.

---

## Running in IntelliJ IDEA

1. Open the project folder in IntelliJ IDEA.
2. Go to **File → Project Structure → Modules** and make sure `src` is marked as a Sources Root.
3. The required JAR libraries are located in the `lib/` folder and configured in `.idea/deadlock-simulator.iml`.
4. Open `Main.java`, right-click, and select **Run 'Main.main()'**.

---

## Running from the Command Line

Open a terminal in the project root directory.

**Compile the project:**

```bash
javac -cp "lib/*" -d out -sourcepath src src/edu/keiser/deadlock/Main.java
```

**Run the application (Windows):**

```bash
java -cp "out;lib/*" edu.keiser.deadlock.Main
```

On macOS or Linux, replace `;` with `:` in the classpath.

---

## How the Banker's Algorithm Works

The Banker's Algorithm is a deadlock avoidance method that checks whether the system can safely allocate resources without causing deadlock.

A **safe state** means there is at least one possible order in which all processes can finish using the available resources.

The algorithm uses the following matrices:

| Matrix    | Meaning                                                |
| --------- | ------------------------------------------------------ |
| Maximum   | The maximum number of resources each process may need. |
| Allocated | The resources currently assigned to each process.      |
| Available | The resources currently free in the system.            |
| Need      | The remaining resources each process needs to finish.  |

**Need = Maximum − Allocated**

**Safety Check:**

1. Check which processes have a Need less than or equal to the Available resources.
2. Select a process that can finish with the available resources.
3. Once it finishes, release its allocated resources back to the system.
4. Repeat the process until all processes finish or no more processes can continue.

If all processes can finish, the system is in a **SAFE STATE**. Otherwise, it is in an **UNSAFE STATE**.

**Resource Request:**

When a process requests additional resources, the algorithm temporarily checks the request and performs another safety check. If the resulting state is safe, the resources are granted. Otherwise, the request is denied, and the previous system state is restored.

---

## How Deadlock Prevention Works

The Dining Philosophers problem demonstrates the four necessary conditions for deadlock, also known as the Coffman conditions.

| Condition        | In the Simulation                                                                 |
| ---------------- | --------------------------------------------------------------------------------- |
| Mutual Exclusion | A fork can only be held by one philosopher at a time.                             |
| Hold and Wait    | A philosopher holds one fork while waiting for another.                           |
| No Preemption    | A fork cannot be taken away from a philosopher while it is being used.            |
| Circular Wait    | Each philosopher waits for a fork held by the next philosopher, creating a cycle. |

**Solution — Resource Ordering:**

To prevent deadlock, the simulation uses resource ordering. Each philosopher must pick up the lower-numbered fork first and then the higher-numbered fork.

This breaks the circular wait condition, preventing the system from entering a deadlock.
