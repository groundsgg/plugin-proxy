#!/usr/bin/env bash
set -euo pipefail
if [[ $# != 2 ]]; then
  echo "Usage: $0 <built-plugin-proxy.jar> <deployed-velocity.jar>" >&2
  exit 2
fi
plugin_jar=$(realpath "$1")
velocity_jar=$(realpath "$2")
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
probe_dir=$(mktemp -d /tmp/grounds-welcome-dispatch.XXXXXX)
trap 'rm -rf -- "$probe_dir"' EXIT
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" -cp "$plugin_jar:$velocity_jar" -d "$probe_dir" "$script_dir/WelcomeDispatchProbe.java"
(cd -- "$probe_dir" && "${JAVA_HOME:+$JAVA_HOME/bin/}java" --enable-native-access=ALL-UNNAMED -cp "$probe_dir:$plugin_jar:$velocity_jar" WelcomeDispatchProbe)
