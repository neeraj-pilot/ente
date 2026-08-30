import SwiftUI

public enum BannerKind {
    case failure
    case information
    case success
    case warning
    case neutral
}

public struct FilterChip: View {
    @Environment(\.entePalette) private var palette
    private let label: String
    private let systemImage: String?
    private let selected: Bool
    private let enabled: Bool
    private let action: () -> Void

    public init(
        _ label: String,
        systemImage: String? = nil,
        selected: Bool,
        enabled: Bool = true,
        action: @escaping () -> Void
    ) {
        self.label = label
        self.systemImage = systemImage
        self.selected = selected
        self.enabled = enabled
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: EnteSpacing.sm) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .frame(width: 16, height: 16)
                }
                Text(label)
                if selected {
                    Image(systemName: "xmark")
                        .font(.system(size: 14, weight: .medium))
                        .frame(width: 14, height: 14)
                }
            }
                .font(EnteTypography.mini)
                .lineLimit(1)
                .padding(.leading, systemImage == nil ? 18 : EnteSpacing.md)
                .padding(.trailing, selected ? EnteSpacing.md : (systemImage == nil ? 18 : EnteSpacing.lg))
                .padding(.vertical, EnteSpacing.md)
                .frame(minHeight: 40)
        }
        .buttonStyle(.plain)
        .foregroundStyle(foreground)
        .background(background)
        .clipShape(Capsule())
        .disabled(!enabled)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private var background: Color {
        selected ? (palette.isDark ? Color(hex: 0xF4F4F4) : Color(hex: 0x161616)) : palette.surface
    }

    private var foreground: Color {
        selected ? (palette.isDark ? .black : .white) : (enabled ? palette.mutedText : palette.disabledText)
    }
}

public struct Banner: View {
    @Environment(\.entePalette) private var palette
    private let title: String
    private let subtitle: String?
    private let kind: BannerKind
    private let systemImage: String?
    private let action: (() -> Void)?

    public init(
        _ title: String,
        subtitle: String? = nil,
        kind: BannerKind = .neutral,
        systemImage: String? = nil,
        action: (() -> Void)? = nil
    ) {
        self.title = title
        self.subtitle = subtitle
        self.kind = kind
        self.systemImage = systemImage
        self.action = action
    }

    public var body: some View {
        Group {
            if let action {
                Button(action: action) { content }
                    .buttonStyle(.plain)
            } else {
                content
            }
        }
        .background(palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: EnteRadius.button, style: .continuous))
    }

    private var content: some View {
        HStack(spacing: EnteSpacing.lg) {
            if let systemImage {
                Image(systemName: systemImage)
                    .frame(width: 24, height: 24)
                    .foregroundStyle(accent)
            }
            VStack(alignment: .leading, spacing: EnteSpacing.xs) {
                Text(title)
                    .font(EnteTypography.bodyBold)
                    .foregroundStyle(kind == .neutral ? palette.text : accent)
                    .lineLimit(subtitle == nil ? 2 : 1)
                if let subtitle {
                    Text(subtitle)
                        .font(EnteTypography.mini)
                        .foregroundStyle(palette.mutedText)
                        .lineLimit(2)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, EnteSpacing.lg)
        .padding(.vertical, EnteSpacing.md)
        .frame(maxWidth: .infinity, minHeight: 66, alignment: .leading)
    }

    private var accent: Color {
        switch kind {
        case .failure: palette.danger
        case .information: palette.information
        case .success, .neutral: palette.primaryDark
        case .warning: palette.caution
        }
    }
}

public struct Tag: View {
    @Environment(\.entePalette) private var palette
    private let label: String
    private let selected: Bool
    private let enabled: Bool
    private let action: () -> Void

    public init(
        _ label: String,
        selected: Bool,
        enabled: Bool = true,
        action: @escaping () -> Void
    ) {
        self.label = label
        self.selected = selected
        self.enabled = enabled
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Text(label)
                .font(EnteTypography.body)
                .lineLimit(1)
                .padding(.horizontal, EnteSpacing.xl)
                .padding(.vertical, EnteSpacing.md)
                .frame(minHeight: 44)
        }
        .buttonStyle(.plain)
        .foregroundStyle(selected ? .white : (enabled ? palette.mutedText : palette.disabledText))
        .background(selected ? palette.primary : palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: EnteRadius.large, style: .continuous))
        .disabled(!enabled)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

public struct MenuRow: View {
    @Environment(\.entePalette) private var palette
    private let title: String
    private let subtitle: String?
    private let systemImage: String?
    private let selected: Bool
    private let enabled: Bool
    private let action: () -> Void

    public init(
        _ title: String,
        subtitle: String? = nil,
        systemImage: String? = nil,
        selected: Bool = false,
        enabled: Bool = true,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.subtitle = subtitle
        self.systemImage = systemImage
        self.selected = selected
        self.enabled = enabled
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: EnteSpacing.md) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .frame(width: 36, height: 36)
                        .foregroundStyle(palette.mutedText)
                }
                VStack(alignment: .leading, spacing: EnteSpacing.xs) {
                    Text(title)
                        .font(EnteTypography.body)
                        .foregroundStyle(enabled ? palette.text : palette.disabledText)
                        .lineLimit(subtitle == nil ? 2 : 1)
                    if let subtitle {
                        Text(subtitle)
                            .font(EnteTypography.mini)
                            .foregroundStyle(palette.mutedText)
                            .lineLimit(2)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.leading, systemImage == nil ? EnteSpacing.lg : EnteSpacing.md)
            .padding(.trailing, EnteSpacing.md)
            .padding(.vertical, 9)
            .frame(minHeight: 58)
        }
        .buttonStyle(.plain)
        .background(palette.surface)
        .overlay {
            RoundedRectangle(cornerRadius: EnteRadius.button, style: .continuous)
                .stroke(selected ? palette.border : .clear, lineWidth: 1)
        }
        .clipShape(RoundedRectangle(cornerRadius: EnteRadius.button, style: .continuous))
        .disabled(!enabled)
    }
}

public struct SettingsRow: View {
    @Environment(\.entePalette) private var palette
    private let title: String
    private let subtitle: String?
    private let systemImage: String?
    private let destructive: Bool
    private let action: () -> Void

    public init(
        _ title: String,
        subtitle: String? = nil,
        systemImage: String? = nil,
        destructive: Bool = false,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.subtitle = subtitle
        self.systemImage = systemImage
        self.destructive = destructive
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: EnteSpacing.lg) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .frame(width: 24, height: 24)
                        .foregroundStyle(destructive ? palette.danger : palette.mutedText)
                }
                VStack(alignment: .leading, spacing: 0) {
                    Text(title)
                        .font(EnteTypography.body)
                        .foregroundStyle(destructive ? palette.danger : palette.text)
                        .lineLimit(2)
                    if let subtitle {
                        Text(subtitle)
                            .font(EnteTypography.mini)
                            .foregroundStyle(palette.mutedText)
                            .lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(palette.mutedText)
            }
            .padding(.vertical, EnteSpacing.md)
        }
        .buttonStyle(.plain)
    }
}

public struct MenuGroup<Content: View>: View {
    @Environment(\.entePalette) private var palette
    private let content: Content

    public init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    public var body: some View {
        content
            .background {
                RoundedRectangle(cornerRadius: EnteRadius.button, style: .continuous)
                    .fill(palette.surface)
            }
            .clipShape(RoundedRectangle(cornerRadius: EnteRadius.button, style: .continuous))
    }
}

public struct EnteDivider: View {
    @Environment(\.entePalette) private var palette

    public init() {}

    public var body: some View {
        Divider().overlay(palette.faintBorder)
    }
}

public struct Avatar: View {
    @Environment(\.entePalette) private var palette
    private let name: String
    private let identity: String
    private let size: AvatarSize

    public init(_ name: String, identity: String? = nil, size: AvatarSize = .regular) {
        self.name = name
        self.identity = identity ?? name
        self.size = size
    }

    public var body: some View {
        Text(initials)
            .font(size.font)
            .foregroundStyle(Color.white)
            .frame(width: size.dimension, height: size.dimension)
            .background(colour)
            .clipShape(Circle())
            .overlay {
                Circle().stroke(palette.background, lineWidth: size.border)
            }
    }

    private var initials: String {
        let words = name.split(whereSeparator: { $0.isWhitespace })
        guard let first = words.first?.first else { return "?" }
        return String(first).uppercased() + (words.count > 1 ? String(words.last!.first!).uppercased() : "")
    }

    private var colour: Color {
        [palette.caution, palette.primary, Color(hex: 0xF24822), Color(hex: 0xDF61BB), Color(hex: 0x9610D6), Color(hex: 0x1071FF), Color(hex: 0x00B8D4)][identityHash % 7]
    }

    private var identityHash: Int {
        Int(identity.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().utf8.reduce(UInt32(0x811c9dc5)) { ($0 ^ UInt32($1)) &* 0x01000193 })
    }
}

public enum AvatarSize {
    case extraSmall
    case small
    case regular
    case medium
    case large
    case contact

    var dimension: CGFloat {
        switch self {
        case .extraSmall: 16
        case .small: 20
        case .regular: 24
        case .medium: 28
        case .large: 32
        case .contact: 56
        }
    }

    var font: Font {
        switch self {
        case .extraSmall: EnteTypography.avatarExtraSmall
        case .small: EnteTypography.avatarSmall
        case .regular, .medium, .large: EnteTypography.mini
        case .contact: EnteTypography.heading2
        }
    }

    var border: CGFloat {
        switch self {
        case .large, .contact: 2
        default: 1
        }
    }
}
