import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import {
    existsSync,
    mkdirSync,
    mkdtempSync,
    readFileSync,
    renameSync,
    rmSync,
    symlinkSync,
    writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import test from "node:test";
import { pathToFileURL } from "node:url";

import { selectMobileTests } from "./select.mjs";

const root = resolve(import.meta.dirname, "../../..");
const script = join(import.meta.dirname, "select.mjs");
const select = (...files) => selectMobileTests(root, files);
const all = select();

test("all selects every member with tests", () => {
    for (const member of ["apps/auth", "apps/photos", "packages/legacy"])
        assert.ok(all.includes(member), member);
    assert.ok(!all.includes("packages/frb"));
});

test("mobile changes select their member and its tested dependents", () => {
    assert.deepEqual(select("mobile/apps/auth/lib/main.dart"), ["apps/auth"]);
    assert.deepEqual(select("mobile/apps/auth/assets/simple-icons"), [
        "apps/auth",
    ]);
    assert.deepEqual(
        select("mobile/apps/photos/plugins/ente_cast/lib/cast.dart"),
        ["apps/photos", "apps/photos/plugins/ente_cast"],
    );
    assert.deepEqual(
        select("mobile/packages/ente_components/example/lib/main.dart"),
        [],
    );
    assert.deepEqual(select("mobile/packages/legacy/lib/legacy.dart"), [
        "apps/locker",
        "packages/legacy",
    ]);
});

test("Rust changes select the members of the FRB crates they feed", () => {
    assert.deepEqual(select("rust/crates/auth/src/lib.rs"), ["apps/auth"]);
    assert.deepEqual(select("rust/bindings/frb/locker/src/lib.rs"), [
        "apps/locker",
    ]);
    assert.deepEqual(select("rust/crates/ml/src/lib.rs"), [
        "apps/locker",
        "apps/photos",
    ]);
    assert.deepEqual(
        select("rust/crates/photos/src/lib.rs", "mobile/apps/auth/lib/a.dart"),
        ["apps/auth", "apps/photos"],
    );
    assert.deepEqual(select("rust/bindings/frb/lib/src/lib.rs"), [
        "apps/auth",
        "apps/locker",
        "apps/photos",
        "packages/contacts",
        "packages/legacy",
        "packages/lock_screen",
        "packages/sharing",
        "packages/ui",
    ]);
});

test("selects Rust owners through a symlinked root", (t) => {
    const dir = mkdtempSync(join(tmpdir(), "ente-mobile-symlink-"));
    t.after(() => rmSync(dir, { recursive: true }));
    const link = join(dir, "repo");
    symlinkSync(root, link);
    assert.deepEqual(selectMobileTests(link, ["rust/crates/auth/src/lib.rs"]), [
        "apps/auth",
    ]);
});

test("evaluates as a module import without process.argv[1]", () => {
    const result = spawnSync(
        process.execPath,
        ["-e", "await import(process.env.SCRIPT)"],
        {
            encoding: "utf8",
            env: { ...process.env, SCRIPT: pathToFileURL(script).href },
        },
    );
    assert.equal(result.status, 0, result.stderr);
    assert.equal(result.stderr, "");
});

test("files outside every member and FRB crate select all", () => {
    for (const file of [
        "mobile/pubspec.lock",
        "rust/.cargo/config.toml",
        "rust/Cargo.lock",
        "rust/Cargo.toml",
        "rust/tools/codegen/src/main.rs",
        "rust/crates/test-support/src/lib.rs",
        ".github/actions/restore-frb-outputs/action.yml",
    ]) {
        assert.deepEqual(
            select(file, "mobile/apps/auth/lib/main.dart"),
            all,
            file,
        );
    }
});

test("CLI selects from the mobile-lint diff", (t) => {
    const cwd = mkdtempSync(join(tmpdir(), "ente-mobile-tests-"));
    t.after(() => rmSync(cwd, { recursive: true }));
    const write = (path, content) => {
        mkdirSync(dirname(join(cwd, path)), { recursive: true });
        writeFileSync(join(cwd, path), content);
    };
    const tree = () => {
        execFileSync("git", ["add", "-A"], { cwd });
        return execFileSync("git", ["write-tree"], {
            cwd,
            encoding: "utf8",
        }).trim();
    };
    execFileSync("git", ["init", "-q"], { cwd });
    write(
        "mobile/pubspec.yaml",
        "workspace:\n  - apps/auth\n  - apps/photos\n  - packages/core\n",
    );
    write(
        "mobile/apps/auth/pubspec.yaml",
        "name: ente_auth\ndependencies:\n  core: 1.0.0\n",
    );
    write("mobile/apps/photos/pubspec.yaml", "name: photos\n");
    write("mobile/packages/core/pubspec.yaml", "name: core\n");
    for (const member of ["apps/auth", "apps/photos", "packages/core"])
        write(`mobile/${member}/test/a_test.dart`, "void main() {}\n");
    write("mobile/apps/photos/lib/a.dart", "const a = 1;\n");
    const base = tree();
    write("README.md", "");
    const readme = tree();
    write("mobile/packages/core/lib/core.dart", "");
    const core = tree();
    mkdirSync(join(cwd, "mobile/apps/auth/lib"));
    renameSync(
        join(cwd, "mobile/apps/photos/lib/a.dart"),
        join(cwd, "mobile/apps/auth/lib/a.dart"),
    );
    const moved = tree();

    const output = (packages, auth, photos) =>
        `packages=${JSON.stringify(packages)}\n` +
        `build_auth_frb=${auth}\nbuild_photos_frb=${photos}\n`;
    const everything = ["apps/auth", "apps/photos", "packages/core"];
    assert.equal(cli(cwd, ["--all"]), output(everything, true, true));
    assert.equal(cli(cwd, [base, readme]), output(everything, true, true));
    assert.equal(
        cli(cwd, [base, core]),
        output(["apps/auth", "packages/core"], true, false),
    );
    assert.equal(
        cli(cwd, [core, moved]),
        output(["apps/auth", "apps/photos"], true, true),
    );
});

test("CLI fails on bad revisions and arguments without output", (t) => {
    const cwd = mkdtempSync(join(tmpdir(), "ente-mobile-tests-"));
    t.after(() => rmSync(cwd, { recursive: true }));
    execFileSync("git", ["init", "-q"], { cwd });
    for (const [args, error] of [
        [["not-a-revision", "HEAD"], /bad revision/],
        [["HEAD"], /Usage: select\.mjs --all \| BASE HEAD/],
        [["--all", "HEAD", "HEAD"], /Usage/],
    ]) {
        const result = run(cwd, args);
        assert.equal(result.status, 1);
        assert.match(result.stderr, error);
        assert.equal(existsSync(join(cwd, "output")), false);
    }
});

function cli(cwd, args) {
    const result = run(cwd, args);
    assert.equal(result.status, 0, result.stderr);
    const output = readFileSync(join(cwd, "output"), "utf8");
    rmSync(join(cwd, "output"));
    return output;
}

function run(cwd, args) {
    return spawnSync(process.execPath, [script, ...args], {
        cwd,
        encoding: "utf8",
        env: { ...process.env, GITHUB_OUTPUT: join(cwd, "output") },
    });
}
