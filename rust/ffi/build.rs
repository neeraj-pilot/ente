fn main() {
    uniffi::generate_scaffolding("src/ente_ffi.udl")
        .expect("failed to generate UniFFI scaffolding");
}
