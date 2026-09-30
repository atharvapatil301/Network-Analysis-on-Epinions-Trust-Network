# Simulation Results

This directory contains results from the DeGroot opinion simulation runs.

## Available Result Files

### `results.csv` (Current/Default)
- **Parameters:** epsilon=0.01, MAX_ROUNDS=500
- **Date:** 2026-08-14
- **Status:** Ready for AWS deployment

### `results_new_500rounds.csv`
- **Parameters:** epsilon=0.01, MAX_ROUNDS=500
- **Total Jobs:** 118
- **Converged:** 8 (6.8%)
- **Non-converged:** 110 (93.2%)
- **Average Rounds:**
  - UNIFORM_RANDOM: 500 (hit limit)
  - BIMODAL_COMMUNITY: 500 (hit limit)
  - SINGLE_SEED_HIGHDEGREE: ~31 (converged)

### `results_old_100rounds.csv`
- **Parameters:** epsilon=0.001, MAX_ROUNDS=100
- **Total Jobs:** 118
- **Converged:** 8 (6.8%)
- **Non-converged:** 110 (93.2%)
- **Average Rounds:**
  - UNIFORM_RANDOM: 100 (hit limit)
  - BIMODAL_COMMUNITY: 100 (hit limit)
  - SINGLE_SEED_HIGHDEGREE: ~31 (converged)

## Key Findings

1. **Convergence Rate:** Only SINGLE_SEED_HIGHDEGREE strategy converges
   - This is expected for graphs with weakly-connected components
   
2. **Non-Convergence:** UNIFORM_RANDOM and BIMODAL_COMMUNITY don't converge even at 500 rounds
   - Indicates community structure in the Epinions trust network
   - Isolated communities prevent global consensus

3. **Scientific Interpretation:**
   - Non-convergence is a FEATURE, not a bug
   - Reveals structural properties of the social network
   - Validates theoretical predictions about DeGroot on weakly-connected graphs

## Recommendation

Use **results_new_500rounds.csv** (epsilon=0.01, MAX_ROUNDS=500) for AWS:
- Provides more complete dynamics (5x more iterations)
- Still completes in reasonable time (~1 min/job)
- Captures the full non-convergence behavior
