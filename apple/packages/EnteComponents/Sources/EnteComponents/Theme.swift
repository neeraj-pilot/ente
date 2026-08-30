import CoreText
import SwiftUI

public enum EnteApp {
    case photos
    case auth
    case locker
}

public struct Palette {
    public let isDark: Bool
    public let primary: Color
    public let primaryDark: Color
    public let primaryDarker: Color
    public let primarySurface: Color
    public let text: Color
    public let mutedText: Color
    public let hintText: Color
    public let disabledText: Color
    public let reverseText: Color
    public let background: Color
    public let surface: Color
    public let fill: Color
    public let fillDarker: Color
    public let fillDarkest: Color
    public let border: Color
    public let faintBorder: Color
    public let danger: Color
    public let dangerDark: Color
    public let dangerDarker: Color
    public let caution: Color
    public let information: Color

    static func make(app: EnteApp, scheme: ColorScheme) -> Palette {
        let dark = scheme == .dark
        let primary: Color
        let primaryDark: Color
        let primaryDarker: Color
        let primarySurface: Color

        switch app {
        case .photos:
            primary = Color(hex: 0x08C225)
            primaryDark = Color(hex: 0x069D1E)
            primaryDarker = Color(hex: 0x057C18)
            primarySurface = Color(hex: dark ? 0x292929 : 0xDDEEDF)
        case .auth:
            primary = Color(hex: 0x9610D6)
            primaryDark = Color(hex: 0x7A0CAE)
            primaryDarker = Color(hex: 0x5D0884)
            primarySurface = Color(hex: dark ? 0x271C32 : 0xF4E7FC)
        case .locker:
            primary = Color(hex: 0x1071FF)
            primaryDark = Color(hex: 0x0E5FD9)
            primaryDarker = Color(hex: 0x0B4CAD)
            primarySurface = Color(hex: dark ? 0x292929 : 0xE7EFFA)
        }

        return Palette(
            isDark: dark,
            primary: primary,
            primaryDark: primaryDark,
            primaryDarker: primaryDarker,
            primarySurface: primarySurface,
            text: dark ? .white : .black,
            mutedText: Color(hex: dark ? 0x999999 : 0x666666),
            hintText: Color(hex: 0x969696),
            disabledText: Color(hex: dark ? 0x414141 : 0xD6D6D6),
            reverseText: dark ? .black : .white,
            background: Color(hex: dark ? 0x161616 : 0xF4F4F4),
            surface: Color(hex: dark ? 0x212121 : 0xFFFFFF),
            fill: Color(hex: dark ? 0x0A0A0A : 0xEAEAEA),
            fillDarker: Color(hex: dark ? 0x141414 : 0xDEDEDE),
            fillDarkest: Color(hex: dark ? 0x292929 : 0xD2D2D2),
            border: Color(hex: dark ? 0x3E3E3E : 0xE0E0E0),
            faintBorder: Color(hex: dark ? 0x2A2A2A : 0xEBEBEB),
            danger: Color(hex: 0xF63A3A),
            dangerDark: Color(hex: 0xDD3434),
            dangerDarker: Color(hex: 0xC52E2E),
            caution: Color(hex: 0xF08A1E),
            information: Color(hex: 0x1071FF)
        )
    }
}

public enum EnteSpacing {
    public static let xs: CGFloat = 4
    public static let sm: CGFloat = 8
    public static let md: CGFloat = 12
    public static let lg: CGFloat = 16
    public static let xl: CGFloat = 20
    public static let xxl: CGFloat = 24
}

public enum EnteRadius {
    public static let small: CGFloat = 8
    public static let medium: CGFloat = 12
    public static let large: CGFloat = 16
    public static let button: CGFloat = 20
    public static let sheet: CGFloat = 24
}

public enum EnteIconSize {
    public static let micro: CGFloat = 8
    public static let tiny: CGFloat = 12
    public static let small: CGFloat = 18
    public static let medium: CGFloat = 24
    public static let large: CGFloat = 36
}

public enum EnteMotion {
    public static let quick = 0.12
    public static let standard = 0.18
    public static let slow = 0.26
}

public enum EnteTypography {
    public static let display1 = EnteFont.outfit(size: 32, relativeTo: .largeTitle)
    public static let display2 = EnteFont.outfit(size: 24, relativeTo: .title)
    public static let heading1 = EnteFont.inter(size: 20, weight: .bold, relativeTo: .title2)
    public static let heading2 = EnteFont.inter(size: 18, weight: .semibold, relativeTo: .title3)
    public static let large = EnteFont.inter(size: 16, weight: .semibold, relativeTo: .body)
    public static let body = EnteFont.inter(size: 14, weight: .medium, relativeTo: .body)
    public static let bodyBold = EnteFont.inter(size: 14, weight: .semibold, relativeTo: .body)
    public static let mini = EnteFont.inter(size: 12, weight: .medium, relativeTo: .caption)
    public static let tiny = EnteFont.inter(size: 10, weight: .medium, relativeTo: .caption2)
    public static let avatarExtraSmall = EnteFont.inter(size: 8, weight: .medium, relativeTo: .caption2)
    public static let avatarSmall = EnteFont.inter(size: 10, weight: .medium, relativeTo: .caption2)
}

public struct EnteTheme<Content: View>: View {
    @Environment(\.colorScheme) private var colorScheme
    private let app: EnteApp
    private let content: Content

    public init(app: EnteApp = .photos, @ViewBuilder content: () -> Content) {
        self.app = app
        self.content = content()
    }

    public var body: some View {
        let palette = Palette.make(app: app, scheme: colorScheme)
        content
            .environment(\.entePalette, palette)
            .tint(palette.primary)
    }
}

public extension EnvironmentValues {
    var entePalette: Palette {
        get { self[PaletteKey.self] }
        set { self[PaletteKey.self] = newValue }
    }
}

private struct PaletteKey: EnvironmentKey {
    static let defaultValue = Palette.make(app: .photos, scheme: .light)
}

private enum EnteFont {
    static func inter(size: CGFloat, weight: Font.Weight, relativeTo: Font.TextStyle) -> Font {
        let name = switch weight {
        case .bold: "Inter-Bold"
        case .semibold: "Inter-SemiBold"
        case .medium: "Inter-Medium"
        default: "Inter-Regular"
        }
        return custom(name, size: size, relativeTo: relativeTo)
    }

    static func outfit(size: CGFloat, relativeTo: Font.TextStyle) -> Font {
        custom("Outfit-SemiBold", size: size, relativeTo: relativeTo)
    }

    private static func custom(_ name: String, size: CGFloat, relativeTo: Font.TextStyle) -> Font {
        _ = registration
        return .custom(name, size: size, relativeTo: relativeTo)
    }

    private static let registration: Void = {
        ["Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold", "Outfit-SemiBold"].forEach { name in
            guard let url = Bundle.module.url(forResource: name, withExtension: "ttf") else { return }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()
}

extension Color {
    init(hex: UInt) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}
