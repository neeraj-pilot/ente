SWIFT_SOURCES := $(shell git ls-files --cached --others --exclude-standard -- '*.swift' ':(exclude)apps/ensu/**' ':(exclude)**/Generated/**')
SWIFTLINT := swift package --allow-writing-to-package-directory swiftlint

.PHONY: lint format check-toolchain

lint: check-toolchain
	xcrun swift-format lint --strict $(SWIFT_SOURCES)
	$(SWIFTLINT) lint --strict $(SWIFT_SOURCES)

format: check-toolchain
	$(SWIFTLINT) lint --fix $(SWIFT_SOURCES)
	xcrun swift-format format --in-place $(SWIFT_SOURCES)

check-toolchain:
	@test "$$(xcrun swift-format --version)" = 6.2.3 || { echo 'Use Xcode 26.2 (swift-format 6.2.3).'; exit 1; }
