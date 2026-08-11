import Foundation
import XCTest
@testable import FileExportCore

final class ExportModelsTests: XCTestCase {
    func testAcceptsGeneratedData() throws {
        let request = try ExportRequest(
            fileName: "ente_recovery_key.txt",
            mimeType: "text/plain",
            source: .data(Data([1, 2, 3]))
        )

        XCTAssertEqual(request.fileName, "ente_recovery_key.txt")
    }

    func testRejectsPathComponentsInFileName() {
        XCTAssertThrowsError(
            try ExportRequest(
                fileName: "../key.txt",
                mimeType: "text/plain",
                source: .data(Data())
            )
        )
    }

    func testRejectsRelativeSourcePath() {
        XCTAssertThrowsError(
            try ExportRequest(
                fileName: "logs.zip",
                mimeType: "application/zip",
                source: .file(URL(string: "logs.zip")!)
            )
        )
    }
}
