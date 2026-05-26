#!/bin/bash
# Use internal toolchain path
export PATH="/usr/local/bin/sdk/sbf/dependencies/platform-tools/rust/bin:$PATH"
cargo build-sbf --version
cargo build-sbf
