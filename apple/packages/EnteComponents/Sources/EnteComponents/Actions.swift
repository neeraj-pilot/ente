import SwiftUI

public enum ActionKind {
    case primary
    case secondary
    case neutral
    case destructive
    case destructiveText
    case link
}

public enum ActionSize {
    case regular
    case compact
}

public enum IconActionKind {
    case primary
    case critical
    case unfilled
    case secondary
    case accent
    case circular
}

public enum FloatingActionKind {
    case primary
    case secondary
    case destructive
}

public struct ActionButton: View {
    @Environment(\.entePalette) private var palette
    private let title: String
    private let systemImage: String?
    private let kind: ActionKind
    private let size: ActionSize
    private let enabled: Bool
    private let fullWidth: Bool
    private let action: () -> Void

    public init(
        _ title: String,
        systemImage: String? = nil,
        kind: ActionKind = .primary,
        size: ActionSize = .regular,
        enabled: Bool = true,
        fullWidth: Bool = true,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.systemImage = systemImage
        self.kind = kind
        self.size = size
        self.enabled = enabled
        self.fullWidth = fullWidth
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Group {
                if let systemImage {
                    Label(title, systemImage: systemImage)
                } else {
                    Text(title)
                }
            }
                .lineLimit(2)
                .font(size == .regular ? EnteTypography.bodyBold : EnteTypography.body)
                .underline(kind == .destructiveText || kind == .link)
                .frame(maxWidth: fullWidth ? .infinity : nil, minHeight: 24)
                .padding(.horizontal, EnteSpacing.xl)
                .padding(.vertical, size == .regular ? 14 : 12)
                .frame(minHeight: 52)
        }
        .buttonStyle(ActionStyle(kind: kind, palette: palette))
        .disabled(!enabled)
    }
}

public struct IconAction<Content: View>: View {
    @Environment(\.entePalette) private var palette
    private let kind: IconActionKind
    private let enabled: Bool
    private let size: CGFloat
    private let action: () -> Void
    private let content: Content

    public init(
        kind: IconActionKind = .secondary,
        enabled: Bool = true,
        size: CGFloat = 40,
        action: @escaping () -> Void,
        @ViewBuilder content: () -> Content
    ) {
        self.kind = kind
        self.enabled = enabled
        self.size = size
        self.action = action
        self.content = content()
    }

    public var body: some View {
        Button(action: action) {
            content
                .frame(width: size, height: size)
        }
        .buttonStyle(IconActionStyle(kind: kind, palette: palette, cornerRadius: kind == .circular ? size / 2 : EnteRadius.medium))
        .disabled(!enabled)
    }
}

public struct FloatingAction<Content: View>: View {
    @Environment(\.entePalette) private var palette
    private let kind: FloatingActionKind
    private let action: () -> Void
    private let content: Content

    public init(
        kind: FloatingActionKind = .primary,
        action: @escaping () -> Void,
        @ViewBuilder content: () -> Content
    ) {
        self.kind = kind
        self.action = action
        self.content = content()
    }

    public var body: some View {
        Button(action: action) {
            content.frame(width: 52, height: 52)
        }
        .buttonStyle(FloatingActionStyle(kind: kind, palette: palette))
    }
}

private struct ActionStyle: ButtonStyle {
    @Environment(\.isEnabled) private var enabled
    let kind: ActionKind
    let palette: Palette

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(foreground(configuration.isPressed))
            .background(background(configuration.isPressed))
            .clipShape(RoundedRectangle(cornerRadius: EnteRadius.button, style: .continuous))
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.easeOut(duration: EnteMotion.quick), value: configuration.isPressed)
    }

    private func background(_ pressed: Bool) -> Color {
        if !enabled {
            return kind == .destructiveText || kind == .link ? .clear : palette.fill
        }
        return switch kind {
        case .primary: pressed ? palette.primaryDarker : palette.primary
        case .secondary: pressed ? palette.fillDarkest : palette.fill
        case .neutral: palette.text
        case .destructive: pressed ? palette.dangerDarker : palette.danger
        case .destructiveText, .link: .clear
        }
    }

    private func foreground(_ pressed: Bool) -> Color {
        if !enabled { return palette.disabledText }
        return switch kind {
        case .primary, .destructive: .white
        case .secondary: palette.text
        case .neutral: palette.reverseText
        case .destructiveText: pressed ? palette.dangerDarker : palette.danger
        case .link: pressed ? palette.primaryDarker : palette.primary
        }
    }
}

private struct IconActionStyle: ButtonStyle {
    @Environment(\.isEnabled) private var enabled
    let kind: IconActionKind
    let palette: Palette
    let cornerRadius: CGFloat

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(foreground)
            .background(background(configuration.isPressed))
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.easeOut(duration: EnteMotion.quick), value: configuration.isPressed)
    }

    private func background(_ pressed: Bool) -> Color {
        if !enabled {
            return kind == .unfilled || kind == .secondary ? .clear : palette.fill
        }
        return switch kind {
        case .primary, .circular: pressed ? palette.fillDarker : palette.surface
        case .critical: pressed ? palette.fillDarkest : palette.fill
        case .unfilled, .secondary: .clear
        case .accent: pressed ? palette.primaryDarker : palette.primary
        }
    }

    private var foreground: Color {
        if !enabled { return palette.hintText }
        return switch kind {
        case .accent: .white
        case .unfilled, .secondary: palette.text.opacity(palette.isDark ? 1 : 0.75)
        case .primary, .critical, .circular: palette.text
        }
    }
}

private struct FloatingActionStyle: ButtonStyle {
    let kind: FloatingActionKind
    let palette: Palette

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(foreground)
            .background(background(configuration.isPressed))
            .clipShape(Circle())
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.easeOut(duration: EnteMotion.quick), value: configuration.isPressed)
    }

    private func background(_ pressed: Bool) -> Color {
        return switch kind {
        case .primary: pressed ? palette.primaryDark : palette.primary
        case .secondary: pressed ? palette.fillDarker : palette.fill
        case .destructive: pressed ? palette.dangerDark : palette.danger
        }
    }

    private var foreground: Color {
        return switch kind {
        case .secondary: palette.primary
        case .primary, .destructive: .white
        }
    }
}
