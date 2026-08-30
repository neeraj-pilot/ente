import SwiftUI

public struct SheetContent<Content: View>: View {
    @Environment(\.entePalette) private var palette
    private let title: String?
    private let message: String?
    private let dismiss: (() -> Void)?
    private let content: Content

    public init(
        title: String? = nil,
        message: String? = nil,
        dismiss: (() -> Void)? = nil,
        @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.message = message
        self.dismiss = dismiss
        self.content = content()
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: EnteSpacing.lg) {
            if title != nil || dismiss != nil {
                HStack {
                    if let title {
                        Text(title)
                            .font(EnteTypography.heading2)
                            .foregroundStyle(palette.text)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 0)
                    if let dismiss {
                        IconAction(kind: .unfilled, size: 40, action: dismiss) {
                            Image(systemName: "xmark")
                        }
                    }
                }
            }
            if let message {
                Text(message)
                    .font(EnteTypography.body)
                    .foregroundStyle(palette.mutedText)
            }
            content
        }
        .padding(EnteSpacing.xl)
        .background(palette.background)
    }
}
