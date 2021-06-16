#!/usr/bin/env bash

#set -e

eval "$(ssh-agent -s)"
for fname in *.private; do
    [ -f "$fname" ] || continue
    file=$(readlink -f "$fname")
    printf '%s\n' "Private ssh key exists: $file"
    export GIT_SSH_COMMAND="ssh -i $file -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no"
    break
done

exec java $JAVA_OPTS -jar target/*.jar