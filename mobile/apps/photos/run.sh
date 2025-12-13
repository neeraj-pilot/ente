#!/bin/sh

FLAVOR="${FLAVOR:-dev}"
SUPPLIED_ENV_FILE="${1:-.env}"

FLUTTER_RUN="flutter run --flavor $FLAVOR "

if [ "$FLAVOR" = "offline" ]; then
    FLUTTER_RUN="$FLUTTER_RUN --dart-define IS_LOCAL_ONLY_DEMO=true"
fi

if [ -f "$SUPPLIED_ENV_FILE" ]; then
    while IFS= read -r line
    do
        if [ -n "$line" ]; then
            FLUTTER_RUN="$FLUTTER_RUN --dart-define $line"
        fi
    done < "$SUPPLIED_ENV_FILE"
fi

echo "Running: $FLUTTER_RUN"
$FLUTTER_RUN
