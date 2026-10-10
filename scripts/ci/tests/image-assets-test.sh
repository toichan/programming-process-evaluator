#!/usr/bin/env bash
set -euo pipefail
umask 077
scripts=$(cd "$(dirname "$0")/.." && pwd)
source "$scripts/image-build-common.sh"
root=$(mktemp -d)
trap 'rm -rf "$root"' EXIT
mkdir -p "$root/source/src/main/webapp/WEB-INF" "$root/source/src/main/resources/db/migration" "$root/inspection"
printf '<http-only>true</http-only>\n' > "$root/source/src/main/webapp/WEB-INF/web.xml"
printf 'CREATE TABLE synthetic (id INT);\n' > "$root/source/src/main/resources/db/migration/V1__synthetic.sql"
cp -R "$root/source/src" "$root/inspection/tools-src"
for war in app-war tools-war; do
    mkdir -p "$root/inspection/$war/WEB-INF/classes/db"
    cp "$root/source/src/main/webapp/WEB-INF/web.xml" "$root/inspection/$war/WEB-INF/web.xml"
    cp -R "$root/source/src/main/resources/db/migration" "$root/inspection/$war/WEB-INF/classes/db/migration"
done
sed '/<http-only>true<\/http-only>/a\
            <secure>true</secure>
' "$root/source/src/main/webapp/WEB-INF/web.xml" > "$root/inspection/app-war/WEB-INF/web.xml"
image_build_assets "$root/source" "$root/inspection"
reject() {
    if image_build_assets "$root/source" "$root/inspection" >/dev/null 2>&1; then
        echo "Invalid assets accepted: $1" >&2; exit 1
    fi
}
printf 'tampered\n' >> "$root/inspection/tools-src/main/resources/db/migration/V1__synthetic.sql"
reject tools-source
cp "$root/source/src/main/resources/db/migration/V1__synthetic.sql" "$root/inspection/tools-src/main/resources/db/migration/V1__synthetic.sql"
cp "$root/source/src/main/webapp/WEB-INF/web.xml" "$root/inspection/app-war/WEB-INF/web.xml"
reject secure-cookie
cp "$root/inspection/expected-web.xml" "$root/inspection/app-war/WEB-INF/web.xml"
printf 'unexpected\n' > "$root/inspection/app-war/extra"
reject war-difference
rm "$root/inspection/app-war/extra"
for war in app-war tools-war; do
    rm "$root/inspection/$war/WEB-INF/classes/db/migration/V1__synthetic.sql"
done
reject missing-migration
echo "Image assets: source/SQL, secure-cookie processing, WAR content and missing migrations PASS (5 scenarios)."
