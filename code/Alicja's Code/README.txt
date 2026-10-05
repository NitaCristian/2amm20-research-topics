# Multi-DISPALE Team Share

## Purpose

This folder contains the modified DISPALE source files for the Multi-DISPALE / top-k discriminating-pattern experiment.

The implementation extends the original DISPALE approach so that multiple discriminating sub-patterns can be selected and used during learning.

## Included files

- `Dispale.scala`
  - Main DISPALE implementation.
  - Contains the Multi-DISPALE changes, including handling of the top-k discriminating patterns and updating the feature/weight representation.

- `BestICVSubset.java`
  - Modified discriminating-pattern mining component.
  - Supports selecting the top-k patterns according to ICV.

- `package.scala`
  - Contains the `RankedPair` implementation.
  - The pair-vector update logic was adapted so that the three temporary feature dimensions used for k=3 are added and removed one dimension at a time.

## Tested configuration

The implementation was tested with:

python3 scripts/test-program.py -m dispale -d chess -f 0.5 -k 5 -i 10 -o flexics -a eflexics -F Items -l 0 -to 100

The test completed successfully:

Finished after 10 iterations
Exit status: 0

During learning, the Multi-DISPALE representation used 79 feature dimensions. The temporary discriminating-pattern dimensions were then removed so that the final learned model returned to 76 weights.

## Building

From the root of the DISPALE repository:

./code/gradlew -p code/dispale build

The modified implementation currently builds successfully.

## Important note

These files are the modified source files only. They are intended to be used together with the original DISPALE repository and its existing dependencies, datasets, Gradle configuration, and scripts.

The successful test confirms that the current implementation runs end-to-end for the tested chess configuration. It does not by itself establish that Multi-DISPALE improves preference-learning performance; that requires comparison with the original DISPALE baseline using the planned evaluation protocol.
