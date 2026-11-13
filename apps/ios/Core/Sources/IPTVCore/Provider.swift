import Foundation

public protocol ProviderRepository: Sendable {
  func validate(_ credentials: Credentials) async throws
  func categories(_ credentials: Credentials, kind: ContentKind) async throws -> [Category]
  func catalog(_ credentials: Credentials, kind: ContentKind) async throws -> [CatalogItem]
  func details(_ credentials: Credentials, item: CatalogItem) async throws -> ContentDetails
  func guide(_ credentials: Credentials, id: String) async throws -> [Programme]
  func streamURL(_ credentials: Credentials, item: CatalogItem, transportStream: Bool) throws -> URL
}

// Decode heterogeneous provider fields (strings/numbers/nulls) without failing an entire catalog.
extension Dictionary where Key == String, Value == Any {
  fileprivate func text(_ key: String, _ fallback: String = "") -> String {
    if let value = self[key] as? String { return value == "null" ? fallback : value }
    if let value = self[key] as? NSNumber { return value.stringValue }
    return fallback
  }
  fileprivate func object(_ key: String) -> [String: Any] { self[key] as? [String: Any] ?? [:] }
  fileprivate func array(_ key: String) -> [[String: Any]] {
    (self[key] as? [Any] ?? []).compactMap { $0 as? [String: Any] }
  }
  fileprivate func firstImage(_ key: String) -> String { (self[key] as? [String])?.first ?? "" }
}

public actor XtreamRepository: ProviderRepository {
  private let session: URLSession
  public init(session: URLSession? = nil) {
    if let session {
      self.session = session
    } else {
      let config = URLSessionConfiguration.ephemeral
      config.timeoutIntervalForRequest = 30
      config.timeoutIntervalForResource = 60
      config.urlCache = nil
      config.httpCookieStorage = nil
      self.session = URLSession(configuration: config)
    }
  }
  private func request(_ c: Credentials, action: String? = nil, parameters: [String: String] = [:])
    async throws -> Any
  {
    guard
      var components = URLComponents(
        url: c.baseURL.appendingPathComponent("player_api.php"), resolvingAgainstBaseURL: false)
    else { throw IPTVError.message("Enter a valid provider URL.") }
    components.queryItems = [
      URLQueryItem(name: "username", value: c.username),
      URLQueryItem(name: "password", value: c.password),
    ]
    if let action { components.queryItems?.append(URLQueryItem(name: "action", value: action)) }
    components.queryItems?.append(
      contentsOf: parameters.sorted { $0.key < $1.key }.map {
        URLQueryItem(name: $0.key, value: $0.value)
      })
    // PHP form decoding treats literal + as a space; preserve credential plus signs.
    components.percentEncodedQuery = components.percentEncodedQuery?.replacingOccurrences(
      of: "+", with: "%2B")
    guard let url = components.url else { throw IPTVError.message("Enter a valid provider URL.") }
    do {
      let (data, response) = try await session.data(from: url)
      try Task.checkCancellation()
      guard let response = response as? HTTPURLResponse else {
        throw IPTVError.message("The provider returned an unreadable response.")
      }
      switch response.statusCode {
      case 200..<300: break
      case 401, 403: throw IPTVError.message("Access denied. Check your login and subscription.")
      case 404:
        throw IPTVError.message("Provider API not found. Check the host, port, and base path.")
      default:
        throw IPTVError.message("Provider returned HTTP \(response.statusCode). Try again later.")
      }
      guard !data.isEmpty,
        let json = try? JSONSerialization.jsonObject(with: data, options: .fragmentsAllowed)
      else {
        throw IPTVError.message(
          "The provider returned an unreadable response. Check the base URL and try again.")
      }
      return json
    } catch is CancellationError { throw CancellationError() } catch let error as IPTVError {
      throw error
    } catch let error as URLError where error.code == .cancelled { throw CancellationError() } catch
    {
      throw IPTVError.message(
        "Cannot reach the provider. Check the address, connection, and HTTP/HTTPS setting.")
    }
  }
  public func validate(_ credentials: Credentials) async throws {
    guard let root = try await request(credentials) as? [String: Any] else {
      throw IPTVError.message("This address did not return a valid Xtream account.")
    }
    let user = root.object("user_info")
    guard user.text("auth") == "1" else {
      throw IPTVError.message("Login failed. Check your username and password.")
    }
    guard user.text("status").lowercased() == "active" else {
      throw IPTVError.message(
        "Subscription is \(user.text("status", "unavailable")). Contact your provider.")
    }
    if let expiry = Double(user.text("exp_date")), expiry > 0,
      expiry <= Date().timeIntervalSince1970
    {
      throw IPTVError.message("Your subscription has expired. Contact your provider.")
    }
  }
  private func list(_ response: Any) throws -> [[String: Any]] {
    guard let result = response as? [Any] else {
      throw IPTVError.message("The provider could not load this catalog. Retry or sign in again.")
    }
    return result.compactMap { $0 as? [String: Any] }
  }
  public func categories(_ credentials: Credentials, kind: ContentKind) async throws -> [Category] {
    var seen = Set<String>()
    return try list(await request(credentials, action: "get_\(kind.api)_categories")).compactMap {
      object in
      let id = object.text("category_id")
      return !id.isEmpty && seen.insert(id).inserted
        ? Category(id: id, name: object.text("category_name", "Other")) : nil
    }
  }
  public func catalog(_ credentials: Credentials, kind: ContentKind) async throws -> [CatalogItem] {
    let action = kind == .series ? "get_series" : "get_\(kind.api)_streams"
    var seen = Set<String>()
    return try list(await request(credentials, action: action)).compactMap { object in
      let id = object.text(kind == .series ? "series_id" : "stream_id")
      guard !id.isEmpty, seen.insert(id).inserted else { return nil }
      var item = CatalogItem(id: id, name: object.text("name", "Untitled"), kind: kind)
      item.categoryID = object.text("category_id")
      item.image = object.text("stream_icon", object.text("cover"))
      item.container = object.text("container_extension", "mp4")
      item.added = Int64(object.text("added", object.text("last_modified"))) ?? 0
      item.rating = (Double(object.text("rating")) ?? 0) > 0 ? object.text("rating") : ""
      let year = object.text("year", String(object.text("releaseDate").prefix(4)))
      item.year = Int(year) == nil ? "" : year
      item.backdrop = object.firstImage("backdrop_path")
      item.genre = object.text("genre")
      item.language = object.text("language")
      item.releaseDate = object.text("releaseDate", object.text("releasedate"))
      item.overview = object.text("plot", object.text("description"))
      return item
    }
  }
  public func details(_ credentials: Credentials, item: CatalogItem) async throws -> ContentDetails
  {
    guard
      let root = try await request(
        credentials, action: item.kind == .series ? "get_series_info" : "get_vod_info",
        parameters: [item.kind == .series ? "series_id" : "vod_id": item.id]) as? [String: Any]
    else { throw IPTVError.message("Details are unavailable. Try again later.") }
    if root.object("user_info").text("auth") == "0" {
      throw IPTVError.message("Access denied. Check your login and subscription.")
    }
    let info = root.object("info")
    var details = ContentDetails()
    details.overview = info.text("plot", info.text("description"))
    details.cast = info.text("cast", info.text("actors"))
    details.director = info.text("director")
    details.rating = info.text("rating")
    details.backdrop = info.firstImage("backdrop_path")
    details.genre = info.text("genre")
    details.year = info.text(
      "year", String(info.text("releasedate", info.text("releaseDate")).prefix(4)))
    details.duration = info.text("duration")
    details.language = info.text("language")
    details.subtitles = info.text("subtitles")
    details.classification = info.text("mpaa", info.text("age"))
    details.poster = info.text("movie_image", info.text("cover"))
    details.trailer = MediaRules.trailer(info.text("youtube_trailer", info.text("trailer")))
    details.container = root.object("movie_data").text("container_extension", item.container)
    let video = info.object("video")
    details.technical = [
      video.text("codec_name"),
      (Int(video.text("width")) ?? 0) > 0 ? "\(video.text("width")) × \(video.text("height"))" : "",
    ].filter { !$0.isEmpty }.joined(separator: " · ")
    var seen = Set<String>()
    details.episodes = root.object("episodes").flatMap { season, value -> [Episode] in
      guard let values = value as? [Any] else { return [] }
      let entries = values.compactMap { $0 as? [String: Any] }
      return entries.compactMap { ep in
        let id = ep.text("id")
        guard !id.isEmpty, seen.insert(id).inserted else { return nil }
        let epInfo = ep.object("info")
        let seasonNumber = Int(ep.text("season", season)) ?? 0
        let number = Int(ep.text("episode_num")) ?? 0
        var child = CatalogItem(id: id, name: ep.text("title", "Episode \(number)"), kind: .series)
        child.categoryID = item.categoryID
        child.image = epInfo.text("movie_image", item.image)
        child.container = ep.text("container_extension", "mp4")
        child.genre = item.genre
        child.parentSeriesID = item.id
        child.parentSeriesName = item.name
        child.season = seasonNumber
        child.episode = number
        var episode = Episode(item: child, season: seasonNumber, number: number)
        episode.overview = epInfo.text("plot", epInfo.text("description"))
        episode.duration =
          Double(epInfo.text("duration_secs")).flatMap { $0 > 0 ? $0 : nil }
          ?? MediaRules.duration(epInfo.text("duration"))
        return episode
      }
    }.sorted { ($0.season, $0.number) < ($1.season, $1.number) }
    return details
  }
  public func guide(_ credentials: Credentials, id: String) async throws -> [Programme] {
    guard
      let root = try await request(
        credentials, action: "get_short_epg", parameters: ["stream_id": id, "limit": "8"])
        as? [String: Any]
    else { return [] }
    var seen = Set<String>()
    return root.array("epg_listings").compactMap { value in
      guard let start = Double(value.text("start_timestamp")),
        let end = Double(value.text("stop_timestamp", value.text("end_timestamp"))), start.isFinite,
        end.isFinite, start >= 0, end > start
      else { return nil }
      let programme = Programme(
        title: Self.decodeTitle(value.text("title")), start: start, end: end,
        overview: Self.decodeTitle(value.text("description")))
      return seen.insert(programme.id).inserted ? programme : nil
    }.sorted { $0.start < $1.start }
  }
  public nonisolated func streamURL(
    _ credentials: Credentials, item: CatalogItem, transportStream: Bool = false
  ) throws -> URL {
    let ext =
      item.kind == .live
      ? (transportStream ? "ts" : "m3u8")
      : (item.container.range(of: "^[A-Za-z0-9]{1,8}$", options: .regularExpression) != nil
        ? item.container : "mp4")
    // URLComponents explicitly encodes each segment, including slashes in credentials.
    var url = URLComponents(url: credentials.baseURL, resolvingAgainstBaseURL: false)!
    let safe = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-._~"))
    if !url.percentEncodedPath.hasSuffix("/") { url.percentEncodedPath += "/" }
    url.percentEncodedPath += [
      item.kind.route, credentials.username, credentials.password, "\(item.id).\(ext)",
    ].map { $0.addingPercentEncoding(withAllowedCharacters: safe)! }.joined(separator: "/")
    guard let result = url.url else {
      throw IPTVError.message("Could not prepare playback. Try again.")
    }
    return result
  }
  private static func decodeTitle(_ value: String) -> String {
    guard let data = Data(base64Encoded: value), let decoded = String(data: data, encoding: .utf8),
      !decoded.unicodeScalars.contains(where: {
        CharacterSet.controlCharacters.contains($0)
          && !CharacterSet.whitespacesAndNewlines.contains($0)
      })
    else { return value }
    return decoded
  }
}
