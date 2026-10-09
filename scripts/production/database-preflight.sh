#!/usr/bin/env bash

verify_existing_database() {
    local container metadata running health image db_project service oneoff config_hash mount
    local expected_hash volume_metadata db_release db_release_dir db_image_id recipe
    [[ "${PPE_RECOVERY_DB_CONTAINER_ID:-}" =~ ^[0-9a-f]{64}$ \
        && -n "${PPE_RECOVERY_DB_VOLUME_CREATED_AT:-}" ]] || {
        echo "Pin the full existing DB container ID and Volume CreatedAt before updating." >&2; return 1;
    }
    db_release=${PPE_DB_RELEASE:-$deployment_release}
    [[ "$db_release" =~ ^[0-9a-f]{40}$ ]] || {
        echo "PPE_DB_RELEASE must be a full release SHA." >&2; return 1;
    }
    db_release_dir="$(dirname "$release")/$db_release"
    [[ -d "$db_release_dir" && -f "$db_release_dir/commit" && -f "$db_release_dir/READY" \
        && "$(cat "$db_release_dir/commit")" = "$db_release" \
        && "$(cat "$db_release_dir/READY")" = "$db_release" ]] || {
        echo "Pinned database release is missing or its READY/commit manifest is invalid." >&2; return 1;
    }
    for recipe in Dockerfile.db init-db.sh db-admin.sh; do
        [[ -f "$release/source/containers/production/$recipe" \
            && -f "$db_release_dir/source/containers/production/$recipe" ]] || {
            echo "Pinned database release recipe is incomplete: $recipe." >&2; return 1;
        }
        cmp -s "$release/source/containers/production/$recipe" \
            "$db_release_dir/source/containers/production/$recipe" || {
            echo "Pinned database release recipe differs from target: $recipe." >&2; return 1;
        }
    done
    export PPE_DB_SOURCE_DIR="$db_release_dir/source"
    db_image_id=$(cat "$db_release_dir/ppe-db.id") || return 1
    container=$(dc ps --all -q db) || return 1
    [[ "$container" = "$PPE_RECOVERY_DB_CONTAINER_ID" ]] || {
        echo "Deployment requires exactly the pinned existing DB container; no DB service was started." >&2; return 1;
    }
    metadata=$(docker inspect --format \
        '{{.State.Running}}|{{if .State.Health}}{{.State.Health.Status}}{{end}}|{{.Image}}|{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}|{{index .Config.Labels "com.docker.compose.oneoff"}}|{{index .Config.Labels "com.docker.compose.config-hash"}}|{{range .Mounts}}{{if eq .Destination "/var/lib/mysql"}}{{.Type}}:{{.Name}}:{{.RW}};{{end}}{{end}}' \
        "$container") || return 1
    IFS='|' read -r running health image db_project service oneoff config_hash mount <<< "$metadata"
    [[ "$running" = true && "$health" = healthy && "$db_project" = "$PPE_PROJECT" \
        && "$service" = db && "$oneoff" = False && "$image" = "$db_image_id" \
        && "$mount" = "volume:${PPE_PROJECT}_database:true;" ]] || {
        echo "Existing DB health, pinned image, labels or persistent mount differ; deployment stopped without DB lifecycle changes." >&2; return 1;
    }
    expected_hash=$(dc config --hash db) || return 1
    [[ "$expected_hash" = "db $config_hash" && "$config_hash" =~ ^[0-9a-f]{64}$ ]] || {
        echo "Existing DB Compose configuration differs; review the environment instead of recreating the DB." >&2; return 1;
    }
    volume_metadata=$(docker volume inspect --format \
        '{{.Name}}|{{.Driver}}|{{index .Labels "com.docker.compose.project"}}|{{index .Labels "com.docker.compose.volume"}}|{{.CreatedAt}}' \
        "${PPE_PROJECT}_database") || return 1
    [[ "$volume_metadata" = "${PPE_PROJECT}_database|local|${PPE_PROJECT}|database|$PPE_RECOVERY_DB_VOLUME_CREATED_AT" ]] || {
        echo "Existing DB volume identity differs; recovery will not create or replace it." >&2; return 1;
    }
    echo "Verified the pinned healthy DB container, image/config and volume; DB lifecycle was unchanged."
}
