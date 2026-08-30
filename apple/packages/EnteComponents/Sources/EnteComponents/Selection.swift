import SwiftUI

public struct Checkmark: View {
    @Binding private var isOn: Bool
    @Environment(\.entePalette) private var palette
    private let enabled: Bool

    public init(isOn: Binding<Bool>, enabled: Bool = true) {
        _isOn = isOn
        self.enabled = enabled
    }

    public var body: some View {
        let fill = isOn ? (enabled ? palette.primary : palette.fillDarkest) : .clear
        Button { isOn.toggle() } label: {
            RoundedRectangle(cornerRadius: 4, style: .continuous)
                .fill(fill)
                .overlay {
                    RoundedRectangle(cornerRadius: 4, style: .continuous)
                        .stroke(isOn ? fill : (enabled ? palette.mutedText : palette.faintBorder), lineWidth: 1)
                }
                .overlay {
                    if isOn {
                        Image(systemName: "checkmark")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(.white)
                    }
                }
                .frame(width: 16, height: 16)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }
}

public struct Radio: View {
    @Environment(\.entePalette) private var palette
    private let selected: Bool
    private let action: (() -> Void)?

    public init(selected: Bool, action: (() -> Void)? = nil) {
        self.selected = selected
        self.action = action
    }

    public var body: some View {
        let active = action == nil ? palette.fillDarkest : palette.primary
        Button { action?() } label: {
            Circle()
                .stroke(selected ? active : palette.border, lineWidth: selected ? 2 : 1)
                .overlay {
                    if selected {
                        Circle().fill(active).padding(3)
                    }
                }
                .frame(width: 16, height: 16)
        }
        .buttonStyle(.plain)
        .disabled(action == nil)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

public struct EnteToggleStyle: ToggleStyle {
    @Environment(\.entePalette) private var palette

    public init() {}

    public func makeBody(configuration: Configuration) -> some View {
        HStack {
            configuration.label
            Spacer(minLength: EnteSpacing.md)
            Button { configuration.isOn.toggle() } label: {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(configuration.isOn ? palette.primary : palette.fill)
                    .overlay {
                        Circle()
                            .fill(configuration.isOn ? palette.reverseText : palette.primary)
                            .padding(3)
                            .frame(maxWidth: .infinity, alignment: configuration.isOn ? .trailing : .leading)
                    }
                    .frame(width: 51, height: 31)
                    .animation(.easeInOut(duration: EnteMotion.quick), value: configuration.isOn)
            }
            .buttonStyle(.plain)
        }
    }
}

public struct LabeledControl<Control: View>: View {
    @Environment(\.entePalette) private var palette
    private let label: String
    private let subtitle: String?
    private let control: Control

    public init(
        _ label: String,
        subtitle: String? = nil,
        @ViewBuilder control: () -> Control
    ) {
        self.label = label
        self.subtitle = subtitle
        self.control = control()
    }

    public var body: some View {
        HStack(spacing: EnteSpacing.md) {
            control
            VStack(alignment: .leading, spacing: 0) {
                Text(label)
                    .font(EnteTypography.body)
                    .foregroundStyle(palette.text)
                if let subtitle {
                    Text(subtitle)
                        .font(EnteTypography.mini)
                        .foregroundStyle(palette.mutedText)
                }
            }
        }
    }
}
