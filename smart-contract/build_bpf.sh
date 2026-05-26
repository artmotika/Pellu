cat <<EOF > $(dirname $(which cargo-build-sbf))/cargo-build-bpf
#!/bin/bash
# Игнорируем первый аргумент, если это 'build-bpf'
if [ "\$1" == "build-bpf" ]; then
  shift
fi
exec cargo-build-sbf "\$@"
EOF
chmod +x $(dirname $(which cargo-build-sbf))/cargo-build-bpf