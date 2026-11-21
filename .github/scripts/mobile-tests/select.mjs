import { execFileSync } from "node:child_process";
import {
    appendFileSync,
    existsSync,
    readFileSync,
    realpathSync,
} from "node:fs";
import { dirname, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const DEPENDENCY_SECTIONS = new Set([
    "dependencies",
    "dev_dependencies",
    "dependency_overrides",
]);

const RUST_FRB_TARGETS = [
    ["ente-frb-lib", "packages/frb"],
    ["ente-auth-frb", "apps/auth"],
    ["ente-photos-frb", "apps/photos"],
    ["ente-locker-frb", "apps/locker"],
];

export function selectMobileTests(root, changedFiles) {
    const { members, dependents, tested } = loadWorkspace(root);
    if (!changedFiles.length) return tested;
    const owners = [
        ...members.map((member) => [`mobile/${member}`, [member]]),
        ...(changedFiles.some((file) => file.startsWith("rust/"))
            ? rustOwners(root)
            : []),
    ].sort(([a], [b]) => b.length - a.length);
    const selected = new Set();
    for (const file of changedFiles) {
        const owned =
            owners.find(([dir]) => file.startsWith(`${dir}/`))?.[1] ?? [];
        if (!owned.length) return tested;
        for (const member of owned) selected.add(member);
    }
    for (const member of selected)
        for (const dependent of dependents.get(member)) selected.add(dependent);
    return tested.filter((member) => selected.has(member));
}

function loadWorkspace(root) {
    const mobile = resolve(root, "mobile");
    const members = parseWorkspaceMembers(
        readFileSync(resolve(mobile, "pubspec.yaml"), "utf8"),
    );
    const byName = new Map();
    const dependencies = new Map();
    for (const member of members) {
        const pubspecPath = resolve(mobile, member, "pubspec.yaml");
        const source = readFileSync(pubspecPath, "utf8");
        byName.set(parsePubspecName(source, pubspecPath), member);
        dependencies.set(member, parsePubspecDependencies(source));
    }
    const dependents = new Map(members.map((member) => [member, []]));
    for (const [member, names] of dependencies)
        for (const name of names)
            if (byName.has(name)) dependents.get(byName.get(name)).push(member);
    const tested = members
        .filter((member) => existsSync(resolve(mobile, member, "test")))
        .sort();
    return { members, dependents, tested };
}

function rustOwners(root) {
    const { packages } = JSON.parse(
        execFileSync(
            "cargo",
            [
                "metadata",
                "--no-deps",
                "--format-version",
                "1",
                "--locked",
                "--offline",
            ],
            { cwd: resolve(root, "rust"), encoding: "utf8" },
        ),
    );
    const byDirectory = new Map(
        packages.map((pkg) => [dirname(pkg.manifest_path), pkg]),
    );
    const owners = new Map([...byDirectory.keys()].map((dir) => [dir, []]));
    for (const [crate, member] of RUST_FRB_TARGETS) {
        const closure = new Set([
            dirname(packages.find((pkg) => pkg.name === crate).manifest_path),
        ]);
        for (const dir of closure) {
            owners.get(dir).push(member);
            for (const dependency of byDirectory.get(dir).dependencies)
                if (dependency.path && dependency.kind !== "dev")
                    closure.add(dependency.path);
        }
    }
    return [...owners].map(([dir, members]) => [relative(root, dir), members]);
}

function parseWorkspaceMembers(source) {
    const members = [];
    let inWorkspace = false;
    for (const raw of source.split(/\r?\n/)) {
        const line = raw.replace(/(?:^|\s)#.*$/, "").trimEnd();
        if (!line.trim()) continue;
        const indent = line.match(/^ */)[0].length;
        if (indent === 0) {
            inWorkspace = /^workspace:\s*$/.test(line);
            continue;
        }
        if (!inWorkspace) continue;
        const match = line.match(/^\s+-\s+(.+)$/);
        if (match) {
            members.push(stripQuotes(match[1].trim()));
        }
    }
    return members;
}

function parsePubspecName(source, filePath) {
    for (const raw of source.split(/\r?\n/)) {
        const line = raw.replace(/(?:^|\s)#.*$/, "").trimEnd();
        const match = line.match(/^name:\s*(.+)$/);
        if (match) {
            return stripQuotes(match[1].trim());
        }
    }
    throw new Error(`${filePath}: missing package name`);
}

function parsePubspecDependencies(source) {
    const deps = new Set();
    let section = null;
    let childIndent = null;
    for (const raw of source.split(/\r?\n/)) {
        const line = raw.replace(/(?:^|\s)#.*$/, "").trimEnd();
        if (!line.trim()) continue;
        const indent = line.match(/^ */)[0].length;
        if (indent === 0) {
            const header = line.match(/^([A-Za-z0-9_]+):/);
            section = header ? header[1] : null;
            childIndent = null;
            continue;
        }
        if (!DEPENDENCY_SECTIONS.has(section)) continue;
        childIndent ??= indent;
        if (indent === childIndent) {
            const match = line.match(/^\s+([A-Za-z0-9_-]+)\s*:/);
            if (match) deps.add(match[1]);
        }
    }
    return deps;
}

function stripQuotes(value) {
    if (
        (value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))
    ) {
        return value.slice(1, -1);
    }
    return value;
}

function changedFiles(base, head) {
    const paths = JSON.parse(
        readFileSync(new URL("../ci/paths.json", import.meta.url), "utf8"),
    );
    return execFileSync(
        "git",
        [
            "diff",
            "--name-only",
            "--no-renames",
            "-z",
            base,
            head,
            "--",
            ...paths["mobile-lint"].map((pattern) => `:(top,glob)${pattern}`),
        ],
        { encoding: "utf8" },
    )
        .split("\0")
        .filter(Boolean);
}

if (realpathSync(process.argv[1]) === fileURLToPath(import.meta.url)) {
    const args = process.argv.slice(2);
    const all = args.length === 1 && args[0] === "--all";
    if (!all && args.length !== 2)
        throw new Error("Usage: select.mjs --all | BASE HEAD");
    const packages = selectMobileTests(
        process.cwd(),
        all ? [] : changedFiles(...args),
    );
    console.log(`Selected mobile tests: ${packages.join(", ") || "none"}`);
    if (process.env.GITHUB_OUTPUT)
        appendFileSync(
            process.env.GITHUB_OUTPUT,
            `packages=${JSON.stringify(packages)}\n` +
                `build_auth_frb=${packages.includes("apps/auth")}\n` +
                `build_photos_frb=${packages.includes("apps/photos")}\n`,
        );
}
