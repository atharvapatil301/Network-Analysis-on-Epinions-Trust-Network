# Dataset: Epinions Trust Network

## Overview

This directory contains the Epinions trust network dataset used for opinion simulation.

## Dataset Information

- **Source**: Stanford Network Analysis Project (SNAP)
- **URL**: https://snap.stanford.edu/data/soc-Epinions1.html
- **Citation**: M. Richardson, R. Agrawal, P. Domingos. "Trust Management for the Semantic Web." ISWC, 2003.

## Statistics

- **Nodes**: 75,879
- **Edges**: 508,837
- **Type**: Directed, unweighted
- **Largest WCC**: 75,877 nodes (99.998%)
- **Largest SCC**: 32,223 nodes (42.5%)
- **Average Clustering Coefficient**: 0.1378
- **Diameter**: 14
- **90-percentile Effective Diameter**: 5

## Download Instructions

### Option 1: Direct Download

```bash
cd data
wget https://snap.stanford.edu/data/soc-Epinions1.txt.gz
gunzip soc-Epinions1.txt.gz
```

### Option 2: Manual Download

1. Visit: https://snap.stanford.edu/data/soc-Epinions1.html
2. Download `soc-Epinions1.txt.gz`
3. Extract to this directory

## File Format

The dataset is a tab-separated edge list:

```
# FromNodeId    ToNodeId
0               1
0               2
1               0
...
```

- Lines starting with `#` are comments
- Each line represents a directed trust edge
- FromNodeId trusts ToNodeId

## Semantic Meaning

In the Epinions network:
- **Node**: A user on Epinions.com
- **Edge (A → B)**: User A trusts user B's reviews
- **Trust**: Used to weight review ratings in recommendation

## Why This Dataset?

1. **Directed trust relationships** - Natural fit for DeGroot model (asymmetric influence)
2. **Well-studied** - Allows comparison with existing network analysis research
3. **Moderate size** - Large enough for big data techniques, small enough for reasonable runtime
4. **Real-world** - Actual trust relationships, not synthetic

## Data Processing

The raw dataset will be processed as follows:

1. **Load**: Read edge list, skip comments
2. **Compute out-degree**: Count outgoing edges per node
3. **Assign weights**: Uniform weight = 1/out-degree for each edge
4. **Initialize opinions**: Apply seeding strategy to all nodes

## File Size

- Compressed (`.gz`): ~2.7 MB
- Uncompressed (`.txt`): ~7.5 MB

## Notes

- The dataset is already in the `.gitignore` to avoid committing large files
- For AWS deployment, upload to S3 instead of storing in repository
- Some nodes may have zero in-degree or out-degree (isolated or one-directional)
