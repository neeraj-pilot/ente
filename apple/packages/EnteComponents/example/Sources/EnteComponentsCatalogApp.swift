import EnteComponents
import SwiftUI

@main
struct EnteComponentsCatalogApp: App {
    var body: some Scene {
        WindowGroup { Catalog() }
    }
}

private struct Catalog: View {
    @State private var dark = false
    @State private var album = ""
    @State private var chipSelected = true
    @State private var tagSelected = true
    @State private var checked = true
    @State private var notifications = true
    @State private var sheetPresented = false

    var body: some View {
        EnteTheme {
            CatalogContent(
                dark: $dark,
                album: $album,
                chipSelected: $chipSelected,
                tagSelected: $tagSelected,
                checked: $checked,
                notifications: $notifications,
                sheetPresented: $sheetPresented
            )
        }
        .preferredColorScheme(dark ? .dark : .light)
    }
}

private struct CatalogContent: View {
    @Environment(\.entePalette) private var palette
    @Binding var dark: Bool
    @Binding var album: String
    @Binding var chipSelected: Bool
    @Binding var tagSelected: Bool
    @Binding var checked: Bool
    @Binding var notifications: Bool
    @Binding var sheetPresented: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: EnteSpacing.xl) {
                HStack {
                    Text("Components")
                        .font(EnteTypography.display2)
                        .foregroundStyle(palette.text)
                    Spacer()
                    IconAction(action: { dark.toggle() }) {
                        Image(systemName: dark ? "sun.max" : "moon")
                    }
                }
                Text("A compact visual catalog for the shared native layer.")
                    .font(EnteTypography.body)
                    .foregroundStyle(palette.mutedText)

                Section("Identity") {
                    HStack(spacing: EnteSpacing.md) {
                        Avatar("Aman Gupta", identity: "aman@example.com", size: .contact)
                        VStack(alignment: .leading, spacing: EnteSpacing.xs) {
                            Text("Aman Gupta").font(EnteTypography.large)
                            Text("aman@example.com").font(EnteTypography.mini).foregroundStyle(palette.mutedText)
                        }
                    }
                }

                Section("Choices") {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: EnteSpacing.sm) {
                            FilterChip("Recent", selected: chipSelected) { chipSelected.toggle() }
                            FilterChip("Favorites", selected: !chipSelected) { chipSelected.toggle() }
                            Tag("Private", selected: tagSelected) { tagSelected.toggle() }
                        }
                    }
                }

                Section("Input") {
                    InputField(
                        text: $album,
                        label: "Album name",
                        required: true,
                        placeholder: "Summer 2026",
                        message: "Visible only to people you share it with."
                    )
                }

                Section("Controls") {
                    VStack(alignment: .leading, spacing: EnteSpacing.sm) {
                        LabeledControl("Include metadata", subtitle: "Location and camera details") {
                            Checkmark(isOn: $checked)
                        }
                        Toggle("Notifications", isOn: $notifications)
                            .font(EnteTypography.body)
                            .foregroundStyle(palette.text)
                            .toggleStyle(EnteToggleStyle())
                        LabeledControl("Keep original quality") {
                            Radio(selected: true) {}
                        }
                    }
                }

                Section("Feedback") {
                    Banner(
                        "Backup is protected",
                        subtitle: "Your photos are encrypted before upload.",
                        kind: .success,
                        systemImage: "checkmark.shield"
                    ) {}
                }

                Section("Rows") {
                    MenuGroup {
                        VStack(spacing: 0) {
                            MenuRow("Account", subtitle: "aman@example.com", systemImage: "person") {}
                            EnteDivider()
                            MenuRow("Storage", subtitle: "2.4 GB of 10 GB", systemImage: "internaldrive") {}
                        }
                    }
                    SettingsRow("Delete account", systemImage: "trash", destructive: true) {}
                }

                Section("Actions") {
                    HStack(spacing: EnteSpacing.sm) {
                        ActionButton("Save", fullWidth: true) {}
                        ActionButton("Cancel", kind: .secondary, fullWidth: true) {}
                    }
                    ActionButton("Open sheet", kind: .link) { sheetPresented = true }
                }
            }
            .padding(EnteSpacing.lg)
        }
        .background(palette.background)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .overlay(alignment: .bottomTrailing) {
            FloatingAction(action: { sheetPresented = true }) {
                Image(systemName: "plus")
            }
            .padding(EnteSpacing.lg)
        }
        .sheet(isPresented: $sheetPresented) {
            EnteTheme {
                SheetContent(
                    title: "Create album",
                    message: "Albums keep your library organized.",
                    dismiss: { sheetPresented = false }
                ) {
                    ActionButton("Create album") { sheetPresented = false }
                }
            }
            .presentationDetents([.medium])
        }
    }
}

private struct Section<Content: View>: View {
    @Environment(\.entePalette) private var palette
    let title: String
    @ViewBuilder let content: Content

    init(_ title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: EnteSpacing.md) {
            Text(title)
                .font(EnteTypography.heading2)
                .foregroundStyle(palette.text)
            content
        }
    }
}
