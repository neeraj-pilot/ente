import SwiftUI

public enum InputMessageKind {
    case helper
    case error
    case alert
    case success
}

public struct InputField: View {
    @Binding private var text: String
    @FocusState private var focused: Bool
    @Environment(\.entePalette) private var palette
    private let label: String?
    private let required: Bool
    private let placeholder: String
    private let message: String?
    private let messageKind: InputMessageKind
    private let secure: Bool
    private let enabled: Bool

    public init(
        text: Binding<String>,
        label: String? = nil,
        required: Bool = false,
        placeholder: String = "",
        message: String? = nil,
        messageKind: InputMessageKind = .helper,
        secure: Bool = false,
        enabled: Bool = true
    ) {
        _text = text
        self.label = label
        self.required = required
        self.placeholder = placeholder
        self.message = message
        self.messageKind = messageKind
        self.secure = secure
        self.enabled = enabled
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let label {
                HStack(spacing: 2) {
                    Text(label)
                        .font(EnteTypography.body)
                        .foregroundStyle(palette.text)
                    if required {
                        Text("*")
                            .font(EnteTypography.bodyBold)
                            .foregroundStyle(palette.danger)
                    }
                }
                .padding(.bottom, EnteSpacing.sm)
            }
            Group {
                if secure {
                    SecureField(
                        "",
                        text: $text,
                        prompt: Text(placeholder)
                            .font(EnteTypography.body)
                            .foregroundColor(palette.hintText)
                    )
                } else {
                    TextField(
                        "",
                        text: $text,
                        prompt: Text(placeholder)
                            .font(EnteTypography.body)
                            .foregroundColor(palette.hintText)
                    )
                }
            }
            .focused($focused)
            .font(EnteTypography.body)
            .foregroundStyle(enabled ? palette.text : palette.disabledText)
            .tint(palette.primary)
            .padding(.horizontal, EnteSpacing.lg)
            .frame(minHeight: 52)
            .background(enabled ? palette.surface : palette.fill)
            .clipShape(RoundedRectangle(cornerRadius: EnteRadius.large, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: EnteRadius.large, style: .continuous)
                    .stroke(borderColor, lineWidth: 1)
            }
            .disabled(!enabled)
            if let message {
                Text(message)
                    .font(EnteTypography.mini)
                    .foregroundStyle(messageColor)
                    .padding(.top, EnteSpacing.sm)
            }
        }
    }

    private var borderColor: Color {
        switch messageKind {
        case .error, .alert: palette.danger
        case .success: palette.primary
        case .helper: focused ? palette.hintText : palette.faintBorder
        }
    }

    private var messageColor: Color {
        switch messageKind {
        case .helper: palette.mutedText
        case .error, .alert: palette.danger
        case .success: palette.primary
        }
    }
}
