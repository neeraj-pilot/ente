import EnteComponents
import SwiftUI

@main
struct EnteComponentsCatalogApp: App {
    var body: some Scene {
        WindowGroup {
            EnteTheme {
                Catalog()
            }
        }
    }
}

private struct Catalog: View {
    @Environment(\.entePalette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: EnteSpacing.sm) {
            Text("Components")
                .font(EnteTypography.display2)
                .foregroundStyle(palette.text)
            Text("Native foundations for Ente apps")
                .font(EnteTypography.body)
                .foregroundStyle(palette.mutedText)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding(EnteSpacing.lg)
        .background(palette.background)
    }
}
