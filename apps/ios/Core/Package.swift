// swift-tools-version: 5.9
import PackageDescription
let package = Package(
    name: "IPTVCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "IPTVCore", type: .static, targets: ["IPTVCore"])],
    targets: [
        .systemLibrary(name: "CSQLite"),
        .target(name: "IPTVCore", dependencies: ["CSQLite"]),
        .testTarget(name: "IPTVCoreTests", dependencies: ["IPTVCore"])
    ]
)
