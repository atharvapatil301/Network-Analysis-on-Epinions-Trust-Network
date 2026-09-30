# Network Analysis on Epinions Trust Network


**GitHub Repository**: https://github.com/2024-F-CS6240/project-adit-atharva

---

## Project Overview

This project implements two major tasks analyzing the Epinions trust network (75,879 nodes, 508,837 edges):

1. **Ban-Wave Robustness Analysis** - Tests network fragility under targeted vs. random node removal
2. **Opinion Dynamics Simulation** - DeGroot consensus model on graph variants

---

## Branch Navigation

### Task 1: Ban-Wave Robustness Analysis
📁 **Branch**: `task1`

Navigate to this branch for:
- Connected components analysis
- Graph variant generation (degree/community/random removal)
- Scala implementation with Spark
- Fragmentation statistics and speedup analysis

### Task 2: Opinion Dynamics Simulation
📁 **Branch**: `task2`

Navigate to this branch for:
- DeGroot opinion dynamics model
- Java implementation with Spark
- Sequential baseline comparison
- AWS EMR execution with 2.93× speedup


---

## Quick Start

```bash
# Clone repository
git clone https://github.com/2024-F-CS6240/project-adit-atharva.git
cd project-adit-atharva

# For Task 1 (Ban-Wave Analysis)
git checkout task1
# See README in task1 branch for build/execution instructions

# For Task 2 (Opinion Dynamics)
git checkout task2
# See README in task2 branch for build/execution instructions
```

---


**Course**: CS 6240 - Parallel Data Processing in MapReduce
**Institution**: Northeastern University
**Semester**: Summer 2026
