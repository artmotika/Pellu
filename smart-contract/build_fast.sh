#!/bin/bash
# Force resolution of problematic transitive dependencies to older versions
cargo update -p block-buffer --precise 0.10.4 || true
cargo update -p constant_time_eq --precise 0.3.1 || true
cargo update -p crypto-common --precise 0.1.6 || true
cargo update -p digest --precise 0.10.7 || true
cargo update -p cpufeatures --precise 0.2.16 || true
anchor build
