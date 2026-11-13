import SwiftUI

actor ArtworkCache {
  static let shared = ArtworkCache()
  private let session: URLSession
  private let cache: URLCache
  private var generation = 0
  init() {
    cache = URLCache(
      memoryCapacity: 24 * 1024 * 1024, diskCapacity: 120 * 1024 * 1024, diskPath: "IPTVArtwork")
    let config = URLSessionConfiguration.default
    config.urlCache = cache
    config.httpCookieStorage = nil
    config.timeoutIntervalForRequest = 20
    session = URLSession(configuration: config)
  }
  func load(_ url: URL) async throws -> UIImage {
    guard ["http", "https"].contains(url.scheme?.lowercased() ?? "") else {
      throw URLError(.unsupportedURL)
    }
    let token = generation
    let (data, response) = try await session.data(from: url)
    try Task.checkCancellation()
    guard token == generation,
      (response as? HTTPURLResponse).map({ (200..<300).contains($0.statusCode) }) == true,
      data.count <= 20 * 1024 * 1024, let source = CGImageSourceCreateWithData(data as CFData, nil),
      let image = CGImageSourceCreateThumbnailAtIndex(
        source, 0,
        [
          kCGImageSourceCreateThumbnailFromImageAlways: true,
          kCGImageSourceThumbnailMaxPixelSize: 1400,
          kCGImageSourceCreateThumbnailWithTransform: true,
        ] as CFDictionary)
    else { throw URLError(.cannotDecodeContentData) }
    return UIImage(cgImage: image)
  }
  func clear() {
    generation += 1
    session.getAllTasks { tasks in for task in tasks { task.cancel() } }
    cache.removeAllCachedResponses()
  }
}
struct CachedArtwork<Placeholder: View>: View {
  let url: URL?
  var fit = false
  @ViewBuilder var placeholder: () -> Placeholder
  @State private var image: UIImage?
  var body: some View {
    GeometryReader { geometry in
      Group {
        if let image {
          Image(uiImage: image).resizable().aspectRatio(contentMode: fit ? .fit : .fill)
        } else {
          placeholder()
        }
      }.frame(width: geometry.size.width, height: geometry.size.height).clipped()
    }.task(id: url) {
      image = nil
      if let url { image = try? await ArtworkCache.shared.load(url) }
    }
  }
}
